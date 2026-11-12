import React from 'react';
import { Logo } from './Logo';

/**
 * PayResolveMark — the brand icon mark for compact spaces.
 */
export const PayResolveMark: React.FC<{
  className?: string;
  size?: number;
  spinning?: boolean;
}> = ({ className = '', size = 32, spinning = false }) => {
  return (
    <span
      className={`inline-flex items-center justify-center shrink-0 ${spinning ? 'animate-spin' : ''} ${className}`}
      role="img"
      aria-label="PayResolve mark"
    >
      <Logo variant="symbol" size={size} />
    </span>
  );
};

/**
 * PayResolveLogo — official brand logo component.
 * Delegates to <Logo /> using theme-correct SVG assets under /brand/.
 */
export const PayResolveLogo: React.FC<{
  className?: string;
  markSize?: number;
  height?: number | string;
  textColor?: string;
  showText?: boolean;
  alt?: string;
  theme?: 'auto' | 'light' | 'dark';
}> = ({
  className = '',
  markSize,
  height,
  showText = true,
  alt = 'PayResolve AI',
  theme = 'auto',
}) => {
  const numericHeight =
    typeof height === 'number'
      ? height
      : typeof height === 'string'
      ? parseInt(height, 10) || 32
      : markSize ?? 32;

  return (
    <Logo
      variant={showText && !markSize ? 'lockup' : markSize && !height ? 'symbol' : 'lockup'}
      size={numericHeight}
      theme={theme}
      className={className}
      alt={alt}
    />
  );
};
