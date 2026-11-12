import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { GoogleIcon } from './GoogleIcon';
import { logoutUser, getCurrentUser } from '../lib/authStore';
import { Logo } from './Logo';
import { ThemeToggle } from './ThemeToggle';
import { DemoFaultBanner } from './DemoFaultBanner';

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
        <Link to="/queue" aria-label="PayResolve AI Home">
          <Logo variant="symbol" size="mobileHeader" />
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
      <aside className="w-64 bg-white dark:bg-[#0a0f1d] border-r border-slate-200 dark:border-[#151c2e] flex flex-col justify-between shrink-0 py-6 px-4 hidden md:flex h-full select-none z-20 text-slate-700 dark:text-slate-300 transition-colors duration-200 shadow-sm">
        <div className="space-y-6">
          {/* Brand Logo — Auto-switches based on active theme */}
          <Link to="/" className="px-2 py-1 block cursor-pointer" aria-label="PayResolve AI Home">
            <Logo variant="lockup" size="sidebar" />
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
                      ? 'bg-slate-100 text-slate-950 font-semibold shadow-2xs dark:bg-[#121c33] dark:text-white'
                      : 'text-slate-600 hover:text-slate-950 hover:bg-slate-50 dark:text-slate-400 dark:hover:text-white dark:hover:bg-white/[0.06]'
                  }`}
                >
                  <div className="flex items-center space-x-3.5">
                    <GoogleIcon
                      name={item.icon}
                      size={19}
                      className={`shrink-0 transition-colors ${
                        active
                          ? 'text-blue-600 dark:text-[#3b82f6]'
                          : 'text-slate-400 dark:text-slate-400'
                      }`}
                    />
                    <span>{item.label}</span>
                  </div>

                  {/* Badge count matching screenshot on Queue */}
                  {item.label === 'Queue' && (
                    <span
                      className={`text-[11px] font-mono px-2 py-0.5 rounded-full ${
                        active
                          ? 'bg-blue-100 text-blue-800 font-medium dark:bg-[#1e293b] dark:text-[#93c5fd]'
                          : 'bg-slate-100 text-slate-600 dark:bg-[#151d2f] dark:text-slate-400'
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
        <div className="space-y-3.5 pt-4 my-1 border-t border-slate-200 dark:border-[#151c2e]">
          <Link
            to="/status"
            className="w-full flex items-center justify-between px-3.5 py-2 rounded-xl border border-emerald-300 bg-emerald-50 text-emerald-900 text-xs font-medium cursor-pointer hover:bg-emerald-100/70 dark:border-emerald-500/30 dark:bg-[#061814]/70 dark:text-emerald-400 dark:hover:bg-[#061814] transition-colors shadow-2xs"
          >
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-emerald-600 dark:bg-emerald-400 animate-pulse" />
              <span className="text-[12px] font-medium">Sandbox mode</span>
            </div>
            <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-emerald-200 border border-emerald-400 text-emerald-950 font-bold dark:bg-emerald-950/80 dark:border-emerald-500/40 dark:text-emerald-300">
              LIVE
            </span>
          </Link>

          <div className="flex items-center justify-between pt-1 px-1 text-xs">
            <div className="flex items-center space-x-2.5">
              <div className="w-7 h-7 rounded-full bg-slate-200 text-slate-800 dark:bg-[#1e293b] dark:text-slate-300 flex items-center justify-center font-bold text-[11px]">
                OP
              </div>
              <div className="flex flex-col">
                <span className="text-slate-950 dark:text-white text-xs font-semibold leading-tight">
                  {currentUser?.role === 'viewer' ? 'Viewer' : 'Operator'}
                </span>
                <button
                  onClick={handleSignOut}
                  className="text-[11px] text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200 transition-colors text-left cursor-pointer"
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
        <DemoFaultBanner />
        <div key={location.pathname} className="route-fade-slide">
          {children}
        </div>
      </main>
    </div>
  );
};
