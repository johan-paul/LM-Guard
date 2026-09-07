/** Formatting helpers shared across the platform. */

const IST = 'en-IN';

export const nf = new Intl.NumberFormat(IST);

export const formatNumber = (n) => (n === null || n === undefined ? '—' : nf.format(n));

export const formatPercent = (n, digits = 1) =>
  n === null || n === undefined ? '—' : `${Number(n).toFixed(digits)}%`;

export const formatDelta = (n) => {
  if (n === null || n === undefined) return '—';
  const sign = n > 0 ? '+' : '';
  return `${sign}${Number(n).toFixed(1)}%`;
};

export const formatConfidence = (c) =>
  c === null || c === undefined ? '—' : `${Math.round(Number(c) * 100)}%`;

const startOfDay = (d) => {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
};

/** "Today, 10:42 AM" · "Yesterday, 4:20 PM" · "28 Aug 2026" */
export function formatDateTime(iso, { relativeDays = 2 } = {}) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';

  const days = Math.round((startOfDay(new Date()) - startOfDay(d)) / 86400000);
  const time = d.toLocaleTimeString(IST, { hour: 'numeric', minute: '2-digit', hour12: true }).toUpperCase();

  if (days === 0) return `Today, ${time}`;
  if (days === 1) return `Yesterday, ${time}`;
  if (days > 1 && days < relativeDays) return `${days} days ago`;
  return d.toLocaleDateString(IST, { day: '2-digit', month: 'short', year: 'numeric' });
}

/** "Today" · "Yesterday" · "28 Aug 2026" */
export function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const days = Math.round((startOfDay(new Date()) - startOfDay(d)) / 86400000);
  if (days === 0) return 'Today';
  if (days === 1) return 'Yesterday';
  return d.toLocaleDateString(IST, { day: '2-digit', month: 'short', year: 'numeric' });
}

/** "September 2026" — used by the product change timeline. */
export function formatMonthYear(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString(IST, { month: 'long', year: 'numeric' });
}

export function formatTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleTimeString(IST, { hour: 'numeric', minute: '2-digit', hour12: true }).toUpperCase();
}

/** "3 products" · "1 product" */
export const plural = (n, singular, suffix = 's') => `${n} ${singular}${n === 1 ? '' : suffix}`;

/** Initials for avatars — max two characters, no emoji, no images. */
export function initials(name = '') {
  const parts = String(name).replace(/[^\p{L}\s.]/gu, '').trim().split(/[\s.]+/).filter(Boolean);
  if (!parts.length) return '—';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function greeting(date = new Date()) {
  const h = date.getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

/** Compliance score from a declaration checklist. */
export function complianceScore(declarations = []) {
  if (!declarations.length) return 0;
  const ok = declarations.filter((d) => d.status === 'COMPLIANT').length;
  return Math.round((ok / declarations.length) * 100);
}

export const titleCase = (s = '') =>
  String(s)
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (m) => m.toUpperCase());
