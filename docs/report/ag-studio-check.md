# AG Grid and AG Studio Audit Report

This report documents the verification, debugging, and audit of **AG Grid** and **AG Studio** integrations within PayResolve AI in accordance with PART 1 of the deployment specification.

---

## 1. Exception Queue & Package Audit

### Verification Results
| Package | Version | Purpose in PayResolve AI | License Required | Verified File |
| :--- | :--- | :--- | :--- | :--- |
| `ag-grid-community` | `~36.3.0` | Core grid engine for tabular exception triage | **No (Community)** | `frontend/src/pages/ExceptionQueuePage.tsx` |
| `ag-grid-react` | `~36.3.0` | React component wrapper for Exception Queue | **No (Community)** | `frontend/src/pages/ExceptionQueuePage.tsx` |
| `ag-studio` | `^1.1.0` | Executive operations dashboard engine (`/operations`) | **Optional Trial / Evaluation** | `frontend/src/pages/OperationsPage.tsx` |
| `ag-studio-react` | `^1.1.0` | React wrapper & widget portal bridge for AG Studio | **Optional Trial / Evaluation** | `frontend/src/pages/OperationsPage.tsx` |
| `ag-grid-enterprise` | `~36.3.0` | Transitive dependency of `ag-studio` | **Only if commercial license used** | `frontend/src/pages/OperationsPage.tsx` (only) |

### Key Findings
1. **Zero Enterprise Grid Imports in Exception Queue:**
   - [ExceptionQueuePage.tsx](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/PayResolve-AI/frontend/src/pages/ExceptionQueuePage.tsx) imports only `ModuleRegistry`, `AllCommunityModule`, and `AgGridReact` from `ag-grid-community` and `ag-grid-react`.
   - The queue requires no license key and produces zero watermark or license errors.
2. **Enterprise License Isolation:**
   - `ag-grid-enterprise` is imported **only** in `OperationsPage.tsx` (`LicenseManager.setLicenseKey(agLicense)`), where it is passed the trial license key from `VITE_AG_LICENSE` when provided.
   - No feature in the primary operator workflow depends on an enterprise grid license.

---

## 2. Operations Page Debugging & Root-Cause Analysis

### Previous State: Empty Dashboard ("No filters available")
Prior to this audit, navigating to `/operations` displayed an empty AG Studio workspace showing only a sidebar with *"No filters available"* and no custom widgets on the canvas.

### Root-Cause Analysis (Discovered via Browser Instrumentation)
Through automated Playwright test traces (`scripts/test-operations.mjs`), two fatal errors were identified during AG Studio canvas initialization:

1. **Fatal Schema Discovery Error (`validationEmptyFieldset`):**
   ```text
   AG Studio - Fatal error:
   Source 'cases' has no fields.
   Source 'events' has no fields.
   Source 'benchmark' has no fields.
   ```
   *Cause:* During initial component mount, the TanStack queries for `/api/cases`, `/api/events`, and `/api/benchmark` are in flight, so `casesList`, `eventsList`, and `benchmarkData` evaluate to empty arrays (`[]`). AG Studio infers schema fields dynamically by inspecting input rows; when passed 0 rows, it flags empty fieldsets and halts layout hydration.

2. **Fatal Data Mapping Type Error (`hydrateDataMapping`):**
   ```text
   TypeError: (dataMappingState[id2] ?? []).map is not a function
       at WidgetResolver.hydrateDataMapping
       at WidgetService.hydrateWidget
       at WidgetService.applyWidgets
   ```
   *Cause:* In `initialState`, the built-in `grid-widget` had `dataMapping: { source: 'cases' }`. AG Studio's `hydrateDataMapping` expects each mapping slot to be an array of field descriptors (e.g. `{ cols: [] }`). Passing a string (`'cases'`) caused `('cases').map` to throw an uncaught exception, crashing layout application and leaving the canvas blank.

3. **Missing License Visibility:**
   When `VITE_AG_LICENSE` was empty, AG Studio outputted trial watermark notices to the browser console, but the UI provided no context to operators about whether the dashboard was running in evaluation mode.

---

## 3. Implemented Fixes

1. **Schema Discovery Fallbacks (`OperationsPage.tsx`):**
   - Added schema seed rows to `studioData.sources` for `cases`, `events`, and `benchmark` so that AG Studio immediately discovers entity field definitions even before asynchronous queries resolve.
2. **Corrected Widget Layout & Data Mapping:**
   - Removed invalid `dataMapping: { source: 'cases' }`.
   - Custom widgets are configured with valid empty mappings (`dataMapping: {}`) since they ingest live telemetry directly through their typed `context` prop (`OperationsContext`).
   - Adjusted `defaultSize` and `minSize` to pixel values (`{ width: 340, height: 480 }`) matching AG Studio's pixel-based track sizing algorithm.
   - Assigned responsive 24-column tracks (`xSpan: 8`, `ySpan: 24` for each of the 3 custom widgets).
3. **Graceful Evaluation Notice Banner:**
   - Added an amber informational banner at the top of the Operations workspace when `VITE_AG_LICENSE` is not set:
     > **AG Studio Evaluation Mode:** `VITE_AG_LICENSE` is not set in `frontend/.env`. AG Studio core features and custom widgets run in unrestricted trial mode with evaluation watermark. Exception Queue uses Community AG Grid (no license required).
4. **Theme Synchronization & High Contrast Styling:**
   - Synchronized AG Studio with PayResolve's light and dark modes.
   - Updated widget card backgrounds in `OperationsWidgets.tsx` to `bg-white` (light) and `dark:bg-[#131b26]` with `dark:border-slate-700/80` (dark) for clear, crisp contrast against the `#0b101a` canvas background.

---

## 4. Verification Evidence

### Three Custom AG Studio Widgets Tested
| Widget Name | Widget ID | Real Telemetry Endpoint | Metrics Displayed | Verified Light | Verified Dark |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Cases by Type** | `cases-by-type` | `GET /api/cases` | Anomaly distribution across 5 categories, percentages, severity pills | Yes | Yes |
| **Time to Root Cause** | `time-to-cause` | `GET /api/benchmark` | 1.2s median AI latency vs. 45 min manual merchant triage (2,250x speedup), grounding rate | Yes | Yes |
| **Evidence Coverage** | `evidence-coverage` | `GET /api/cases` & graph | 84% confirmed link integrity, confirmed vs. missing breakdown | Yes | Yes |

### Additional Verified Features
- **Summary Stat Cards:** Display real API totals: 7 Total Exceptions, 1.2s Investigation Latency, 100.0% AI Grounding Rate, 100% Evidence Integrity.
- **Customize Layout Mode:** Clicking "Customize layout" switches AG Studio into `edit` mode, exposing the drag-and-drop toolboxes (`Cases`, `Events`, `Benchmark` sources and `PayResolve Operations` widget palette).
- **Operations Agent Assistant:** Clicking "Ask AI Agent" opens the assistant side panel for conversational telemetry queries.

### Visual Evidence Artifacts
- **Light Mode Screenshot:** `docs/report/screens/after/operations-light.png`
- **Dark Mode Screenshot:** `docs/report/screens/after/operations-dark.png`
- **Exception Queue Screenshot:** `docs/report/screens/after/queue-light.png`

---

## 5. Summary Conclusion
- **Part 1 Status:** **PASSED / COMPLETE**
- **Community License Guarantee:** Exception Queue is 100% community-licensed (`ag-grid-community` & `ag-grid-react`). Zero enterprise grid imports.
- **AG Studio Dashboard:** Fully functional with 3 real-time custom widgets in light and dark modes. Graceful evaluation notice active when no commercial license key is supplied.

