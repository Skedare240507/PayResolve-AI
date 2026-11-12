# PayResolve AI Brand Asset System

## SVG Assets (`frontend/public/brand/`)

All brand marks are vector SVGs optimized with `svgo --multipass` and served directly from `/brand/`:

| File | Variant | Usage |
| :--- | :--- | :--- |
| `logo-horizontal.svg` | Horizontal lockup (light mode) | Light backgrounds (`#ffffff`, `#f8fafc`) |
| `logo-reversed.svg` | Horizontal lockup (dark mode) | Dark backgrounds (`#0a0f1d`, `#080c14`) and the dark-blue auth gradient |
| `logo-symbol.svg` | Standalone symbol (light mode) | Compact headers, favicons, mobile bar on light backgrounds |
| `logo-symbol-light-check.svg` | Standalone symbol (dark mode, `#ffffff` checkmark) | Compact headers and spinners on dark backgrounds |

## Favicons, PWA & Social Assets

- `frontend/public/favicon.svg`: Vector symbol favicon
- `frontend/public/favicon-16x16.png` & `favicon-32x32.png`: Standard browser tab icons
- `frontend/public/apple-touch-icon.png` (180x180): iOS home screen icon
- `frontend/public/manifest.webmanifest` + `/brand/icon-192.png`, `/brand/icon-512.png`: Web app manifest
- `frontend/public/brand/og-image.png` (1200x630): OpenGraph & Twitter Card social preview (`og:image`, `twitter:image` in `frontend/index.html`)

## React Component & Size Tokens

- **Component:** `frontend/src/components/Logo.tsx`
  - `variant`: `'lockup' | 'symbol'` (default `'lockup'`)
  - `size`: `number | LogoSizeKey` (default `'sidebar'`)
  - `theme`: `'auto' | 'light' | 'dark'` (default `'auto'`, switches via Tailwind `dark:` classes)
- **Size tokens (`frontend/src/lib/logoSizes.ts`):**
  - `sidebar`: `32px`
  - `mobileHeader`: `28px`
  - `authHeader`: `32px`
  - `authHero`: `40px`
  - `spinner`: `44px`
  - `emptyState`: `40px`

## Contrast & Placement Rules

- Light surfaces use `logo-horizontal.svg` (`#04235c` wordmark on `#ffffff`).
- Dark surfaces (`dark:bg-[#0a0f1d]` sidebar, `dark:bg-[#0d131f]` mobile bar) use `logo-reversed.svg` (`#fbfcfc` wordmark) or `logo-symbol-light-check.svg`.
- The auth left panel gradient (`from-[#071f49] via-[#0b337c] to-[#0f4eb7]`) forces `theme="dark"` (`logo-reversed.svg`) inside a translucent pill (`bg-white/10 border-white/20 backdrop-blur-xs`) for crisp contrast.

