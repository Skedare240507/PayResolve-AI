import React from 'react';

export const PayResolveMark: React.FC<{
  className?: string;
  size?: number;
  spinning?: boolean;
}> = ({ className = '', size = 32, spinning = false }) => {
  return (
    <div
      className={`relative inline-flex items-center justify-center shrink-0 ${className}`}
      style={{ width: size, height: size }}
      role="img"
      aria-label="PayResolve Mark"
    >
      <svg
        width={size}
        height={size}
        viewBox="0 0 40 40"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className="overflow-visible"
      >
        <defs>
          <linearGradient id="prShieldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#0066cc" />
            <stop offset="100%" stopColor="#003366" />
          </linearGradient>

          <linearGradient id="prRingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#38bdf8" />
            <stop offset="50%" stopColor="#0066cc" />
            <stop offset="100%" stopColor="#1e40af" />
          </linearGradient>

          <filter id="prGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="1.5" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        {/* Deep blue background capsule */}
        <rect
          x="2"
          y="2"
          width="36"
          height="36"
          rx="10"
          fill="url(#prShieldGrad)"
          className="shadow-inner"
        />

        {/* Lighter blue ring with travelling sweep */}
        <circle
          cx="20"
          cy="20"
          r="13"
          stroke="url(#prRingGrad)"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeDasharray="28 54"
          className={spinning ? 'animate-spin-slow origin-center' : ''}
          filter="url(#prGlow)"
        />

        {/* Crisp checkmark that self-draws on load with stroke-dashoffset */}
        <path
          d="M14.5 20.5L18.5 24.5L25.5 16.5"
          stroke="#ffffff"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="animate-check-draw"
        />
      </svg>
    </div>
  );
};

export const PayResolveLogo: React.FC<{
  className?: string;
  markSize?: number;
  textColor?: string;
  showText?: boolean;
}> = ({
  className = '',
  markSize = 28,
  textColor = 'text-slate-900 dark:text-white',
  showText = true,
}) => {
  return (
    <div className={`flex items-center space-x-2.5 ${className}`}>
      <PayResolveMark size={markSize} />
      {showText && (
        <div className="flex items-center space-x-1.5">
          <span className={`font-bold tracking-tight text-base sm:text-lg ${textColor}`}>
            PayResolve
          </span>
          <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-[#0066cc] text-white tracking-wide uppercase">
            AI
          </span>
        </div>
      )}
    </div>
  );
};
