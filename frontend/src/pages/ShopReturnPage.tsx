import { useEffect, useRef, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AppLayout } from '../components/AppLayout';
import { PayResolveSpinner } from '../components/PayResolveSpinner';
import { GoogleIcon } from '../components/GoogleIcon';
import { PaymentTraceStepper } from '../components/PaymentTraceStepper';
import { LastUpdatedLabel } from '../components/LastUpdatedLabel';
import { api, type CaptureOrderResponseDto } from '../lib/api';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_DURATION_MS = 60_000;

export default function ShopReturnPage() {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get('orderId') || 'ORD-10482';
  const token = searchParams.get('token'); // PayPal Order ID

  return <ShopReturnContent key={orderId} orderId={orderId} token={token} />;
}

function ShopReturnContent({
  orderId,
  token,
}: {
  orderId: string;
  token: string | null;
}) {
  const [captureState, setCaptureState] = useState<'capturing' | 'captured' | 'error'>('capturing');
  const [captureResult, setCaptureResult] = useState<CaptureOrderResponseDto | null>(null);
  const [captureError, setCaptureError] = useState<string | null>(null);
  const [timedOut, setTimedOut] = useState(false);

  const pollStartRef = useRef<number>(0);
  const inFlightCaptureRef = useRef<{
    orderId: string;
    promise: Promise<CaptureOrderResponseDto>;
  } | null>(null);

  useEffect(() => {
    let isMounted = true;

    if (!inFlightCaptureRef.current || inFlightCaptureRef.current.orderId !== orderId) {
      inFlightCaptureRef.current = {
        orderId,
        promise: api.shop.capture(orderId),
      };
    }

    inFlightCaptureRef.current.promise
      .then((result) => {
        if (!isMounted) return;
        setCaptureResult(result);
        pollStartRef.current = Date.now();
        setCaptureState('captured');
      })
      .catch((err: unknown) => {
        if (!isMounted) return;
        setCaptureError(err instanceof Error ? err.message : 'Server-side capture failed');
        setCaptureState('error');
      });

    return () => {
      isMounted = false;
    };
  }, [orderId]);

  const orderQuery = useQuery({
    queryKey: ['shop-order-live', orderId],
    queryFn: () => api.shop.getOrderById(orderId),
    enabled: captureState === 'captured',
    refetchIntervalInBackground: false,
    refetchInterval: (query) => {
      const currentStatus = query.state.data?.status;
      if (currentStatus === 'PAID' || currentStatus === 'REFUNDED') {
        return false;
      }
      if (timedOut) {
        return false;
      }
      if (pollStartRef.current > 0 && Date.now() - pollStartRef.current >= MAX_POLL_DURATION_MS) {
        return false;
      }
      return POLL_INTERVAL_MS;
    },
  });

  const liveStatus = orderQuery.data?.status || captureResult?.status || 'AWAITING_CONFIRMATION';
  const isPaid = liveStatus === 'PAID' || liveStatus === 'REFUNDED';
  const isPolling = captureState === 'captured' && !isPaid && !timedOut;

  // Enforce strict 60s max polling cutoff and clean up on unmount
  useEffect(() => {
    if (captureState !== 'captured' || isPaid || timedOut) {
      return;
    }

    const elapsed = pollStartRef.current > 0 ? Date.now() - pollStartRef.current : 0;
    const remaining = Math.max(0, MAX_POLL_DURATION_MS - elapsed);

    const timerId = window.setTimeout(() => {
      setTimedOut(true);
    }, remaining);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [captureState, isPaid, timedOut]);

  const traceQuery = useQuery({
    queryKey: ['shop-order-trace', orderId],
    queryFn: () => api.shop.getTrace(orderId),
    enabled: captureState === 'captured',
    refetchIntervalInBackground: false,
    refetchInterval: isPolling ? POLL_INTERVAL_MS : false,
  });

  const handleRetryCapture = () => {
    inFlightCaptureRef.current = null;
    setTimedOut(false);
    setCaptureError(null);
    setCaptureState('capturing');
    const promise = api.shop.capture(orderId);
    inFlightCaptureRef.current = { orderId, promise };
    promise
      .then((result) => {
        setCaptureResult(result);
        pollStartRef.current = Date.now();
        setCaptureState('captured');
      })
      .catch((err: unknown) => {
        setCaptureError(err instanceof Error ? err.message : 'Server-side capture failed');
        setCaptureState('error');
      });
  };

  return (
    <AppLayout>
      <div className="max-w-2xl mx-auto py-8 sm:py-10 px-4 sm:px-6 space-y-6">
        <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-lg p-6 sm:p-9 text-center space-y-6">
          {captureState === 'capturing' ? (
            <div className="flex flex-col items-center justify-center space-y-6 py-6">
              <PayResolveSpinner size={64} />
              <div className="space-y-2">
                <h1 className="text-xl font-bold text-slate-900 dark:text-white">
                  Confirming PayPal payment
                </h1>
                <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 max-w-sm mx-auto">
                  Executing server-side capture for order <span className="font-mono font-medium">{orderId}</span>...
                </p>
                {token && (
                  <p className="text-[11px] font-mono text-slate-400">PayPal Ref: {token}</p>
                )}
              </div>
            </div>
          ) : captureState === 'error' ? (
            <div className="space-y-6 py-4">
              <div className="w-14 h-14 rounded-2xl bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 mx-auto flex items-center justify-center">
                <GoogleIcon name="error" size={32} />
              </div>
              <div className="space-y-2">
                <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Payment failed</h1>
                <p className="text-sm text-slate-600 dark:text-slate-400 max-w-md mx-auto">
                  {captureError}
                </p>
              </div>
              <div className="flex items-center justify-center gap-3 pt-2">
                <button
                  type="button"
                  onClick={handleRetryCapture}
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
            <div className="space-y-6">
              {/* Top Status Hero */}
              {isPolling ? (
                <div className="flex flex-col items-center space-y-4">
                  <PayResolveSpinner size={60} />
                  <div className="space-y-1.5">
                    <h1 className="text-xl sm:text-2xl font-bold text-slate-900 dark:text-white">
                      Payment captured, waiting for confirmation
                    </h1>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 max-w-md mx-auto">
                      Checking order <span className="font-mono font-semibold text-slate-700 dark:text-slate-200">{orderId}</span> every 2 seconds until PayPal&apos;s verified webhook confirms it.
                    </p>
                  </div>
                </div>
              ) : isPaid ? (
                <div className="space-y-3">
                  <div className="w-14 h-14 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 mx-auto flex items-center justify-center">
                    <GoogleIcon name="check_circle" size={32} />
                  </div>
                  <div className="space-y-1.5">
                    <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
                      Payment confirmed
                    </h1>
                    <p className="text-sm text-slate-600 dark:text-slate-400 max-w-md mx-auto">
                      PayPal&apos;s verified webhook arrived and order <span className="font-mono font-medium text-slate-800 dark:text-slate-200">{orderId}</span> is marked <strong className="text-emerald-600 dark:text-emerald-400">PAID</strong>.
                    </p>
                  </div>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="w-14 h-14 rounded-2xl bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400 mx-auto flex items-center justify-center">
                    <GoogleIcon name="schedule" size={32} />
                  </div>
                  <div className="space-y-2">
                    <h1 className="text-xl sm:text-2xl font-bold text-slate-900 dark:text-white">
                      Confirmation delayed
                    </h1>
                    <div
                      role="alert"
                      className="p-3.5 rounded-xl bg-amber-50/80 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800/70 text-xs sm:text-sm text-amber-900 dark:text-amber-200 max-w-lg mx-auto text-left leading-relaxed"
                    >
                      Captured, but PayPal&apos;s confirmation has not arrived yet. This creates a Paid, not marked case if it never comes.
                    </div>
                  </div>
                </div>
              )}

              {/* Live 2-Step Confirmation Progress Bar */}
              <div className="max-w-md mx-auto rounded-xl bg-slate-50/80 dark:bg-[#131b26] border border-slate-200/80 dark:border-slate-800 p-4 text-left space-y-3">
                <div className="flex items-center justify-between text-xs sm:text-sm">
                  <div className="flex items-center gap-2.5">
                    <GoogleIcon name="check_circle" size={18} className="text-emerald-600 dark:text-emerald-400 shrink-0" />
                    <span className="font-semibold text-slate-900 dark:text-slate-100">Payment captured</span>
                  </div>
                  <span className="text-[11px] font-mono text-emerald-600 dark:text-emerald-400">Done</span>
                </div>

                <div className="flex items-center justify-between text-xs sm:text-sm">
                  <div className="flex items-center gap-2.5">
                    {isPaid ? (
                      <GoogleIcon name="check_circle" size={18} className="text-emerald-600 dark:text-emerald-400 shrink-0" />
                    ) : timedOut ? (
                      <GoogleIcon name="warning" size={18} className="text-amber-600 dark:text-amber-400 shrink-0" />
                    ) : (
                      <span className="w-4 h-4 m-[1px] rounded-full border-2 border-blue-600 border-t-transparent animate-spin shrink-0" />
                    )}
                    <span
                      className={`font-semibold ${
                        isPaid
                          ? 'text-slate-900 dark:text-slate-100'
                          : timedOut
                          ? 'text-amber-800 dark:text-amber-300'
                          : 'text-blue-700 dark:text-blue-300'
                      }`}
                    >
                      Waiting for PayPal&apos;s confirmation (webhook)
                    </span>
                  </div>
                  <span className="text-[11px] font-mono text-slate-500 dark:text-slate-400">
                    {isPaid ? 'PAID' : timedOut ? 'Timed out (60s)' : 'Polling 2s'}
                  </span>
                </div>
              </div>

              {/* Order Summary Box */}
              {captureResult && (
                <div className="p-4 rounded-xl bg-slate-50 dark:bg-[#131b26] border border-slate-200/60 dark:border-slate-800/60 max-w-md mx-auto text-left font-mono text-xs space-y-1.5">
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-sans">Order ID:</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">{orderId}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-sans">Capture ID:</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">
                      {traceQuery.data?.captureId || captureResult.captureId}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-sans">Status:</span>
                    <span
                      className={`font-semibold ${
                        isPaid
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : 'text-amber-600 dark:text-amber-400'
                      }`}
                    >
                      {liveStatus}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-sans">Amount:</span>
                    <span className="text-slate-800 dark:text-slate-200 font-bold">
                      ${((orderQuery.data?.totalMinor ?? captureResult.amountCapturedMinor ?? 0) / 100).toFixed(2)}
                    </span>
                  </div>
                </div>
              )}

              {/* Vertical Payment Trace Stepper */}
              <div className="max-w-lg mx-auto pt-2 border-t border-slate-100 dark:border-slate-800/80 text-left space-y-3">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                    Payment trace
                  </h2>
                  <LastUpdatedLabel
                    dataUpdatedAt={traceQuery.dataUpdatedAt}
                    isFetching={traceQuery.isFetching}
                  />
                </div>
                <PaymentTraceStepper
                  trace={traceQuery.data}
                  isLoading={traceQuery.isLoading}
                  isError={traceQuery.isError}
                />
              </div>

              {/* Navigation Actions */}
              <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
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
