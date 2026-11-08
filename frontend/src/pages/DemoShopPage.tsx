import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { api, type ProductRecord, type DemoFaultSwitchesRecord } from '../lib/api';
import { toast } from '../lib/toast';
import { CardSkeleton } from '../components/DataStates';

const DEFAULT_PRODUCTS: ProductRecord[] = [
  { id: 'mug', name: 'Ceramic mug', priceMinor: 2400, currency: 'USD', price: 24.0, description: 'Handcrafted ceramic mug (12 oz)' },
  { id: 'tote', name: 'Canvas tote', priceMinor: 3850, currency: 'USD', price: 38.5, description: 'Heavyweight organic cotton canvas tote' },
  { id: 'notebook', name: 'Notebook set', priceMinor: 2200, currency: 'USD', price: 22.0, description: 'Pack of 3 dot-grid softcover pocket notebooks' },
];

function ToggleSwitch({
  label,
  description,
  checked,
  onChange,
  disabled = false,
}: {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (val: boolean) => void;
  disabled?: boolean;
}) {
  const switchId = `switch-${label.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <div className="flex items-center justify-between py-1.5 gap-3">
      <div className="space-y-0.5">
        <label htmlFor={switchId} className="text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-200 cursor-pointer block">
          {label}
        </label>
        {description && (
          <p className="text-[11px] text-slate-500 dark:text-slate-400">{description}</p>
        )}
      </div>
      <button
        id={switchId}
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2 dark:focus:ring-offset-slate-900 ${
          checked ? 'bg-[#0066cc]' : 'bg-slate-200 dark:bg-slate-700'
        } disabled:opacity-50`}
      >
        <span
          className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-md ring-0 transition duration-200 ease-in-out ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
}

