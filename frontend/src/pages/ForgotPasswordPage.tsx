import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';
import { requestPasswordReset } from '../lib/authStore';
import { ApiError } from '../lib/api';

export default function ForgotPasswordPage() {
  const [identifier, setIdentifier] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = identifier.trim();
    if (!trimmed) return;

    setErrorMsg('');
    setIsSending(true);
    try {
      const res = await requestPasswordReset(trimmed);
      navigate(`/check-email?identifier=${encodeURIComponent(trimmed)}`, {
        state: {
          identifier: trimmed,
          message: res.message,
          resetToken: res.resetToken ?? null,
          resetUrl: res.resetUrl ?? null,
          demoNotice: res.demoNotice ?? null,
        },
      });
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 429) {
        setErrorMsg(err.message || 'Too many reset requests. Please wait before trying again.');
      } else {
        setErrorMsg(err instanceof Error ? err.message : 'Unable to submit reset request.');
      }
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
            Enter your username or email. Any issued reset token expires in 15 minutes.
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
              Username or email
            </label>
            <input
              type="text"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="viewer or viewer@payresolve.local"
              disabled={isSending}
              className="w-full px-3.5 py-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent bg-white dark:bg-[#131b26] disabled:opacity-60"
              required
            />
            <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
              We never confirm whether an account exists, and outbound email is not enabled here.
            </p>
          </div>

          <button
            type="submit"
            disabled={isSending}
            className="w-full py-2.5 px-4 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] disabled:bg-blue-400 text-white font-medium text-sm shadow-sm cursor-pointer transition-all flex items-center justify-center space-x-2"
          >
            {isSending ? <span>Submitting request...</span> : <span>Request password reset</span>}
          </button>
        </form>

        <div className="pt-2">
          <Link
            to="/sign-in"
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            Back to sign in
          </Link>
        </div>
      </div>
    </AuthLayout>
  );
}
