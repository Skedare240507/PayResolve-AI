import React from 'react';
import logoLight from '../assets/edited-photo (1).png';
import logoDark from '../assets/edited-photo.png';

/**
 * PayResolveMark — the brand icon mark for compact spaces.
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
          <linearGradient id="prBodyGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#1e6fff" />
            <stop offset="45%" stopColor="#2563eb" />
            <stop offset="100%" stopColor="#1a4fc9" />
          </linearGradient>
          <linearGradient id="prHiGrad" x1="0" y1="0" x2="1" y2="0.6">
            <stop offset="0%" stopColor="#60c8ff" stopOpacity="0.95" />
            <stop offset="60%" stopColor="#3ba0ff" stopOpacity="0.50" />
            <stop offset="100%" stopColor="#2563eb" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="prShGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#1240a8" />
            <stop offset="100%" stopColor="#0d2f7a" />
          </linearGradient>
          <radialGradient id="prBubble" cx="50%" cy="40%" r="60%">
            <stop offset="0%" stopColor="#ffffff" />
            <stop offset="100%" stopColor="#e8f1ff" />
          </radialGradient>
          <filter id="prShadow" x="-10%" y="-5%" width="130%" height="130%">
            <feDropShadow dx="0" dy="3" stdDeviation="4" floodColor="#1a3f9f" floodOpacity="0.35" />
          </filter>
        </defs>

        <g filter="url(#prShadow)">
          <path
            d="M 28 95 C 15 90, 10 78, 14 66 C 18 55, 24 50, 30 46 L 30 28 C 30 16, 38 8, 56 8 C 76 8, 92 20, 92 42 C 92 62, 76 74, 56 74 L 46 74 L 46 85 C 46 94, 40 102, 32 102 C 30 102, 28 100, 28 95 Z"
            fill="url(#prBodyGrad)"
          />
          <path d="M 32 20 C 38 10, 52 7, 62 10 C 52 10, 42 15, 36 26 Z" fill="url(#prHiGrad)" opacity="0.85" />
          <path d="M 68 10 C 84 16, 93 28, 92 42 C 92 32, 86 20, 72 13 Z" fill="#60c8ff" opacity="0.45" />
          <path d="M 28 72 C 18 74, 12 80, 14 88 C 16 96, 22 100, 28 95 C 26 90, 24 83, 28 72 Z" fill="url(#prShGrad)" opacity="0.6" />
          <ellipse cx="62" cy="41" rx="22" ry="18" fill="url(#prBubble)" />
          <path d="M 51 41 L 59 50 L 74 32" stroke="#2563eb" strokeWidth="5.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </g>
      </svg>
    </div>
  );
};

/**
 * PayResolveLogo — official brand logo component.
 * - Light mode: uses "edited-photo (1).png"
 * - Dark mode:  uses "edited-photo.png"
 * Automatically switches with active theme, maintains aspect ratio,
 * provides proper alt text and never overflows on mobile.
 */
export const PayResolveLogo: React.FC<{
  className?: string;
  markSize?: number;
  height?: number | string;
  textColor?: string;
  showText?: boolean;
  alt?: string;
}> = ({
  className = '',
  markSize,
  height,
  alt = 'PayResolve AI',
}) => {
  const computedHeight = height ?? markSize ?? 32;
  const heightStyle = typeof computedHeight === 'number' ? `${computedHeight}px` : computedHeight;

  return (
    <div className={`inline-flex items-center shrink-0 ${className}`}>
      {/* Light mode: edited photo(1) */}
      <img
        src={logoLight}
        alt={alt}
        className="block dark:hidden object-contain max-w-full transition-opacity duration-150"
        style={{ height: heightStyle, maxHeight: heightStyle }}
        loading="eager"
      />
      {/* Dark mode: edited photo */}
      <img
        src={logoDark}
        alt={alt}
        className="hidden dark:block object-contain max-w-full transition-opacity duration-150"
        style={{ height: heightStyle, maxHeight: heightStyle }}
        loading="eager"
      />
    </div>
  );
};
