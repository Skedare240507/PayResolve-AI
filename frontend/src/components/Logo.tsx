import React from 'react';
import { logoSizes, type LogoSizeKey } from '../lib/logoSizes';

export interface LogoProps {
  variant?: 'lockup' | 'symbol';
  size?: number | LogoSizeKey;
  theme?: 'auto' | 'light' | 'dark';
  className?: string;
  alt?: string;
}

const LOCKUP_ASPECT = 4162 / 1200;
const SYMBOL_ASPECT = 1309 / 1200;

export const Logo: React.FC<LogoProps> = ({
  variant = 'lockup',
  size = 'sidebar',
  theme = 'auto',
  className = '',
  alt = 'PayResolve AI',
}) => {
  const heightPx = typeof size === 'number' ? size : logoSizes[size];
  const widthPx = Math.round(heightPx * (variant === 'lockup' ? LOCKUP_ASPECT : SYMBOL_ASPECT));

  const lightSrc =
    variant === 'lockup' ? '/brand/logo-horizontal.svg' : '/brand/logo-symbol.svg';
  const darkSrc =
    variant === 'lockup' ? '/brand/logo-reversed.svg' : '/brand/logo-symbol-light-check.svg';

  const imgStyle: React.CSSProperties = {
    height: `${heightPx}px`,
    width: 'auto',
    maxWidth: '100%',
  };

  if (theme === 'light') {
    return (
      <span className={`inline-flex items-center shrink-0 ${className}`}>
        <img
          src={lightSrc}
          alt={alt}
          width={widthPx}
          height={heightPx}
          style={imgStyle}
          className="block object-contain select-none"
          loading="eager"
          decoding="async"
        />
      </span>
    );
  }

  if (theme === 'dark') {
    return (
      <span className={`inline-flex items-center shrink-0 ${className}`}>
        <img
          src={darkSrc}
          alt={alt}
          width={widthPx}
          height={heightPx}
          style={imgStyle}
          className="block object-contain select-none"
          loading="eager"
          decoding="async"
        />
      </span>
    );
  }

  return (
    <span className={`inline-flex items-center shrink-0 ${className}`}>
      <img
        src={lightSrc}
        alt={alt}
        width={widthPx}
        height={heightPx}
        style={imgStyle}
        className="block dark:hidden object-contain select-none"
        loading="eager"
        decoding="async"
      />
      <img
        src={darkSrc}
        alt={alt}
        width={widthPx}
        height={heightPx}
        style={imgStyle}
        className="hidden dark:block object-contain select-none"
        loading="eager"
        decoding="async"
      />
    </span>
  );
};

