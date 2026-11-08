import { useNavigate, useSearchParams } from 'react-router-dom';
import { AppLayout } from '../components/AppLayout';
import { PayResolveMark } from '../components/PayResolveLogo';
import { GoogleIcon } from '../components/GoogleIcon';
import { toast } from '../lib/toast';

export default function UIStatesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') === 'loading' ? 'loading' : 'empty';
  const navigate = useNavigate();

  const setTab = (tab: 'empty' | 'loading') => {
    setSearchParams({ tab });
  };

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-7">
        
        {/* Top View Selector Switch */}
        <div className="flex items-center justify-between pb-1 border-b border-slate-200 dark:border-slate-800">
          <div className="flex items-center space-x-2">
            <button
              onClick={() => setTab('empty')}
              className={`px-4 py-1.5 rounded-lg text-xs sm:text-sm font-medium cursor-pointer transition-colors ${
                activeTab === 'empty'
                  ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              Empty, error and feedback states
            </button>
            <button
              onClick={() => setTab('loading')}
              className={`px-4 py-1.5 rounded-lg text-xs sm:text-sm font-medium cursor-pointer transition-colors ${
                activeTab === 'loading'
                  ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              Loading states
            </button>
          </div>

          <button
            onClick={() => toast.info('Sample states active.')}
            className="px-4 py-1.5 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs"
          >
            Sample data
          </button>
        </div>

        {/* ─── VIEW 1: Empty, Error & Feedback States ─── */}
        {activeTab === 'empty' && (
          <div className="space-y-7">
            {/* Header */}
            <div>
              <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
                Empty, error and feedback states
              </h1>
              <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                Every state a user can land in, designed.
              </p>
            </div>

            {/* 2-Column Content */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
              
              {/* Left Column */}
              <div className="space-y-6">
                
                {/* Empty queue card */}
                <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                  <div>
                    <h2 className="text-base font-bold text-slate-900 dark:text-white">Empty queue</h2>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1">
                      No exceptions yet. Run a demo checkout and switch on a fault to see your first case.
                    </p>
                  </div>
                  <div className="flex items-center space-x-4 pt-1">
                    <button
                      onClick={() => navigate('/shop')}
                      className="px-5 py-2 rounded-lg bg-[#0066cc] hover:bg-blue-600 text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all"
                    >
                      Open demo shop
                    </button>
                    <button
                      onClick={() => navigate('/queue')}
                      className="text-xs sm:text-sm text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
                    >
                      How cases are detected
                    </button>
                  </div>
                </div>

                {/* Toasts card */}
                <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                  <h2 className="text-base font-bold text-slate-900 dark:text-white">Toasts</h2>
                  
                  <div className="space-y-3">
                    <div className="p-4 rounded-xl bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-700/80 shadow-md flex items-center justify-between">
                      <span className="text-xs sm:text-sm text-slate-800 dark:text-slate-200 font-medium">
                        Order #10482 synced to PAID.
                      </span>
                      <button className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline cursor-pointer">
                        Undo
                      </button>
                    </div>

                    <div className="p-4 rounded-xl bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-700/80 shadow-md flex items-center justify-between">
                      <span className="text-xs sm:text-sm text-slate-800 dark:text-slate-200 font-medium">
                        Could not reach PayPal. Try again.
                      </span>
                      <button className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline cursor-pointer">
                        Retry
                      </button>
                    </div>
                  </div>
                </div>

                {/* Error banner card */}
                <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                  <h2 className="text-base font-bold text-slate-900 dark:text-white">Error banner</h2>
                  <div className="p-4 rounded-xl border border-rose-400 dark:border-rose-800/80 bg-white dark:bg-[#151722] text-xs sm:text-sm text-slate-800 dark:text-slate-200">
                    AI investigator is degraded. Rule-based findings are still shown.
                  </div>
                </div>

              </div>

              {/* Right Column */}
              <div className="space-y-6">
                
                {/* No search results card */}
                <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                  <div>
                    <h2 className="text-base font-bold text-slate-900 dark:text-white">No search results</h2>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1">
                      No case matches "10999". Try an order id, a capture id, or clear the filters.
                    </p>
                  </div>
                  <div className="pt-1">
                    <button
                      onClick={() => navigate('/queue')}
                      className="px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs"
                    >
                      Clear filters
                    </button>
                  </div>
                </div>

                {/* Inline confirm, no modal card */}
                <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                  <h2 className="text-base font-bold text-slate-900 dark:text-white">Inline confirm, no modal</h2>
                  
                  <div className="p-4 rounded-xl bg-[#edf3fa] dark:bg-[#131d2e] border border-blue-200/40 dark:border-blue-900/40 space-y-1">
                    <span className="font-semibold text-xs sm:text-sm text-slate-900 dark:text-white block">
                      Sync order #10482 to PAID?
                    </span>
                    <p className="text-xs text-slate-600 dark:text-slate-400">
                      This changes the shop order only. No money moves.
                    </p>
                  </div>

                  <div className="flex items-center space-x-3 pt-1">
                    <button className="px-5 py-2 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all">
                      Confirm sync
                    </button>
                    <button className="px-5 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs sm:text-sm shadow-2xs cursor-pointer transition-all">
                      Cancel
                    </button>
                  </div>
                </div>

                {/* Form validation card */}
                <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-3 shadow-2xs">
                  <h2 className="text-base font-bold text-slate-900 dark:text-white">Form validation</h2>
                  <div className="space-y-1.5">
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                      New password
                    </label>
                    <input
                      type="password"
                      defaultValue="•••••••"
                      readOnly
                      className="w-full px-3.5 py-2.5 rounded-lg border border-rose-400 dark:border-rose-600 text-sm text-slate-900 dark:text-white bg-white dark:bg-[#131b26]"
                    />
                    <p className="text-xs text-rose-500 dark:text-rose-400 font-normal">
                      Password is too short.
                    </p>
                  </div>
                </div>

              </div>

            </div>
          </div>
        )}

        {/* ─── VIEW 2: Loading States ─── */}
        {activeTab === 'loading' && (
          <div className="space-y-7">
            {/* Header */}
            <div>
              <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
                Loading states
              </h1>
              <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                Our animated mark carries page, route and AI waits. Buttons use a fast ring.
              </p>
            </div>

            {/* 2-Column Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
              
              {/* Top Left: Loading your queue */}
              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-10 flex flex-col items-center justify-center space-y-5 shadow-2xs min-h-[300px]">
                <PayResolveMark size={60} />
                <div className="text-center space-y-1">
                  <h2 className="text-xl font-bold text-slate-900 dark:text-white">Loading your queue</h2>
                  <p className="text-xs text-slate-500 dark:text-slate-400">Fetching the latest cases</p>
                </div>
                {/* Thin loading bar */}
                <div className="w-56 h-1 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                  <div className="h-full bg-[#0066cc] w-1/2 animate-pulse" />
                </div>
              </div>

              {/* Top Right: AI investigator, running */}
              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                <h2 className="text-base font-bold text-slate-900 dark:text-white">AI investigator, running</h2>

                <div className="space-y-3 pt-2 text-xs sm:text-sm">
                  <div className="flex items-center space-x-2.5 pb-2">
                    <PayResolveMark size={20} />
                    <span className="font-semibold text-slate-900 dark:text-white">Investigating case PR-0142</span>
                  </div>

                  <div className="flex items-center justify-between text-slate-600 dark:text-slate-400">
                    <div className="flex items-center space-x-2">
                      <span className="text-emerald-600 dark:text-emerald-400 font-medium">Done</span>
                      <span className="font-mono text-xs">getPaypalOrder</span>
                    </div>
                    <span className="font-mono text-xs text-slate-400">140 ms</span>
                  </div>

                  <div className="flex items-center justify-between text-slate-600 dark:text-slate-400">
                    <div className="flex items-center space-x-2">
                      <span className="text-emerald-600 dark:text-emerald-400 font-medium">Done</span>
                      <span className="font-mono text-xs">getCaptureDetails</span>
                    </div>
                    <span className="font-mono text-xs text-slate-400">212 ms</span>
                  </div>

                  <div className="flex items-center justify-between text-slate-600 dark:text-slate-400">
                    <div className="flex items-center space-x-2">
                      <span className="text-blue-600 dark:text-blue-400 font-medium">Running</span>
                      <span className="font-mono text-xs">listWebhookEvents</span>
                    </div>
                    <span className="font-mono text-xs text-slate-400">...</span>
                  </div>

                  <div className="flex items-center justify-between text-slate-600 dark:text-slate-400">
                    <div className="flex items-center space-x-2">
                      <span className="text-slate-400 font-medium">Queued</span>
                      <span className="font-mono text-xs text-slate-400">getShopOrder</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Bottom Left: Buttons */}
              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                <h2 className="text-base font-bold text-slate-900 dark:text-white">Buttons</h2>
                
                <div className="flex flex-wrap items-center gap-3 pt-2">
                  <button className="px-5 py-2.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer">
                    Approve
                  </button>
                  <button className="px-5 py-2.5 rounded-lg bg-[#0066cc] text-white font-medium text-xs sm:text-sm shadow-sm flex items-center space-x-2">
                    <GoogleIcon name="progress_activity" size={14} className="animate-spin text-white inline-block" />
                    <span>Approving</span>
                  </button>
                  <button disabled className="px-5 py-2.5 rounded-lg bg-blue-400 dark:bg-blue-500/50 text-white font-medium text-xs sm:text-sm cursor-not-allowed">
                    Approve
                  </button>
                  <button className="px-5 py-2.5 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 font-medium text-xs sm:text-sm shadow-2xs">
                    Reject
                  </button>
                </div>
              </div>

              {/* Bottom Right: Skeleton, queue */}
              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
                <h2 className="text-base font-bold text-slate-900 dark:text-white">Skeleton, queue</h2>
                
                <div className="space-y-3 pt-1">
                  <div className="h-3 bg-blue-100/70 dark:bg-slate-800/80 rounded-full w-full animate-pulse" />
                  <div className="h-3 bg-blue-100/70 dark:bg-slate-800/80 rounded-full w-11/12 animate-pulse" />
                  <div className="h-3 bg-blue-100/70 dark:bg-slate-800/80 rounded-full w-full animate-pulse" />
                  <div className="h-3 bg-blue-100/70 dark:bg-slate-800/80 rounded-full w-10/12 animate-pulse" />
                  <div className="h-3 bg-blue-100/70 dark:bg-slate-800/80 rounded-full w-11/12 animate-pulse" />
                  
                  <div className="h-24 bg-blue-50/60 dark:bg-slate-800/40 rounded-xl mt-4 animate-pulse" />
                </div>
              </div>

            </div>
          </div>
        )}

      </div>
    </AppLayout>
  );
}
