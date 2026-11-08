import React from 'react';
import { GoogleIcon } from './GoogleIcon';
import { useTheme } from '../lib/theme';

interface ThemeToggleProps {
  className?: string;
  variant?: 'default' | 'nav';
}

export const ThemeToggle: React.FC<ThemeToggleProps> = ({ className = '', variant = 'default' }) => {
  const { isDark, toggleTheme } = useTheme();

  const handleToggle = (e: React.MouseEvent<HTMLButtonElement>) => {
    // If reduced motion is requested or browser lacks startViewTransition, toggle directly
    const isReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (isReducedMotion || !document.startViewTransition) {
      toggleTheme();
      return;
    }

    const rect = e.currentTarget.getBoundingClientRect();
    const x = rect.left + rect.width / 2;
    const y = rect.top + rect.height / 2;
    const endRadius = Math.hypot(
      Math.max(x, window.innerWidth - x),
      Math.max(y, window.innerHeight - y)
    );

    const transition = document.startViewTransition(() => {
      toggleTheme();
    });

    transition.ready.then(() => {
      document.documentElement.animate(
        {
          clipPath: [
            `circle(0px at ${x}px ${y}px)`,
            `circle(${endRadius}px at ${x}px ${y}px)`,
          ],
        },
        {
          duration: 400,
          easing: 'ease-out',
          pseudoElement: '::view-transition-new(root)',
        }
      );
    });
  };

  if (variant === 'nav') {
    return (
      <button
        type="button"
        onClick={handleToggle}
        title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
        aria-label="Toggle theme"
        className={`px-3 py-1.5 rounded-lg border border-white/20 bg-white/10 hover:bg-white/20 active:scale-[0.97] text-white backdrop-blur-xs transition-all cursor-pointer inline-flex items-center gap-1.5 text-xs font-medium ${className}`}
      >
        {isDark ? (
          <>
            <GoogleIcon name="light_mode" size={15} className="text-amber-300" />
            <span className="hidden sm:inline">Light</span>
          </>
        ) : (
          <>
            <GoogleIcon name="dark_mode" size={15} className="text-blue-100" />
            <span className="hidden sm:inline">Dark</span>
          </>
        )}
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={handleToggle}
      title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
      aria-label="Toggle theme"
      className={`p-2 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-[0.97] transition-all cursor-pointer inline-flex items-center justify-center ${className}`}
    >
      {isDark ? (
        <GoogleIcon name="light_mode" size={16} className="text-amber-400" />
      ) : (
        <GoogleIcon name="dark_mode" size={16} className="text-slate-600 dark:text-slate-300" />
      )}
    </button>
  );
};
