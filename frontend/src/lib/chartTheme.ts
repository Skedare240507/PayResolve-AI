import { AllCommunityModule, ModuleRegistry, type AgChartTheme } from 'ag-charts-community';
import { useMemo } from 'react';
import { useTheme } from './theme';

let chartsRegistered = false;

export function ensureAgChartsRegistered(): void {
  if (!chartsRegistered) {
    ModuleRegistry.registerModules([AllCommunityModule]);
    chartsRegistered = true;
  }
}

// High-contrast accessible palette verified >= 3.1:1 on both #ffffff (light) and #0e1522 (dark)
export const PAYRESOLVE_CHART_PALETTE = [
  '#2563eb', // Cobalt Blue
  '#059669', // Emerald Green
  '#d97706', // Amber Gold
  '#dc2626', // Crimson Red
  '#7c3aed', // Indigo Violet
  '#0284c7', // Ocean Sky
];

export const SEMANTIC_COLORS = {
  confirmed: '#059669',
  missing: '#d97706',
  conflicting: '#dc2626',
  primary: '#2563eb',
  secondary: '#7c3aed',
  neutral: '#0284c7',
} as const;

function readCssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback;
  const val = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return val || fallback;
}

/**
 * Builds an AG Charts Community theme by reading the active app CSS variables
 * so theme toggles update background, axes, gridlines, font, and tooltips without page reload.
 */
export function buildPayResolveChartTheme(isDark: boolean): AgChartTheme {
  ensureAgChartsRegistered();

  const backgroundColor = readCssVar('--bg-surface', isDark ? '#0e1522' : '#ffffff');
  const textPrimary = readCssVar('--text-primary', isDark ? '#f1f5f9' : '#0f172a');
  const textSecondary = readCssVar('--text-secondary', isDark ? '#cbd5e1' : '#475569');
  const gridLineColor = readCssVar('--chart-grid-line', isDark ? '#1e293b' : '#e2e8f0');

  return {
    baseTheme: isDark ? 'ag-default-dark' : 'ag-default',
    palette: {
      fills: PAYRESOLVE_CHART_PALETTE,
      strokes: PAYRESOLVE_CHART_PALETTE,
    },
    overrides: {
      common: {
        background: {
          fill: backgroundColor,
        },
        padding: {
          top: 12,
          right: 16,
          bottom: 12,
          left: 16,
        },
        legend: {
          item: {
            label: {
              fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
              fontSize: 12,
              color: textSecondary,
            },
            marker: {
              size: 10,
            },
          },
        },
        axes: {
          category: {
            line: {
              stroke: gridLineColor,
            },
            title: {
              fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
              fontSize: 12,
              fontWeight: 'bold',
              color: textPrimary,
            },
            label: {
              fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
              fontSize: 11,
              color: textSecondary,
            },
            gridLine: {
              style: [{ stroke: gridLineColor, lineDash: [2, 2] }],
            },
          },
          number: {
            line: {
              stroke: gridLineColor,
            },
            title: {
              fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
              fontSize: 12,
              fontWeight: 'bold',
              color: textPrimary,
            },
            label: {
              fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
              fontSize: 11,
              color: textSecondary,
            },
            gridLine: {
              style: [{ stroke: gridLineColor, lineDash: [3, 3] }],
            },
          },
        },
      },
      bar: {
        series: {
          label: {
            fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
            fontSize: 11,
            fontWeight: 'bold',
            color: '#ffffff',
          },
        },
      },
      donut: {
        series: {
          calloutLabel: {
            fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
            fontSize: 11,
            color: textSecondary,
          },
          sectorLabel: {
            fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
            fontSize: 11,
            fontWeight: 'bold',
            color: '#ffffff',
          },
        },
      },
    },
  };
}

export function useChartTheme(): { chartTheme: AgChartTheme; isDark: boolean } {
  const { isDark } = useTheme();
  const chartTheme = useMemo(() => buildPayResolveChartTheme(isDark), [isDark]);
  return { chartTheme, isDark };
}
