import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { AppLayout } from '../components/AppLayout';
import { PayResolveSpinner } from '../components/PayResolveSpinner';
import { GoogleIcon } from '../components/GoogleIcon';
import { api, type CaptureOrderResponseDto } from '../lib/api';

export default function ShopReturnPage() {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get('orderId') || 'ORD-10482';
  const token = searchParams.get('token'); // PayPal Order ID

  const [isLoading, setIsLoading] = useState(true);
  const [captureResult, setCaptureResult] = useState<CaptureOrderResponseDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function executeCapture() {
      setIsLoading(true);
      setError(null);
      try {
        const result = await api.shop.capture(orderId);
        if (isMounted) {
          setCaptureResult(result);
          setIsLoading(false);
        }
      } catch (err: unknown) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Server-side capture failed');
          setIsLoading(false);
        }
      }
    }

    executeCapture();

    return () => {
      isMounted = false;
    };
  }, [orderId]);

  return (
    <AppLayout>
      <div className="max-w-2xl mx-auto py-12 px-4 sm:px-6">
        <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-lg p-8 sm:p-10 text-center">
          {isLoading ? (
            <div className="flex flex-col items-center justify-center space-y-6 py-8">
              <PayResolveSpinner size={64} />
              <div className="space-y-2">
                <h1 className="text-xl font-bold text-slate-900 dark:text-white">
                  Confirming PayPal payment
                </h1>
                <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 max-w-sm mx-auto">
                  Executing server-side capture for order <span className="font-mono font-medium">{orderId}</span> and awaiting verified webhook event.
                </p>
                {token && (
                  <p className="text-[11px] font-mono text-slate-400">PayPal Ref: {token}</p>
                )}
              </div>
            </div>
          ) : error ? (
            <div className="space-y-6 py-4">
              <div className="w-14 h-14 rounded-2xl bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 mx-auto flex items-center justify-center">
                <GoogleIcon name="error" size={32} />
              </div>
              <div className="space-y-2">
                <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Payment failed</h1>
                <p className="text-sm text-slate-600 dark:text-slate-400 max-w-md mx-auto">
                  {error}
                </p>
              </div>
              <div className="flex items-center justify-center gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => window.location.reload()}
                  className="px-5 py-2.5 rounded-xl bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-semibold shadow-sm cursor-pointer transition-all"
                >
                  Retry capture
                </button>
                <Link
                  to="/shop"
                  className="px-5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors"
                >
                  Return to shop
                </Link>
              </div>
            </div>
          ) : (
            <div className="space-y-6 py-4">
              <div className="w-14 h-14 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 mx-auto flex items-center justify-center">
                <GoogleIcon name="check_circle" size={32} />
              </div>
              <div className="space-y-2">
                <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
                  Payment captured successfully
                </h1>
                <p className="text-sm text-slate-600 dark:text-slate-400 max-w-md mx-auto">
                  PayPal capture completed for order <span className="font-mono font-medium text-slate-800 dark:text-slate-200">{orderId}</span>. Server-side payment record created and awaiting webhook confirmation.
                </p>
              </div>

              {captureResult && (
                <div className="p-4 rounded-xl bg-slate-50 dark:bg-[#131b26] border border-slate-200/60 dark:border-slate-800/60 max-w-md mx-auto text-left font-mono text-xs space-y-1.5">
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-sans">Capture ID:</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">{captureResult.captureId}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-sans">Status:</span>
                    <span className="text-emerald-600 dark:text-emerald-400 font-semibold">{captureResult.status}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-sans">Amount:</span>
                    <span className="text-slate-800 dark:text-slate-200 font-bold">${((captureResult.amountCapturedMinor || 0) / 100).toFixed(2)}</span>
                  </div>
                </div>
              )}

              <div className="flex flex-wrap items-center justify-center gap-3 pt-4">
                <Link
                  to="/queue"
                  className="px-5 py-2.5 rounded-xl bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-semibold shadow-sm cursor-pointer transition-all"
                >
                  View Exception Queue
                </Link>
                <Link
                  to="/events"
                  className="px-5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors"
                >
                  Check Event Inbox
                </Link>
                <Link
                  to="/shop"
                  className="px-4 py-2.5 text-xs text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200"
                >
                  Back to shop
                </Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </AppLayout>
  );
}
