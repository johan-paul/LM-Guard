# LM-GUARD — Frontend

AI-assisted, evidence-first Legal Metrology inspection console.

**Package → AI analysis → Structured facts → Rule validation → Evidence → Human review → Enforcement intelligence**

> AI observes. Rules validate. Humans decide.

React 18 · Vite 5 · Tailwind CSS 3 · React Router 6 · Recharts · Lucide

```bash
npm install
npm run dev      # http://localhost:5173
npm run build
```

Demonstration sign-in: `admin / admin123` (System Administrator) · `inspector / inspect123` (Inspecting Officer).

---

## Design system

Defined once in `tailwind.config.js` and `src/index.css`; no page hard-codes a colour.

| Token | Value | Used for |
| --- | --- | --- |
| `navy-900` | `#0B1F33` | Navigation rail, evidence stage, masthead |
| `navy-800` | `#16324F` | Secondary navy surfaces, chart series |
| `brand-600` | `#2563EB` | Primary actions, active nav, primary data series |
| `canvas` | `#F6F8FB` | Application background |
| `surface` | `#FFFFFF` | Panels and cards |
| `ink` / `ink-500` | `#172033` / `#667085` | Primary / secondary text |
| `line` | `#E4E7EC` | Borders and dividers |
| `success-600` `warning-600` `danger-600` `critical-600` | `#16A34A` `#D97706` `#DC2626` `#B42318` | Compliance and risk semantics only |

Typography is Inter (JetBrains Mono for identifiers), with a fixed scale: page title 30px semibold, section 18px, KPI figure 28px bold, body 13–15px, micro labels 11px uppercase with 0.06em tracking. Figures use tabular numerals so columns align.

Component classes (`.panel`, `.btn-primary`, `.input`, `.badge`, `.data-table`, `.nav-item`, …) live in `src/index.css` under `@layer components`. Radii stay at 6–10px, shadows are near-invisible (`shadow-card`), and motion is limited to fades, the sidebar slide and the evidence scan sweep.

## Architecture

```
src/
  config/navigation.js     Single source of truth for nav + breadcrumbs
  data/                    Mock data layer (no data in page components)
    assets.js              Generated SVG package labels + product thumbnails
    products.js            PRD-2026-0XX registry, pack-change history, listings
    inspections.js         INS-2026-0XX records, declarations, timelines
    violations.js          VIO-2026-0XX cases + evidence bounding boxes
    rules.js               LMPC 2026.1 demonstration ruleset
    riskData.js            Programme counters, risk queue, repeat offenders
    analytics.js           Deterministic (seeded) trend series and aggregates
  services/
    inspectionService.js   Async API surface over the data layer
    api.js                 Axios instance for the Spring Boot backend
    authData.js            Officer directory (prototype auth)
    pdfExport.js           Compliance report export
  hooks/useData.js         useResource + domain hooks, debounce, pagination
  layouts/AppLayout.jsx    Sidebar + sticky header + content shell
  components/              Reusable UI (see below)
  pages/                   One file per route
```

**Data flow.** Pages never import from `src/data` for their own records — they call `inspectionService` through the hooks in `useData.js`. When the Spring Boot API is ready, replace the method bodies in `inspectionService.js` with `api.js` calls; no page changes.

Evidence images are generated as inline SVG "principal display panels" (`data/assets.js`) in a fixed 1000×680 coordinate space, and every violation's bounding boxes are expressed in that same space — so a finding highlights the exact region of the panel it came from, with no external image host.

## Components

| Component | Purpose |
| --- | --- |
| `AppSidebar` | Fixed navy rail, grouped nav, ruleset status, officer block |
| `TopHeader` | Breadcrumb, global search, notifications, profile menu |
| `PageHeader` | Title / subtitle / actions block |
| `StatCard`, `MiniStat` | KPI tiles and in-panel figures |
| `StatusBadge` | One badge vocabulary for verdicts, case states and rule states |
| `RiskBadge`, `RiskScore`, `ConfidenceMeter` | Risk level, `92 / 100` meter, confidence readout |
| `DataTable`, `Pagination`, `PrimaryCell` | Investigation tables with responsive column dropping |
| `SearchFilterBar`, `SegmentedControl` | Filter strip above tables; range switches |
| `ChartCard`, `ChartTooltip`, `LegendItem`, `CHART` | Chart shell, tooltip and the restrained palette |
| `EvidenceViewer` | Zoom, pan, region isolation, labelled bounding boxes |
| `ComplianceItem` | Declaration row: status, detected value, confidence, rule |
| `Timeline`, `ChangeTimeline` | Processing timeline; product pack-change history |
| `Tabs`, `EmptyState`, `LoadingState` | Supporting primitives |

## Routes

| Path | Screen |
| --- | --- |
| `/dashboard` | Command centre: KPIs, compliance trend, risk donut, priority products, recent inspections |
| `/inspections` · `/inspections/:id` | Inspection register · structured compliance report |
| `/violations` · `/violations/:id` | Case queue · evidence-first case view with inspector decision |
| `/risk` | Risk overview, priority queue, repeat offenders, scoring model |
| `/products` · `/products/:id` | Registry · compliance profile (Overview, Inspections, Violations, Package Changes, Digital Comparison) |
| `/product-history` | Cross-product pack size and price change ledger |
| `/rules` | Rule registry with evaluation logic and citing cases |
| `/analytics` | Trends, violation categories, regional insight, risk distribution, repeat offenders |
| `/settings` | Officer profile, notifications, review thresholds, demo reset |
| `/new-inspection` → `/analysis` → `/inspections/:id` | Capture, pipeline, result |
| `/evidence/:id` | Full evidence review for an inspection |

Legacy paths still resolve: `/history` → `/inspections`, `/inspection/:id` → `/inspections/:id`.

## Verdict vocabulary

Records store the backend contract — `COMPLIANT`, `NON_COMPLIANT`, `REVIEW_REQUIRED`, `INCONCLUSIVE`. `StatusBadge` renders `NON_COMPLIANT` as **Violation** and `REVIEW_REQUIRED` as **Review** for inspectors, without altering the stored verdict.

## Demonstration ruleset

`data/rules.js` holds illustrative rules authored for this prototype. They are **not** an official or complete reproduction of any statutory instrument, and the Rules & Compliance screen states this on the page. Do not present them as enforceable regulation.

## Responsive behaviour

- **≥ 1280px** — full column set, two-column intelligence grids.
- **1024–1279px** — sidebar fixed, secondary table columns drop.
- **< 1024px** — sidebar becomes an off-canvas drawer with a scrim; grids stack; tables scroll horizontally within their panel.
