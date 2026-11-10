import { useMemo } from 'react';
import { AgCharts } from 'ag-charts-react';
import type { AgChartOptions } from 'ag-charts-community';
import { PAYRESOLVE_CHART_PALETTE, useChartTheme } from '../../lib/chartTheme';
import { ChartCard, type AccessibleTableColumn } from './ChartCard';

export interface DonutChartCardProps {
  title: string;
  description: string;
  data: Array<Record<string, string | number>>;
  angleKey: string;
  calloutLabelKey: string;
  fills?: string[];
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  emptyMessage?: string;
  lastUpdated?: string | null;
  heightClassName?: string;
}

export function DonutChartCard({
  title,
  description,
  data,
  angleKey,
  calloutLabelKey,
  fills,
  isLoading = false,
  isError = false,
  errorMessage,
  emptyMessage,
  lastUpdated,
  heightClassName = 'h-64',
}: DonutChartCardProps) {
  const { chartTheme } = useChartTheme();

  const totalCount = useMemo(
    () => data.reduce((acc, row) => acc + Number(row[angleKey] ?? 0), 0),
    [angleKey, data]
  );

  const options = useMemo<AgChartOptions>(() => {
    return {
      theme: chartTheme,
      data,
      series: [
        {
          type: 'donut',
          angleKey,
          calloutLabelKey,
          sectorLabelKey: angleKey,
          innerRadiusRatio: 0.58,
          fills: fills ?? PAYRESOLVE_CHART_PALETTE,
          calloutLabel: {
            enabled: true,
          },
          sectorLabel: {
            enabled: true,
            formatter: ({ datum }: { datum?: Record<string, string | number> }) => {
              const val = Number(datum?.[angleKey] ?? 0);
              return val > 0 ? String(val) : '';
            },
          },
          innerLabels: [
            {
              text: String(totalCount),
              fontSize: 20,
              fontWeight: 'bold',
            },
            {
              text: 'Total',
              fontSize: 11,
            },
          ],
        },
      ],
      legend: {
        enabled: true,
        position: 'bottom',
      },
    };
  }, [angleKey, calloutLabelKey, chartTheme, data, fills, totalCount]);

  const accessibleColumns: AccessibleTableColumn[] = useMemo(
    () => [
      { key: calloutLabelKey, label: 'Category' },
      { key: angleKey, label: 'Count' },
    ],
    [angleKey, calloutLabelKey]
  );

  return (
    <ChartCard
      title={title}
      description={description}
      isLoading={isLoading}
      isError={isError}
      errorMessage={errorMessage}
      isEmpty={!isLoading && !isError && totalCount === 0}
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
