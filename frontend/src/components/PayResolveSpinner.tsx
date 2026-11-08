import React from 'react';
import { PayResolveMark } from './PayResolveLogo';

interface PayResolveSpinnerProps {
  size?: number;
  label?: string;
  sublabel?: string;
  className?: string;
}

export const PayResolveSpinner: React.FC<PayResolveSpinnerProps> = ({
  size = 44,
  label,
  sublabel,
  className = '',
}) => {
  return (
    <div
      role="status"
      aria-live="polite"
      className={`flex flex-col items-center justify-center p-8 space-y-4 ${className}`}
    >
      <div className="relative flex items-center justify-center">
        {/* Animated breathing pulse ring */}
        <div
          className="absolute inset-0 rounded-2xl bg-blue-500/20 dark:bg-blue-400/20 blur-md motion-safe:animate-pulse opacity-70"
          style={{ width: size * 1.25, height: size * 1.25, top: -size * 0.12, left: -size * 0.12 }}
        />
        <PayResolveMark size={size} spinning={true} />
      </div>

      {label && (
        <div className="text-center space-y-1">
          <p className="text-sm font-semibold text-slate-800 dark:text-slate-200">
            {label}
          </p>
          {sublabel && (
            <p className="text-xs text-slate-500 dark:text-slate-400 font-mono">
              {sublabel}
            </p>
          )}
        </div>
      )}
    </div>
  );
};
