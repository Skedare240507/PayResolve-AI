import { useSearchParams, Link } from 'react-router-dom';
import { AppLayout } from '../components/AppLayout';
import { PayResolveLogo } from '../components/PayResolveLogo';
import { ThemeToggle } from '../components/ThemeToggle';
import { GoogleIcon } from '../components/GoogleIcon';
import { useAuth } from '../lib/authStore';

export default function ShopCancelPage() {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get('orderId') || 'Pending order';
  const { user } = useAuth();

  const content = (
    <div className="max-w-md mx-auto py-16 px-4">
      <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-lg p-8 text-center space-y-6">
        <div className="w-14 h-14 rounded-2xl bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400 mx-auto flex items-center justify-center">
          <GoogleIcon name="cancel" size={32} />
        </div>
        <div className="space-y-2">
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Checkout cancelled</h1>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            The PayPal Sandbox checkout session for{' '}
            <span className="font-mono font-medium">{orderId}</span> was cancelled by the buyer.
            No funds were captured.
          </p>
        </div>
        <div className="pt-2 flex flex-wrap justify-center gap-3">
          {user ? (
            <Link
              to="/shop"
              className="inline-block px-5 py-2.5 rounded-xl bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-semibold shadow-sm cursor-pointer transition-all"
            >
              Return to shop
            </Link>
          ) : (
            <Link
              to="/signin"
              className="inline-block px-5 py-2.5 rounded-xl bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-semibold shadow-sm cursor-pointer transition-all"
            >
              Sign in to PayResolve
            </Link>
          )}
        </div>
      </div>
    </div>
  );

  if (user) {
    return <AppLayout>{content}</AppLayout>;
  }

  return (
    <div className="min-h-screen bg-[#f8fafc] dark:bg-[#080c14] text-slate-900 dark:text-slate-100">
      <header className="border-b border-slate-200/70 dark:border-slate-800/80 bg-white/80 dark:bg-[#0e1522]/80 backdrop-blur-sm">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between">
          <Link to="/" className="cursor-pointer">
            <PayResolveLogo markSize={24} />
          </Link>
          <ThemeToggle />
        </div>
      </header>
      <main>{content}</main>
    </div>
  );
}
