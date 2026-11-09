import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';
import { authenticateUser } from '../lib/authStore';

export default function SignInPage() {
  const [identifier, setIdentifier] = useState('operator');
  const [password, setPassword] = useState('••••••••');
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');

    // If using the default bullet placeholder value, determine matching role password
    let realPassword = password;
    if (password === '••••••••') {
      realPassword = identifier.trim().toLowerCase() === 'viewer' ? 'viewer123' : 'operator123';
    }

    if (!identifier.trim() || !realPassword) {
      setErrorMsg('Please enter your username and password.');
      return;
    }

    setLoading(true);
    try {
      let res = await authenticateUser(identifier.trim(), realPassword);
      if (!res.success && realPassword.endsWith('!')) {
        // Tolerant retry without trailing '!' if user typed operator123! or viewer123!
        const trimmedPwd = realPassword.slice(0, -1);
        const retryRes = await authenticateUser(identifier.trim(), trimmedPwd);
        if (retryRes.success) {
          navigate('/queue');
          return;
        }
      }
      if (!res.success) {
        setErrorMsg(res.error || 'Invalid credentials.');
        return;
      }
      navigate('/queue');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <h2 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            Sign in
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5">
            Operators can approve actions. Viewers explore read-only.
          </p>
          <div className="flex items-center gap-2 mt-3">
            <span className="text-xs text-slate-500 dark:text-slate-400">Quick fill:</span>
            <button
              type="button"
              onClick={() => {
                setIdentifier('operator');
                setPassword('operator123');
                setErrorMsg('');
              }}
              className="px-2 py-0.5 text-xs font-medium rounded-md bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800 hover:bg-blue-100 dark:hover:bg-blue-900/40 cursor-pointer transition-colors"
            >
              Operator
            </button>
            <button
              type="button"
              onClick={() => {
                setIdentifier('viewer');
                setPassword('viewer123');
                setErrorMsg('');
              }}
              className="px-2 py-0.5 text-xs font-medium rounded-md bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-200 dark:hover:bg-slate-700 cursor-pointer transition-colors"
            >
              Viewer
            </button>
          </div>
        </div>

        {errorMsg && (
          <div className="p-3 rounded-lg bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/60 text-rose-700 dark:text-rose-400 text-xs font-medium">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Username
            </label>
            <input
              type="text"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent bg-white dark:bg-[#131b26]"
              required
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                Password
              </label>
              <Link
                to="/forgot-password"
                className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
              >
                Forgot password?
              </Link>
            </div>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onFocus={() => {
                if (password === '••••••••') setPassword('');
              }}
              className="w-full px-3.5 py-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent bg-white dark:bg-[#131b26]"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-sm shadow-sm cursor-pointer transition-all disabled:opacity-70 disabled:cursor-not-allowed"
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <p className="text-xs text-slate-500 dark:text-slate-400 pt-2">
          Judges: viewer credentials are in the Devpost submission.
        </p>
      </div>
    </AuthLayout>
  );
}
