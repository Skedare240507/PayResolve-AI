import { useMemo } from 'react';
import { AgCharts } from 'ag-charts-react';
import type { AgAreaSeriesOptions, AgChartOptions, AgLineSeriesOptions } from 'ag-charts-community';
import { PAYRESOLVE_CHART_PALETTE, useChartTheme } from '../../lib/chartTheme';
import { ChartCard, type AccessibleTableColumn } from './ChartCard';
import type { MetricId } from '../../lib/metricDefinitions';
import type { MetricValues } from '../InfoTip';

export interface LineSeriesSpec {
  yKey: string;
  yName: string;
  type?: 'line' | 'area';
  stroke?: string;
  fill?: string;
  markerShape?: 'circle' | 'square' | 'diamond' | 'triangle';
  lineDash?: number[];
}

export interface LineAreaChartCardProps {
  title: string;
  description: string;
  metricId?: MetricId;
  metricValues?: MetricValues;
  data: Array<Record<string, string | number>>;
  xKey: string;
  xLabel?: string;
  series: LineSeriesSpec[];
  yAxisTitle?: string;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  emptyMessage?: string;
  lastUpdated?: string | null;
  heightClassName?: string;
}

export function LineAreaChartCard({
  title,
  description,
  metricId,
  metricValues,
  data,
  xKey,
  xLabel = 'Time',
  series,
  yAxisTitle,
  isLoading = false,
  isError = false,
  errorMessage,
  emptyMessage,
  lastUpdated,
  heightClassName = 'h-64',
}: LineAreaChartCardProps) {
  const { chartTheme } = useChartTheme();

  const options = useMemo<AgChartOptions>(() => {
    const builtSeries: Array<AgLineSeriesOptions | AgAreaSeriesOptions> = series.map((s, idx) => {
      const stroke = s.stroke ?? PAYRESOLVE_CHART_PALETTE[idx % PAYRESOLVE_CHART_PALETTE.length];
      const base = {
        xKey,
        yKey: s.yKey,
        yName: s.yName,
        stroke,
        strokeWidth: 2.5,
        lineDash: s.lineDash,
        marker: {
          enabled: true,
          shape: s.markerShape ?? (idx === 0 ? 'circle' : 'diamond'),
          size: 7,
          fill: stroke,
        },
      };

      if (s.type === 'area') {
        return {
          ...base,
          type: 'area',
          fill: s.fill ?? stroke,
          fillOpacity: 0.16,
        } satisfies AgAreaSeriesOptions;
      }

      return {
        ...base,
        type: 'line',
      } satisfies AgLineSeriesOptions;
    });

    return {
      theme: chartTheme,
      data,
      series: builtSeries,
      legend: {
        enabled: true,
        position: 'bottom',
      },
      axes: {
        x: { type: 'category' },
        y: {
          type: 'number',
          title: yAxisTitle ? { text: yAxisTitle } : undefined,
        },
      },
    };
  }, [chartTheme, data, series, xKey, yAxisTitle]);

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
