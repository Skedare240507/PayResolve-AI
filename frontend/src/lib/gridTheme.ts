import {
  AllCommunityModule,
  ModuleRegistry,
  colorSchemeDark,
  colorSchemeLight,
  themeQuartz,
  type ColumnState,
  type GridApi,
  type Theme,
} from 'ag-grid-community';
import { useMemo } from 'react';
import { useTheme } from '../lib/theme';

let gridRegistered = false;

export function ensureAgGridRegistered(): void {
  if (!gridRegistered) {
    ModuleRegistry.registerModules([AllCommunityModule]);
    gridRegistered = true;
  }
}

function readCssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback;
  const val = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return val || fallback;
}

/**
 * Builds an AG Grid Community v36 theme using the official Theming API
 * (`themeQuartz.withPart(...).withParams(...)`) sourced from app CSS variables.
 */
export function buildPayResolveGridTheme(isDark: boolean): Theme {
  ensureAgGridRegistered();

  const surface = readCssVar('--bg-surface', isDark ? '#0e1522' : '#ffffff');
  const surfaceMuted = readCssVar('--bg-surface-muted', isDark ? '#141f33' : '#f8fafc');
  const borderColor = readCssVar('--border-subtle', isDark ? '#1e293b' : '#e2e8f0');
  const textPrimary = readCssVar('--text-primary', isDark ? '#f1f5f9' : '#0f172a');
  const textSecondary = readCssVar('--text-secondary', isDark ? '#cbd5e1' : '#475569');
  const accentPrimary = readCssVar('--accent-primary', isDark ? '#3b82f6' : '#0066cc');
  const focusRing = readCssVar('--focus-ring', isDark ? '#60a5fa' : '#2563eb');

  return themeQuartz
    .withPart(isDark ? colorSchemeDark : colorSchemeLight)
    .withParams({
      fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: 13,
      headerFontSize: 12,
      headerFontWeight: 600,
      rowHeight: 48,
      headerHeight: 44,
      cellHorizontalPaddingScale: 1.1,
      backgroundColor: surface,
      foregroundColor: textPrimary,
      headerBackgroundColor: surfaceMuted,
      headerTextColor: textSecondary,
      chromeBackgroundColor: surfaceMuted,
      borderColor,
      rowHoverColor: isDark ? '#141f33' : '#f1f5f9',
      selectedRowBackgroundColor: isDark ? '#1a2a47' : '#eff6ff',
      accentColor: accentPrimary,
      focusShadow: {
        radius: 0,
        spread: 2,
        color: focusRing,
      },
      wrapperBorderRadius: 12,
    });
}

export function useGridTheme(): { gridTheme: Theme; isDark: boolean } {
  const { isDark } = useTheme();
  const gridTheme = useMemo(() => buildPayResolveGridTheme(isDark), [isDark]);
  return { gridTheme, isDark };
}

export function saveGridColumnState(storageKey: string, api: GridApi): void {
  try {
    const state = api.getColumnState();
    localStorage.setItem(storageKey, JSON.stringify(state));
  } catch {
    // ignore storage errors
  }
}

export function restoreGridColumnState(storageKey: string, api: GridApi): void {
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return;
    const parsed = JSON.parse(raw) as ColumnState[];
    if (Array.isArray(parsed) && parsed.length > 0) {
      api.applyColumnState({ state: parsed, applyOrder: true });
    }
  } catch {
    // ignore malformed state
  }
}

export function resetGridColumnState(storageKey: string, api: GridApi): void {
  try {
    localStorage.removeItem(storageKey);
  } catch {
    // ignore
  }
  api.resetColumnState();
}
