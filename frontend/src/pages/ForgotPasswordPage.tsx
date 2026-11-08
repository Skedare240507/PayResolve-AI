import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';
import { requestPasswordReset } from '../lib/authStore';
import { toast } from '../lib/toast';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('operator@payresolve.dev');
  const [isSending, setIsSending] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;

    setErrorMsg('');
    setIsSending(true);
    try {
      const res = await requestPasswordReset(email.trim());
      toast.info('Reset link generated. In demo mode, link is available on screen.');
      navigate(`/check-email?email=${encodeURIComponent(email.trim())}&token=${encodeURIComponent(res.resetToken)}`);
    } catch {
      // Even if failed or unknown email, navigate to check-email per security best practice
      navigate(`/check-email?email=${encodeURIComponent(email.trim())}`);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <h2 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            Forgot your password?
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
            Enter your email and we will send a reset link. It expires in 30 minutes.
          </p>
        </div>

        {errorMsg && (
          <div className="p-3 rounded-lg bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/60 text-rose-700 dark:text-rose-400 text-xs font-medium">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Email
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isSending}
              className="w-full px-3.5 py-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent bg-white dark:bg-[#131b26] disabled:opacity-60"
              required
            />
            <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
              We never confirm whether an account exists.
            </p>
          </div>

          <button
            type="submit"
            disabled={isSending}
            className="w-full py-2.5 px-4 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] disabled:bg-blue-400 text-white font-medium text-sm shadow-sm cursor-pointer transition-all flex items-center justify-center space-x-2"
          >
            {isSending ? (
              <span>Sending reset link...</span>
            ) : (
              <span>Send reset link</span>
            )}
          </button>
        </form>

        <div className="pt-2">
          <Link
            to="/signin"
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            Back to sign in
          </Link>
        </div>
      </div>
    </AuthLayout>
  );
}
