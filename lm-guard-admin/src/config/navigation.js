import {
  LayoutDashboard,
  ClipboardList,
  AlertOctagon,
  Radar,
  Package,
  History,
  Scale,
  BarChart3,
  Settings,
  UsersRound,
} from 'lucide-react';

/**
 * Single source of truth for navigation and breadcrumbs.
 * Order matches the product specification exactly; the group labels only
 * separate operational work from intelligence and governance.
 */
export const NAV_GROUPS = [
  {
    label: 'Operations',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, crumb: 'Overview' },
      { to: '/inspections', label: 'Inspections', icon: ClipboardList, crumb: 'All records' },
      { to: '/violations', label: 'Violations', icon: AlertOctagon, crumb: 'Case queue', badgeKey: 'openViolations' },
      { to: '/inspectors', label: 'Inspector Management', icon: UsersRound, crumb: 'Directory' },
    ],
  },
  {
    label: 'Intelligence',
    items: [
      { to: '/risk', label: 'Risk Intelligence', icon: Radar, crumb: 'Priority queue' },
      { to: '/products', label: 'Products', icon: Package, crumb: 'Registry' },
      { to: '/product-history', label: 'Product History', icon: History, crumb: 'Change ledger' },
    ],
  },
  {
    label: 'Governance',
    items: [
      { to: '/rules', label: 'Rules & Compliance', icon: Scale, crumb: 'Rule registry' },
      { to: '/analytics', label: 'Analytics', icon: BarChart3, crumb: 'Programme insights' },
    ],
  },
];

export const SETTINGS_ITEM = { to: '/settings', label: 'Settings', icon: Settings, crumb: 'Preferences' };

export const NAV_ITEMS = [...NAV_GROUPS.flatMap((g) => g.items), SETTINGS_ITEM];

/** Route-segment titles used to build the header breadcrumb. */
const ROUTE_TITLES = {
  dashboard: 'Dashboard',
  inspections: 'Inspections',
  violations: 'Violations',
  inspectors: 'Inspector Management',
  risk: 'Risk Intelligence',
  products: 'Products',
  'product-history': 'Product History',
  rules: 'Rules & Compliance',
  analytics: 'Analytics',
  settings: 'Settings',
  'new-inspection': 'New Inspection',
  analysis: 'Analysis',
  evidence: 'Evidence',
};

/**
 * Builds a breadcrumb trail from a pathname.
 * "/violations/VIO-2026-014" → [Violations, VIO-2026-014]
 */
export function breadcrumbsFor(pathname) {
  const segments = pathname.split('/').filter(Boolean);
  if (!segments.length) return [{ label: 'Dashboard', to: '/dashboard' }, { label: 'Overview' }];

  const trail = [];
  const root = segments[0];
  const nav = NAV_ITEMS.find((i) => i.to === `/${root}`);
  trail.push({ label: ROUTE_TITLES[root] || nav?.label || root, to: `/${root}` });

  if (segments.length === 1) {
    trail.push({ label: nav?.crumb || 'Overview' });
  } else {
    segments.slice(1).forEach((seg, idx) => {
      const isLast = idx === segments.length - 2;
      trail.push({
        label: ROUTE_TITLES[seg] || seg,
        to: isLast ? undefined : `/${segments.slice(0, idx + 2).join('/')}`,
      });
    });
  }
  return trail;
}
