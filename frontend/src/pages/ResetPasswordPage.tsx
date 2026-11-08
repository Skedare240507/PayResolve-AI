import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';
import { performPasswordReset } from '../lib/authStore';
import { toast } from '../lib/toast';

export default function ResetPasswordPage() {
  const [newPassword, setNewPassword] = useState('•••••••');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [touched, setTouched] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const token = searchParams.get('token') || 'demo_token';

  const realPassword = newPassword === '•••••••' ? 'shortpw' : newPassword;
  const isLongEnough = realPassword.length >= 8;
  const isTooShort = realPassword.length > 0 && !isLongEnough;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setTouched(true);
    setErrorMsg('');

    if (!isLongEnough) {
      setErrorMsg('Password must be at least 8 characters.');
      return;
    }

    if (realPassword !== confirmPassword) {
      setErrorMsg('Passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      const res = await performPasswordReset(token, realPassword);
      if (res.success) {
        toast.success('Password updated successfully.');
        navigate('/password-updated');
      } else {
        setErrorMsg(res.message || 'Failed to reset password. The link may have expired.');
      }
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Invalid or expired token.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <h2 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            Choose a new password
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
            Use at least 8 characters.
          </p>
        </div>

        {errorMsg && (
          <div className="p-3 rounded-lg bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/60 text-rose-700 dark:text-rose-400 text-xs font-medium">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              New password
            </label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => {
                setNewPassword(e.target.value);
                setTouched(true);
              }}
              onFocus={() => {
                if (newPassword === '•••••••') setNewPassword('');
              }}
              className={`w-full px-3.5 py-2.5 rounded-lg border text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:border-transparent bg-white dark:bg-[#131b26] ${
                isTooShort && touched
                  ? 'border-rose-400 dark:border-rose-500 focus:ring-rose-500'
                  : 'border-slate-200 dark:border-slate-800 focus:ring-blue-600'
              }`}
              required
            />
            {isTooShort && touched && (
              <p className="text-xs text-rose-500 dark:text-rose-400 font-normal pt-0.5">
                Password is too short.
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Confirm password
            </label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent bg-white dark:bg-[#131b26]"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-sm shadow-sm cursor-pointer transition-all disabled:opacity-70 mt-2"
          >
            {loading ? 'Updating password...' : 'Update password'}
          </button>
        </form>
      </div>
    </AuthLayout>
  );
}