export default function DemoShopPage() {
  const queryClient = useQueryClient();

  const [cart, setCart] = useState<{ [id: string]: number }>({
    mug: 1,
    tote: 1,
  });

  // 1. Fetch Products
  const { data: productsData, isLoading: productsLoading } = useQuery({
    queryKey: ['shop-products'],
    queryFn: api.shop.getProducts,
  });

  const products = (productsData && productsData.length > 0) ? productsData : DEFAULT_PRODUCTS;

  // 2. Fetch Recent Orders from API
  const { data: ordersData } = useQuery({
    queryKey: ['shop-orders'],
    queryFn: api.shop.getOrders,
  });

  const recentOrders = useMemo(() => {
    if (ordersData && ordersData.length > 0) {
      return ordersData.slice(0, 6).map((o) => ({
        id: `#${o.id.replace('ORD-', '')}`,
        status: o.status === 'PAID' ? 'Paid' : o.status === 'REFUNDED' ? 'Refunded' : 'Pending',
        amountMinor: o.totalMinor || 0,
      }));
    }
    return [
      { id: '#10482', status: 'Pending', amountMinor: 8450 },
      { id: '#10481', status: 'Paid', amountMinor: 2400 },
      { id: '#10477', status: 'Paid', amountMinor: 3200 },
      { id: '#10470', status: 'Refunded', amountMinor: 3850 },
    ];
  }, [ordersData]);

  // 3. Fetch Fault Switches
  const { data: faultSwitchesData } = useQuery({
    queryKey: ['demo-faults'],
    queryFn: api.faults.get,
  });

  const defaultFaults: DemoFaultSwitchesRecord = {
    failWebhookHandler: true,
    dropWebhook: false,
    alterOrderTotal: false,
    duplicateDelivery: false,
    demoMode: true,
  };

  const faultSwitches: DemoFaultSwitchesRecord = faultSwitchesData ?? defaultFaults;

  // Mutation: Update fault switch
  const switchMutation = useMutation({
    mutationFn: (updated: DemoFaultSwitchesRecord) => api.faults.update(updated),
    onSuccess: (data) => {
      queryClient.setQueryData(['demo-faults'], data);
      toast.info('Fault switch updated in database.');
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: ['demo-faults'] });
      toast.error('Failed to update fault switch.');
    },
  });

  // Mutation: Reset fault switches
  const resetFaultsMutation = useMutation({
    mutationFn: api.faults.reset,
    onSuccess: (data) => {
      queryClient.setQueryData(['demo-faults'], data);
      toast.success('All demo fault switches reset to false.');
    },
    onError: () => {
      toast.error('Failed to reset fault switches.');
    },
  });

  // Mutation: PayPal Checkout Flow
  const checkoutMutation = useMutation({
    mutationFn: async () => {
      const items = Object.entries(cart)
        .filter(([, qty]) => qty > 0)
        .map(([pid, qty]) => {
          const p = products.find((x) => x.id === pid);
          return {
            productId: pid,
            quantity: qty,
            priceMinor: p ? p.priceMinor : 2400,
          };
        });

      if (items.length === 0) {
        throw new Error('Your cart is empty. Add at least one product.');
      }

      // 1. Create shop order
      const order = await api.shop.createOrder({
        customerLabel: 'Demo Customer (customer@demo.local)',
        items,
        currency: 'USD',
      });

      const orderId = order.id || 'ORD-10482';
      const returnUrl = `${window.location.origin}/shop/return?orderId=${orderId}`;
      const cancelUrl = `${window.location.origin}/shop/cancel?orderId=${orderId}`;

      // 2. Initiate PayPal checkout
      const checkout = await api.shop.checkout(orderId, returnUrl, cancelUrl);

      // Redirect to PayPal Sandbox approval or local return page
      if (checkout.approveUrl && checkout.approveUrl.startsWith('http')) {
        window.location.href = checkout.approveUrl;
      } else {
        window.location.href = `/shop/return?orderId=${orderId}&token=${checkout.paypalOrderId || 'MOCK-TOKEN'}`;
      }
      return { order, checkout };
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Checkout failed.');
    },
  });

  const addToCart = (id: string) => {
    setCart((prev) => ({ ...prev, [id]: (prev[id] || 0) + 1 }));
  };

  const removeFromCart = (id: string) => {
    setCart((prev) => {
      const current = prev[id] || 0;
      if (current <= 1) {
        const copy = { ...prev };
        delete copy[id];
        return copy;
      }
      return { ...prev, [id]: current - 1 };
    });
  };

  const totalAmountMinor = Object.entries(cart).reduce((sum, [pid, qty]) => {
    const p = products.find((x) => x.id === pid);
    return sum + (p ? (p.priceMinor || 2400) * qty : 0);
  }, 0);

  const totalAmountDollars = (totalAmountMinor / 100).toFixed(2);

  const toggleSwitch = (key: keyof Omit<DemoFaultSwitchesRecord, 'demoMode'>) => {
    const next = {
      ...faultSwitches,
      [key]: !faultSwitches[key],
    };
    queryClient.setQueryData(['demo-faults'], next);
    switchMutation.mutate(next);
  };

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-7">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
              Demo shop
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              A tiny merchant for real Sandbox payments. Fault switches break it on purpose.
            </p>
          </div>
          <div>
            <span className="px-3.5 py-1 rounded-full border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 text-xs font-normal">
              Sandbox
            </span>
          </div>
        </div>

        {/* 2-Column Content */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left Column: Products & Checkout */}
          <div className="lg:col-span-8 space-y-6">
            
            {/* Products grid */}
            {productsLoading ? (
              <CardSkeleton count={2} />
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                {products.map((prod) => (
                  <div
                    key={prod.id}
                    className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-5 flex flex-col justify-between shadow-2xs space-y-3"
                  >
                    <div>
                      <h2 className="text-sm font-semibold text-slate-900 dark:text-white">{prod.name}</h2>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 line-clamp-2">{prod.description}</p>
                    </div>
                    <div className="flex items-center justify-between pt-2 border-t border-slate-100 dark:border-slate-800/60">
                      <span className="text-sm font-bold text-slate-900 dark:text-white font-mono">
                        ${(prod.priceMinor / 100).toFixed(2)}
                      </span>
                      <button
                        type="button"
                        onClick={() => addToCart(prod.id)}
                        className="px-3 py-1.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-medium cursor-pointer shadow-xs transition-all flex items-center space-x-1"
                      >
                        <GoogleIcon name="add_shopping_cart" size={13} />
                        <span>Add</span>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Checkout summary card */}
            <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-6 shadow-2xs">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-base font-bold text-slate-900 dark:text-white">Checkout summary</h2>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Order lines mapped to PayPal unit items.
                  </p>
                </div>
                <div className="text-right">
                  <span className="text-xs text-slate-500 dark:text-slate-400 block">Total</span>
                  <span className="text-xl font-bold text-slate-900 dark:text-white font-mono">
                    ${totalAmountDollars}
                  </span>
                </div>
              </div>

              {/* Items in cart breakdown with quantity stepper */}
              <div className="divide-y divide-slate-100 dark:divide-slate-800/60 text-xs">
                {Object.entries(cart).length === 0 || totalAmountMinor === 0 ? (
                  <div className="py-6 text-center text-xs text-slate-400">
                    Your cart is empty. Click Add above to select demo items.
                  </div>
                ) : (
                  Object.entries(cart).map(([pid, qty]) => {
                    const p = products.find((x) => x.id === pid);
                    if (!p || qty === 0) return null;
                    return (
                      <div key={pid} className="py-3 flex items-center justify-between text-slate-600 dark:text-slate-300">
                        <div>
                          <span className="font-semibold text-slate-800 dark:text-slate-200">{p.name}</span>
                          <span className="text-slate-400 text-xs ml-2 font-mono">(${(p.priceMinor / 100).toFixed(2)})</span>
                        </div>
                        <div className="flex items-center space-x-3">
                          <div className="flex items-center border border-slate-200 dark:border-slate-700 rounded-lg overflow-hidden bg-slate-50 dark:bg-[#131b26]">
                            <button
                              type="button"
                              onClick={() => removeFromCart(pid)}
                              className="px-2.5 py-1 text-xs text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 cursor-pointer"
                              title="Decrease quantity"
                            >
                              -
                            </button>
                            <span className="px-2.5 text-xs font-mono font-bold text-slate-900 dark:text-white">{qty}</span>
                            <button
                              type="button"
                              onClick={() => addToCart(pid)}
                              className="px-2.5 py-1 text-xs text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 cursor-pointer"
                              title="Increase quantity"
                            >
                              +
                            </button>
                          </div>
                          <span className="font-mono font-bold text-slate-900 dark:text-white min-w-[65px] text-right">
                            ${((p.priceMinor * qty) / 100).toFixed(2)}
                          </span>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              <button
                type="button"
                onClick={() => checkoutMutation.mutate()}
                disabled={checkoutMutation.isPending || totalAmountMinor === 0}
                className="w-full py-3.5 px-4 rounded-xl bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-semibold text-sm shadow-sm cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center space-x-2"
              >
                <GoogleIcon name="lock" size={16} />
                <span>{checkoutMutation.isPending ? 'Redirecting to PayPal Sandbox...' : 'Pay with PayPal Sandbox'}</span>
              </button>
            </div>
          </div>

          {/* Right Column: Fault Switches & Recent Orders */}
          <div className="lg:col-span-4 space-y-6">
            
            {/* Fault Switches Card */}
            <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-5 shadow-2xs">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-base font-bold text-slate-900 dark:text-white">Fault switches</h2>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Causes real discrepancies on the next event.
                  </p>
                </div>
                <span className="px-2.5 py-0.5 rounded-full border border-amber-400 dark:border-amber-700 text-amber-700 dark:text-amber-400 text-[11px] font-normal">
                  Demo only
                </span>
              </div>

              {/* Accessible Role Switch Toggles */}
              <div className="space-y-2.5 divide-y divide-slate-100 dark:divide-slate-800/60">
                <ToggleSwitch
                  label="Fail webhook handler"
                  description="Webhook returns 500; order left Pending"
                  checked={faultSwitches.failWebhookHandler}
                  onChange={() => toggleSwitch('failWebhookHandler')}
                />
                <ToggleSwitch
                  label="Drop webhook"
                  description="Simulates dropped packet in transit"
                  checked={faultSwitches.dropWebhook}
                  onChange={() => toggleSwitch('dropWebhook')}
                />
                <ToggleSwitch
                  label="Alter order total"
                  description="Simulates $10 line-item roundoff discrepancy"
                  checked={faultSwitches.alterOrderTotal}
                  onChange={() => toggleSwitch('alterOrderTotal')}
                />
                <ToggleSwitch
                  label="Duplicate delivery"
                  description="Fires 2 identical webhooks simultaneously"
                  checked={faultSwitches.duplicateDelivery}
                  onChange={() => toggleSwitch('duplicateDelivery')}
                />
              </div>

              <div className="pt-2">
                <button
                  type="button"
                  onClick={() => resetFaultsMutation.mutate()}
                  disabled={resetFaultsMutation.isPending}
                  className="w-full py-2 px-3 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors"
                >
                  {resetFaultsMutation.isPending ? 'Resetting...' : 'Reset all to false'}
                </button>
              </div>
            </div>

            {/* Recent Orders Card */}
            <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
              <h2 className="text-base font-bold text-slate-900 dark:text-white">Recent orders</h2>

              <div className="space-y-3">
                {recentOrders.map((ord) => (
                  <div key={ord.id} className="flex items-center justify-between text-xs sm:text-sm">
                    <span className="font-mono text-slate-800 dark:text-slate-200">{ord.id}</span>
                    <span
                      className={`px-3 py-0.5 rounded-full border text-xs ${
                        ord.status === 'Paid'
                          ? 'border-emerald-400 dark:border-emerald-600 text-emerald-600 dark:text-emerald-400'
                          : ord.status === 'Refunded'
                          ? 'border-purple-400 dark:border-purple-600 text-purple-600 dark:text-purple-400'
                          : 'border-amber-400 dark:border-amber-700 text-amber-600 dark:text-amber-400'
                      }`}
                    >
                      {ord.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>

          </div>
        </div>
      </div>
    </AppLayout>
  );
}
