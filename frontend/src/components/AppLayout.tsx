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
      <div className="md:hidden flex items-center justify-between px-4 py-3 bg-white dark:bg-[#0d131f] border-b border-slate-200 dark:border-slate-800">
        <Link to="/queue">
          <PayResolveLogo markSize={24} />
        </Link>
        <div className="flex items-center space-x-2">
          <ThemeToggle />
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="p-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 flex items-center justify-center cursor-pointer"
            aria-label="Toggle navigation menu"
          >
            {mobileMenuOpen ? <GoogleIcon name="close" size={20} /> : <GoogleIcon name="menu" size={20} />}
          </button>
        </div>
      </div>

      {/* Mobile Menu Dropdown */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-white dark:bg-[#0d131f] border-b border-slate-200 dark:border-slate-800 px-4 py-3 space-y-1">
          {NAV_ITEMS.map((item) => {
            const active = isActive(item.path);
            return (
              <Link
                key={item.path}
                to={item.path}
                onClick={() => setMobileMenuOpen(false)}
                className={`flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm ${
                  active
                    ? 'bg-slate-100 dark:bg-[#131f33] text-slate-900 dark:text-white font-semibold'
                    : 'text-slate-600 dark:text-slate-400'
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
      <aside className="w-60 bg-white dark:bg-[#0a0f19] border-r border-slate-200 dark:border-slate-800/80 flex flex-col justify-between shrink-0 py-6 px-4 hidden md:flex h-full select-none z-20">
        <div className="space-y-7">
          {/* Brand Logo */}
          <Link to="/" className="px-2 block cursor-pointer">
            <PayResolveLogo markSize={28} />
          </Link>

          {/* Flat Navigation List matching PNG exactly */}
          <nav className="space-y-1 text-sm font-normal">
            {NAV_ITEMS.map((item) => {
              const active = isActive(item.path);
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`flex items-center space-x-3 px-3.5 py-2.5 rounded-lg cursor-pointer transition-all ${
                    active
                      ? 'bg-slate-100 dark:bg-[#131f33] text-slate-900 dark:text-white font-semibold shadow-2xs'
                      : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-50 dark:hover:bg-slate-900/50'
                  }`}
                >
                  <GoogleIcon
                    name={item.icon}
                    size={18}
                    className={`shrink-0 ${
                      active ? 'text-slate-900 dark:text-blue-400' : 'text-slate-500 dark:text-slate-400'
                    }`}
                  />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Bottom Sidebar: Sandbox mode pill + Operator / Sign out / Theme */}
        <div className="space-y-3 pt-4 border-t border-slate-100 dark:border-slate-800/60">
          <Link
            to="/status"
            className="w-full flex items-center space-x-2 px-3 py-1.5 rounded-full border border-emerald-500/40 bg-white dark:bg-[#071d18] text-emerald-700 dark:text-emerald-400 text-xs font-medium cursor-pointer hover:bg-emerald-50/50 dark:hover:bg-[#092620] transition-colors"
          >
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            <span>Sandbox mode</span>
          </Link>

          <div className="flex items-center justify-between pt-1 text-xs">
            <div className="flex items-center space-x-2">
              <span className="px-2.5 py-1 rounded-full bg-slate-100 dark:bg-slate-800/80 text-slate-700 dark:text-slate-300 font-medium">
                {currentUser?.role === 'viewer' ? 'Viewer' : 'Operator'}
              </span>
              <button
                onClick={handleSignOut}
                className="text-blue-600 dark:text-blue-400 font-medium hover:underline cursor-pointer"
              >
                Sign out
              </button>
            </div>
            <ThemeToggle />
          </div>
        </div>
      </aside>

      {/* ─── MAIN PAGE CONTENT ─── */}
      <main className="flex-1 min-w-0 h-full overflow-y-auto overflow-x-hidden p-6 sm:p-10 lg:p-12">
        <div key={location.pathname} className="route-fade-slide">
          {children}
        </div>
      </main>
    </div>
  );
};
