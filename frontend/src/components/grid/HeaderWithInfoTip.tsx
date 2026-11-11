import type { CustomHeaderProps } from 'ag-grid-react';
import { InfoTip } from '../InfoTip';
import type { MetricId } from '../../lib/metricDefinitions';

export interface HeaderWithInfoTipProps extends CustomHeaderProps {
  metricId?: MetricId;
}

export function HeaderWithInfoTip(props: HeaderWithInfoTipProps) {
  const metricId = props.metricId || (props.column.getColDef() as any).metricId;

  const onSortRequested = (event: React.MouseEvent) => {
    props.progressSort(event.shiftKey);
  };

  return (
    <div className="flex items-center justify-between w-full h-full min-w-0 pr-1">
      <div
        className="flex items-center gap-1.5 min-w-0 cursor-pointer select-none"
        onClick={onSortRequested}
      >
        <span className="ag-header-cell-text font-semibold text-xs truncate">
          {props.displayName}
        </span>
        {metricId && (
          <span onClick={(e) => e.stopPropagation()}>
            <InfoTip metricId={metricId} size={15} />
          </span>
        )}
      </div>

      {props.enableSorting && props.column.isSortAscending() && (
        <span className="text-[10px] text-blue-500 font-mono">▲</span>
      )}
      {props.enableSorting && props.column.isSortDescending() && (
        <span className="text-[10px] text-blue-500 font-mono">▼</span>
      )}
    </div>
  );
}

