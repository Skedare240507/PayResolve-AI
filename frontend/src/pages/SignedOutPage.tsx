import { useNavigate } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';

export default function SignedOutPage() {
  const navigate = useNavigate();

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <h2 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            You were signed out
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
            After 30 minutes of inactivity we end the session to protect approvals.
          </p>
        </div>

        {/* Informative returning banner */}
        <div className="p-3.5 rounded-lg bg-[#edf3fa] dark:bg-[#131b26] border border-slate-200/60 dark:border-slate-800 text-xs sm:text-sm text-slate-700 dark:text-slate-300">
          After you sign in you will return to <strong className="font-semibold text-slate-900 dark:text-white">Case PR-0142</strong>.
        </div>

        <button
          onClick={() => navigate('/signin')}
          className="w-full py-2.5 px-4 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-sm shadow-sm cursor-pointer transition-all"
        >
          Sign in again
        </button>
      </div>
    </AuthLayout>
  );
}
