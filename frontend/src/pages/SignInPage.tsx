import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';
import { authenticateUser } from '../lib/authStore';
import { GoogleIcon } from '../components/GoogleIcon';

export default function SignInPage() {
  const [identifier, setIdentifier] = useState('operator');
  const [password, setPassword] = useState('••••••••');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSelectRole = (role: 'operator' | 'viewer') => {
    if (role === 'operator') {
      setIdentifier('operator');
      setPassword('operator123');
    } else {
      setIdentifier('viewer');
      setPassword('viewer123');
    }
    setErrorMsg('');
  };

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
      const res = await authenticateUser(identifier.trim(), realPassword);
      if (!res.success && realPassword.endsWith('!')) {
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

  const isOperator = identifier.toLowerCase() === 'operator';
  const isViewer = identifier.toLowerCase() === 'viewer';

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-blue-50 dark:bg-blue-950/50 border border-blue-200/80 dark:border-blue-900/60 text-[11px] font-mono text-blue-700 dark:text-blue-300 font-semibold mb-3">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
            <span>PayPal Sandbox Demo Access</span>
          </div>
          <h2 className="text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight">
            Sign in to PayResolve
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
            Select a demo role or enter your credentials to access the exception queue.
          </p>
        </div>

        {/* Role Quick-Select Cards */}
        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={() => handleSelectRole('operator')}
            className={`p-3 rounded-xl border text-left cursor-pointer transition-all ${
              isOperator
                ? 'bg-blue-50/70 dark:bg-blue-950/40 border-blue-500/80 shadow-xs'
                : 'bg-white dark:bg-slate-900/40 border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
            }`}
          >
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-bold text-slate-900 dark:text-white">Operator</span>
              {isOperator && (
                <span className="w-2 h-2 rounded-full bg-blue-600" />
              )}
            </div>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
              Full access · can approve actions
            </p>
          </button>

          <button
            type="button"
            onClick={() => handleSelectRole('viewer')}
            className={`p-3 rounded-xl border text-left cursor-pointer transition-all ${
              isViewer
                ? 'bg-blue-50/70 dark:bg-blue-950/40 border-blue-500/80 shadow-xs'
                : 'bg-white dark:bg-slate-900/40 border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
            }`}
          >
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-bold text-slate-900 dark:text-white">Viewer</span>
              {isViewer && (
                <span className="w-2 h-2 rounded-full bg-blue-600" />
              )}
            </div>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
              Read-only · inspect queue &amp; logs
            </p>
          </button>
        </div>

        {errorMsg && (
          <div className="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/60 text-rose-700 dark:text-rose-400 text-xs font-medium flex items-start gap-2">
            <GoogleIcon name="error" size={16} className="text-rose-600 shrink-0 mt-0.5" />
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Username
            </label>
            <div className="relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400">
                <GoogleIcon name="person" size={18} />
              </span>
              <input
                type="text"
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                placeholder="operator or viewer"
                className="w-full pl-10 pr-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent bg-white dark:bg-[#131b26] transition"
                required
              />
            </div>
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
            <div className="relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400">
                <GoogleIcon name="lock" size={18} />
              </span>
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onFocus={() => {
                  if (password === '••••••••') setPassword('');
                }}
                placeholder="Enter password"
                className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent bg-white dark:bg-[#131b26] transition"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1 cursor-pointer"
                title={showPassword ? 'Hide password' : 'Show password'}
              >
                <GoogleIcon name={showPassword ? 'visibility_off' : 'visibility'} size={18} />
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 active:scale-[0.98] text-white font-semibold text-sm shadow-sm cursor-pointer transition-all disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {loading ? (
              <>
                <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                <span>Signing in...</span>
              </>
            ) : (
              <span>Sign in</span>
            )}
          </button>
        </form>

        <div className="pt-4 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 font-medium text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400 transition-colors cursor-pointer"
          >
            <GoogleIcon name="arrow_back" size={15} />
            <span>Back to landing page</span>
          </Link>
          <span className="font-mono text-[11px] text-slate-400">operator123 / viewer123</span>
        </div>
      </div>
    </AuthLayout>
  );
}
