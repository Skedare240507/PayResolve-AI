import React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface EvidenceNodeData {
  label: string;
  sublabel?: string;
  source?: string;
  status: string;
  amount?: string;
  isMissing?: boolean;
}

export const EvidenceNode: React.FC<NodeProps> = ({ data }) => {
  const nodeData = data as unknown as EvidenceNodeData;
  const isMissing = nodeData.isMissing || nodeData.status === 'MISSING';

  return (
    <div
      className={`px-4 py-2.5 rounded-xl shadow-xs transition-all select-none min-w-[130px] ${
        isMissing
          ? 'border-2 border-dashed border-amber-500/80 bg-amber-50/70 dark:bg-amber-950/30 text-amber-900 dark:text-amber-200'
          : nodeData.status === 'COMPLETED'
          ? 'border-2 border-blue-500 bg-white dark:bg-[#131b26] text-slate-900 dark:text-white'
          : 'border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#131b26] text-slate-800 dark:text-slate-100'
      }`}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!bg-slate-400 !w-2 !h-2 !border-none"
      />

      <div className="flex items-center justify-between gap-2 mb-0.5">
        <span className="text-[11px] font-bold tracking-tight">
          {nodeData.label}
        </span>
        {nodeData.source && (
          <span className="text-[9px] px-1.5 py-0.2 rounded bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 uppercase font-mono">
            {nodeData.source}
          </span>
        )}
      </div>

      <div className="flex items-center justify-between gap-2 text-[10px]">
        <span className={isMissing ? 'text-amber-600 dark:text-amber-400 font-medium' : 'text-slate-500 dark:text-slate-400'}>
          {nodeData.sublabel || nodeData.status}
        </span>
        {nodeData.amount && (
          <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
            {nodeData.amount}
          </span>
        )}
      </div>

      <Handle
        type="source"
        position={Position.Right}
        className="!bg-slate-400 !w-2 !h-2 !border-none"
      />
    </div>
  );
};
