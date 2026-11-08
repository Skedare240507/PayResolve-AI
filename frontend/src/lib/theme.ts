import { useState, useEffect } from 'react';

export type ThemeMode = 'light' | 'dark';

export function getInitialTheme(): ThemeMode {
  if (typeof window === 'undefined') return 'light';
  try {
    const saved = localStorage.getItem('payresolve-theme');
    if (saved === 'dark' || saved === 'light') {
      return saved;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

export function applyTheme(theme: ThemeMode) {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  if (theme === 'dark') {
    root.classList.add('dark');
  } else {
    root.classList.remove('dark');
  }
  try {
    localStorage.setItem('payresolve-theme', theme);
  } catch {
    // ignore
  }
  window.dispatchEvent(new CustomEvent('payresolve-theme-change', { detail: { theme } }));
}

export function toggleTheme(): ThemeMode {
  const current = document.documentElement.classList.contains('dark') ? 'dark' : 'light';
  const next: ThemeMode = current === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  return next;
}

export function useTheme() {
  const [theme, setTheme] = useState<ThemeMode>(() => {
    if (typeof document !== 'undefined') {
      return document.documentElement.classList.contains('dark') ? 'dark' : 'light';
    }
    return 'light';
  });

  useEffect(() => {
    const syncTheme = () => {
      const isDark = document.documentElement.classList.contains('dark');
      setTheme(isDark ? 'dark' : 'light');
    };

    // Observer for direct DOM class changes
    const observer = new MutationObserver(syncTheme);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });

    // Events for custom dispatch and cross-tab storage
    window.addEventListener('payresolve-theme-change', syncTheme);
    window.addEventListener('storage', syncTheme);

    return () => {
      observer.disconnect();
      window.removeEventListener('payresolve-theme-change', syncTheme);
      window.removeEventListener('storage', syncTheme);
    };
  }, []);

  return {
    theme,
    isDark: theme === 'dark',
    toggleTheme,
    setTheme: (t: ThemeMode) => applyTheme(t),
  };
}

// Auto-initialize theme on import
if (typeof window !== 'undefined') {
  applyTheme(getInitialTheme());

  // Listen for OS theme changes if user hasn't explicitly set localStorage
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
    try {
      if (!localStorage.getItem('payresolve-theme')) {
        applyTheme(e.matches ? 'dark' : 'light');
      }
    } catch {
      // ignore
    }
  });
}
