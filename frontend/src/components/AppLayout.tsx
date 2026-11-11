import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { GoogleIcon } from './GoogleIcon';
import { logoutUser, getCurrentUser } from '../lib/authStore';
import { PayResolveLogo } from './PayResolveLogo';
import { ThemeToggle } from './ThemeToggle';

interface AppLayoutProps {
  children: React.ReactNode;
}

const NAV_ITEMS = [
  { label: 'Queue', path: '/queue', icon: 'list_alt' },
  { label: 'Case', path: '/case/PR-0142', icon: 'account_tree' },
  { label: 'Graph Explorer', path: '/graph-explorer', icon: 'hub' },
  { label: 'Operations', path: '/operations', icon: 'dashboard' },
  { label: 'Events', path: '/events', icon: 'bolt' },
  { label: 'Demo shop', path: '/shop', icon: 'shopping_bag' },
  { label: 'Actions', path: '/actions', icon: 'task_alt' },
  { label: 'Benchmark', path: '/benchmark', icon: 'bar_chart' },
];

export const AppLayout: React.FC<AppLayoutProps> = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const currentUser = getCurrentUser();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const isActive = (path: string) => {
    if (path.startsWith('/case')) {
      return location.pathname.startsWith('/case');
    }
    return location.pathname === path;
  };

  const handleSignOut = async () => {
    await logoutUser();
    navigate('/signed-out');
  };

  return (
    <div className="h-screen w-screen overflow-hidden bg-[#f8fafc] dark:bg-[#080c14] text-slate-900 dark:text-slate-100 flex flex-col md:flex-row font-sans">
      
      {/* Mobile Top Bar */}
      <div className="md:hidden flex items-center justify-between px-4 py-3 bg-white dark:bg-[#0d131f] border-b border-slate-200 dark:border-slate-800 shrink-0 z-30">
        <Link to="/queue">
          <PayResolveLogo markSize={24} />
        </Link>
        <div className="flex items-center space-x-2">
          <ThemeToggle />
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="p-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 flex items-center justify-center cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
            aria-label="Toggle navigation menu"
          >
            {mobileMenuOpen ? <GoogleIcon name="close" size={20} /> : <GoogleIcon name="menu" size={20} />}
          </button>
        </div>
      </div>

      {/* Mobile Menu Dropdown */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-white/95 dark:bg-[#0d131f]/95 backdrop-blur-md border-b border-slate-200 dark:border-slate-800 px-4 py-3 space-y-1 shrink-0 z-20 shadow-md">
          {NAV_ITEMS.map((item) => {
            const active = isActive(item.path);
            return (
              <Link
                key={item.path}
                to={item.path}
                onClick={() => setMobileMenuOpen(false)}
                className={`flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                  active
                    ? 'bg-slate-100 dark:bg-[#131f33] text-slate-900 dark:text-white font-semibold'
                    : 'text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-900/40'
                }`}
              >
                <GoogleIcon
                  name={item.icon}
                  size={18}
                  className={active ? 'text-slate-900 dark:text-blue-400' : 'text-slate-500 dark:text-slate-400'}
                />
                <span>{item.label}</span>
              </Link>
            );
          })}
          <div className="pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs">
            <span className="text-slate-500">Role: {currentUser?.role || 'Operator'}</span>
            <button onClick={handleSignOut} className="text-blue-600 dark:text-blue-400 font-medium cursor-pointer">
              Sign out
            </button>
          </div>
        </div>
      )}

      {/* ─── DESKTOP SIDEBAR ─── */}
      <aside className="w-64 bg-[#0a0f1d] dark:bg-[#F5F5F0] border-r border-[#151c2e] dark:border-[#E2E2DC] flex flex-col justify-between shrink-0 py-6 px-4 hidden md:flex h-full select-none z-20 text-slate-300 dark:text-slate-800 transition-colors duration-200 shadow-sm">
        <div className="space-y-6">
          {/* Brand Logo — Auto-switches based on active theme */}
          <Link to="/" className="px-2 py-1 block cursor-pointer" aria-label="PayResolve AI Home">
            <PayResolveLogo height={32} />
          </Link>

          {/* Target Navigation List with WCAG AA contrast tokens */}
          <nav className="space-y-1.5 text-[13.5px] font-medium px-0.5">
            {NAV_ITEMS.map((item) => {
              const active = isActive(item.path);
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`flex items-center justify-between px-3.5 py-2.5 rounded-xl cursor-pointer transition-all my-0.5 ${
                    active
                      ? 'bg-[#121c33] text-white font-semibold dark:bg-[#E3E3DC] dark:text-slate-950 dark:shadow-2xs'
                      : 'text-slate-300 hover:text-white hover:bg-white/[0.06] dark:text-slate-700 dark:hover:text-slate-950 dark:hover:bg-black/[0.05]'
                  }`}
                >
                  <div className="flex items-center space-x-3.5">
                    <GoogleIcon
                      name={item.icon}
                      size={19}
                      className={`shrink-0 transition-colors ${
                        active
                          ? 'text-[#3b82f6] dark:text-blue-600'
                          : 'text-slate-300 dark:text-slate-600'
                      }`}
                    />
                    <span>{item.label}</span>
                  </div>

                  {/* Badge count matching screenshot on Queue */}
                  {item.label === 'Queue' && (
                    <span
                      className={`text-[11px] font-mono px-2 py-0.5 rounded-full ${
                        active
                          ? 'bg-[#1e293b] text-[#93c5fd] font-medium dark:bg-slate-300 dark:text-slate-900'
                          : 'bg-[#151d2f] text-slate-400 dark:bg-slate-200 dark:text-slate-700'
                      }`}
                    >
                      12
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Bottom Sidebar: Sandbox mode pill with LIVE badge + Operator / Sign out / Theme */}
        <div className="space-y-3.5 pt-4 my-1 border-t border-[#151c2e] dark:border-[#deded8]">
          <Link
            to="/status"
            className="w-full flex items-center justify-between px-3.5 py-2 rounded-xl border border-emerald-500/30 bg-[#061814]/70 text-emerald-400 text-xs font-medium cursor-pointer hover:bg-[#061814] dark:bg-emerald-50 dark:border-emerald-300 dark:text-emerald-900 dark:hover:bg-emerald-100/70 transition-colors shadow-2xs"
          >
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400 dark:bg-emerald-600 animate-pulse" />
              <span className="text-[12px] font-medium">Sandbox mode</span>
            </div>
            <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-emerald-950/80 border border-emerald-500/40 text-emerald-300 dark:bg-emerald-200 dark:border-emerald-400 dark:text-emerald-950 font-bold">
              LIVE
            </span>
          </Link>

          <div className="flex items-center justify-between pt-1 px-1 text-xs">
            <div className="flex items-center space-x-2.5">
              <div className="w-7 h-7 rounded-full bg-[#1e293b] dark:bg-slate-300 flex items-center justify-center font-bold text-[11px] text-slate-300 dark:text-slate-800">
                OP
              </div>
              <div className="flex flex-col">
                <span className="text-white dark:text-slate-950 text-xs font-semibold leading-tight">
                  {currentUser?.role === 'viewer' ? 'Viewer' : 'Operator'}
                </span>
                <button
                  onClick={handleSignOut}
                  className="text-[11px] text-slate-400 hover:text-slate-200 dark:text-slate-600 dark:hover:text-slate-950 transition-colors text-left cursor-pointer"
                >
                  Sign out
                </button>
              </div>
            </div>
            <ThemeToggle />
          </div>
        </div>
      </aside>

      {/* ─── MAIN PAGE CONTENT ─── */}
      <main className="flex-1 min-w-0 h-full overflow-y-auto overflow-x-hidden p-4 sm:p-8 lg:p-12">
        <div key={location.pathname} className="route-fade-slide">
          {children}
        </div>
      </main>
    </div>
  );
};
