import React from 'react';

/**
 * PayResolveMark — the stylised "P" icon with ribbon sweep and checkmark bubble.
 * Hand-crafted SVG matching the official PayResolve AI brand identity.
 */
export const PayResolveMark: React.FC<{
  className?: string;
  size?: number;
  spinning?: boolean;
}> = ({ className = '', size = 32 }) => {
  return (
    <div
      className={`relative inline-flex items-center justify-center shrink-0 ${className}`}
      style={{ width: size, height: size }}
      role="img"
      aria-label="PayResolve mark"
    >
      <svg
        width={size}
        height={size}
        viewBox="0 0 120 120"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          {/* Main body — deep→bright blue gradient (3-D ribbon depth) */}
          <linearGradient id="prBodyGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%"   stopColor="#1e6fff" />
            <stop offset="45%"  stopColor="#2563eb" />
            <stop offset="100%" stopColor="#1a4fc9" />
          </linearGradient>

          {/* Highlight streak on ribbon */}
          <linearGradient id="prHiGrad" x1="0" y1="0" x2="1" y2="0.6">
            <stop offset="0%"   stopColor="#60c8ff" stopOpacity="0.95" />
            <stop offset="60%"  stopColor="#3ba0ff" stopOpacity="0.50" />
            <stop offset="100%" stopColor="#2563eb" stopOpacity="0" />
          </linearGradient>

          {/* Shadow underside of tail */}
          <linearGradient id="prShGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%"   stopColor="#1240a8" />
            <stop offset="100%" stopColor="#0d2f7a" />
          </linearGradient>

          {/* White speech-bubble oval */}
          <radialGradient id="prBubble" cx="50%" cy="40%" r="60%">
            <stop offset="0%"   stopColor="#ffffff" />
            <stop offset="100%" stopColor="#e8f1ff" />
          </radialGradient>

          <filter id="prShadow" x="-10%" y="-5%" width="130%" height="130%">
            <feDropShadow dx="0" dy="3" stdDeviation="4" floodColor="#1a3f9f" floodOpacity="0.35" />
          </filter>
        </defs>

        <g filter="url(#prShadow)">
          {/* ── P body shape ── */}
          <path
            d="M 28 95
               C 15 90, 10 78, 14 66
               C 18 55, 24 50, 30 46
               L 30 28
               C 30 16, 38 8, 56 8
               C 76 8, 92 20, 92 42
               C 92 62, 76 74, 56 74
               L 46 74
               L 46 85
               C 46 94, 40 102, 32 102
               C 30 102, 28 100, 28 95 Z"
            fill="url(#prBodyGrad)"
          />

          {/* ── Highlight streak (top-left ribbon shine) ── */}
          <path
            d="M 32 20 C 38 10, 52 7, 62 10 C 52 10, 42 15, 36 26 Z"
            fill="url(#prHiGrad)"
            opacity="0.85"
          />

          {/* ── Bowl outer-edge highlight ── */}
          <path
            d="M 68 10 C 84 16, 93 28, 92 42 C 92 32, 86 20, 72 13 Z"
            fill="#60c8ff"
            opacity="0.45"
          />

          {/* ── Tail underside shadow ── */}
          <path
            d="M 28 72 C 18 74, 12 80, 14 88 C 16 96, 22 100, 28 95 C 26 90, 24 83, 28 72 Z"
            fill="url(#prShGrad)"
            opacity="0.6"
          />

          {/* ── White speech-bubble oval ── */}
          <ellipse cx="62" cy="41" rx="22" ry="18" fill="url(#prBubble)" />

          {/* ── Checkmark ── */}
          <path
            d="M 51 41 L 59 50 L 74 32"
            stroke="#2563eb"
            strokeWidth="5.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
          />
        </g>
      </svg>
    </div>
  );
};

/**
 * PayResolveLogo — full horizontal lockup: mark + "PayResolve" wordmark + "AI" badge.
 * Mirrors the official brand lockup from the PayResolve AI design system.
 */
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
          <span className="px-1.5 py-0.5 rounded-md text-[10px] font-bold bg-[#2563eb] text-white tracking-wide uppercase leading-none flex items-center">
            AI
          </span>
        </div>
      )}
    </div>
  );
};
