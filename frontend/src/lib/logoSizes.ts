/**
 * Standardized pixel heights for PayResolve AI brand logo & symbol placements.
 */
export const logoSizes = {
  sidebar: 32,
  mobileHeader: 28,
  authHeader: 32,
  authHero: 40,
  spinner: 44,
  emptyState: 40,
} as const;

export type LogoSizeKey = keyof typeof logoSizes;

