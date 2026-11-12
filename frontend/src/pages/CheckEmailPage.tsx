import { useState, useEffect, useRef } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';
import { requestPasswordReset } from '../lib/authStore';
import { ApiError } from '../lib/api';
import { toast } from '../lib/toast';
import { GoogleIcon } from '../components/GoogleIcon';

interface ResetLocationState {
  identifier?: string;
  message?: string;
  resetToken?: string | null;
  resetUrl?: string | null;
  demoNotice?: string | null;
}

export default function CheckEmailPage() {
  const [countdown, setCountdown] = useState(30);
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const state = (location.state as ResetLocationState | null) || null;
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const identifier =
    state?.identifier || searchParams.get('identifier') || searchParams.get('email') || '';

  const [serverMessage, setServerMessage] = useState(
    state?.message || 'If that account exists, ask the administrator to reset it.'
  );
  const [resetToken, setResetToken] = useState<string | null>(
    state?.resetToken || searchParams.get('token')
  );
  const [resetUrl, setResetUrl] = useState<string | null>(
    state?.resetUrl || (searchParams.get('token') ? `/reset-password?token=${encodeURIComponent(searchParams.get('token')!)}` : null)
  );
  const [demoNotice, setDemoNotice] = useState<string | null>(state?.demoNotice || null);

  useEffect(() => {
    intervalRef.current = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          return 0;
        }
        return c - 1;
      });
    }, 1000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  const handleResend = async () => {
    if (!identifier) return;
    try {
      const res = await requestPasswordReset(identifier);
      setServerMessage(res.message);
      setResetToken(res.resetToken ?? null);
      setResetUrl(res.resetUrl ?? null);
      setDemoNotice(res.demoNotice ?? null);
      toast.info('Password reset request submitted.');
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 429) {
        toast.error(err.message || 'Rate limit reached. Please wait before trying again.');
      } else {
        toast.error('Failed to submit password reset request.');
      }
    }
    setCountdown(30);
    if (intervalRef.current) clearInterval(intervalRef.current);
    intervalRef.current = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          return 0;
        }
        return c - 1;
      });
    }, 1000);
  };

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <h2 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            {resetToken ? 'Demo reset link ready' : 'Reset request recorded'}
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
            {serverMessage}
          </p>
        </div>

        {resetToken && resetUrl ? (
          <div className="p-3.5 rounded-xl bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800/80 space-y-1.5">
            <div className="flex items-center space-x-2 text-xs font-semibold text-blue-800 dark:text-blue-300">
              <GoogleIcon name="info" className="text-sm" />
              <span>Demo Reset Links Enabled (Viewer Only)</span>
            </div>
            <p className="text-xs text-blue-700 dark:text-blue-400 leading-relaxed">
              {demoNotice || 'No email service is configured. Use the link below within 15 minutes to reset this viewer password.'}
            </p>
            <div className="pt-1">
              <Link
                to={resetUrl}
                className="inline-flex items-center text-xs font-semibold text-blue-600 dark:text-blue-300 hover:underline"
              >
                Proceed to reset password &rarr;
              </Link>
            </div>
          </div>
        ) : (
          <div className="bg-[#edf3fa] dark:bg-[#131b26] rounded-2xl p-6 space-y-3 border border-slate-200/60 dark:border-slate-800/80">
            <div className="flex items-center space-x-2 text-xs font-semibold text-slate-700 dark:text-slate-200">
              <GoogleIcon name="mail" className="text-base text-slate-500" />
              <span>No email was sent</span>
            </div>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
              This deployment does not send password reset emails, and demo on-screen reset links are disabled or not permitted for administrator accounts.
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              <Link to="/reset-password" className="text-blue-600 dark:text-blue-400 hover:underline font-medium">
                Already have a reset token?
              </Link>
            </p>
          </div>
        )}

        {identifier && (
          <div>
            <button
              type="button"
              disabled={countdown > 0}
              onClick={handleResend}
              className="w-full py-2.5 px-4 rounded-lg bg-white dark:bg-[#131b26] text-slate-600 dark:text-slate-300 font-medium text-xs sm:text-sm border border-slate-200 dark:border-slate-800 cursor-pointer disabled:cursor-default text-center hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
            >
              {countdown > 0 ? `Request again in ${countdown} s` : 'Submit request again'}
            </button>
          </div>
        )}

        <div className="flex items-center justify-between pt-1">
          <Link
            to="/forgot-password"
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            Try a different account
          </Link>
          <Link
            to="/sign-in"
            className="text-xs font-medium text-slate-500 dark:text-slate-400 hover:underline cursor-pointer"
          >
            Back to sign in
          </Link>
        </div>
      </div>
    </AuthLayout>
  );
}
