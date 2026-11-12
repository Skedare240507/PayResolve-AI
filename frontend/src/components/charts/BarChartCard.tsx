import { useMemo } from 'react';
import { AgCharts } from 'ag-charts-react';
import type { AgBarSeriesOptions, AgChartOptions } from 'ag-charts-community';
import { PAYRESOLVE_CHART_PALETTE, useChartTheme } from '../../lib/chartTheme';
import { ChartCard, type AccessibleTableColumn } from './ChartCard';
import type { MetricId } from '../../lib/metricDefinitions';
import type { MetricValues } from '../InfoTip';

export interface BarSeriesSpec {
  yKey: string;
  yName: string;
  fill?: string;
  stacked?: boolean;
}

export interface BarChartCardProps {
  title: string;
  description: string;
  metricId?: MetricId;
  metricValues?: MetricValues;
  data: Array<Record<string, string | number>>;
  xKey: string;
  xLabel?: string;
  series: BarSeriesSpec[];
  direction?: 'vertical' | 'horizontal';
  yAxisTitle?: string;
  valueSuffix?: string;
  showLabels?: boolean;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  emptyMessage?: string;
  lastUpdated?: string | null;
  heightClassName?: string;
}

export function BarChartCard({
  title,
  description,
  metricId,
  metricValues,
  data,
  xKey,
  xLabel = 'Category',
  series,
  direction = 'vertical',
  yAxisTitle,
  valueSuffix = '',
  showLabels = true,
  isLoading = false,
  isError = false,
  errorMessage,
  emptyMessage,
  lastUpdated,
  heightClassName = 'h-64',
}: BarChartCardProps) {
  const { chartTheme } = useChartTheme();

  const options = useMemo<AgChartOptions>(() => {
    const barSeries: AgBarSeriesOptions[] = series.map((s, idx) => ({
      type: 'bar',
      direction,
      xKey,
      yKey: s.yKey,
      yName: s.yName,
      fill: s.fill ?? PAYRESOLVE_CHART_PALETTE[idx % PAYRESOLVE_CHART_PALETTE.length],
      stacked: s.stacked ?? false,
      cornerRadius: s.stacked ? 2 : 4,
      label: {
        enabled: showLabels,
        formatter: ({ value }: { value?: number }) =>
          value == null ? '' : `${value}${valueSuffix}`,
      },
      tooltip: {
        renderer: (params: { datum?: Record<string, unknown> }) => {
          const datum = params.datum || {};
          const fullToolName = String(datum.toolName || datum[xKey] || '');
          const val = datum[s.yKey];
          return {
            title: fullToolName,
            content: `${s.yName}: ${val != null ? val : 0}${valueSuffix}`,
          };
        },
      },
    }));

    return {
      theme: chartTheme,
      data,
      series: barSeries,
      padding: {
        bottom: 24,
        left: 12,
        right: 12,
        top: 12,
      },
      legend: {
        enabled: series.length > 1,
        position: 'bottom',
      },
      axes:
        direction === 'horizontal'
          ? {
              y: { type: 'category' },
              x: {
                type: 'number',
                title: yAxisTitle ? { text: yAxisTitle } : undefined,
              },
            }
          : {
              x: {
                type: 'category',
                label: {
                  autoRotate: true,
                  rotation: 25,
                },
              },
              y: {
                type: 'number',
                title: yAxisTitle ? { text: yAxisTitle } : undefined,
              },
            },
    };
  }, [chartTheme, data, direction, series, showLabels, valueSuffix, xKey, yAxisTitle]);

  const accessibleColumns: AccessibleTableColumn[] = useMemo(
    () => [
      { key: xKey, label: xLabel },
      ...series.map((s) => ({ key: s.yKey, label: s.yName })),
    ],
    [series, xKey, xLabel]
  );

  return (
    <ChartCard
      title={title}
      description={description}
      metricId={metricId}
      metricValues={metricValues}
      isLoading={isLoading}
      isError={isError}
      errorMessage={errorMessage}
      isEmpty={!isLoading && !isError && data.length === 0}
      emptyMessage={emptyMessage}
      lastUpdated={lastUpdated}
      heightClassName={heightClassName}
      accessibleColumns={accessibleColumns}
      accessibleRows={data}
    >
      <AgCharts options={options} style={{ width: '100%', height: '100%' }} />
    </ChartCard>
  );
}
