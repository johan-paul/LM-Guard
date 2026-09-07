import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Menu, Search, Bell, ChevronRight, ChevronDown, LogOut, Settings, UserRound, ClipboardList, AlertOctagon, Package } from 'lucide-react';
import { breadcrumbsFor } from '../config/navigation';
import { useAuth } from '../context/AuthContext';
import { initials, formatDateTime } from '../utils/format';
import { INSPECTIONS } from '../data/inspections';
import { VIOLATIONS } from '../data/violations';
import { PRODUCTS } from '../data/products';

/** Closes a floating panel on outside click or Escape. */
function useDismiss(ref, onDismiss, active) {
  useEffect(() => {
    if (!active) return undefined;
    const onClick = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onDismiss();
    };
    const onKey = (e) => {
      if (e.key === 'Escape') onDismiss();
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [ref, onDismiss, active]);
}

const hit = (v, q) => String(v || '').toLowerCase().includes(q);

export default function TopHeader({ onMenuClick }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const [query, setQuery] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [bellOpen, setBellOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const searchRef = useRef(null);
  const bellRef = useRef(null);
  const profileRef = useRef(null);

  useDismiss(searchRef, () => setSearchOpen(false), searchOpen);
  useDismiss(bellRef, () => setBellOpen(false), bellOpen);
  useDismiss(profileRef, () => setProfileOpen(false), profileOpen);

  useEffect(() => {
    setSearchOpen(false);
    setBellOpen(false);
    setProfileOpen(false);
  }, [location.pathname]);

  const crumbs = breadcrumbsFor(location.pathname);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (q.length < 2) return null;
    return {
      inspections: INSPECTIONS.filter((i) => hit(i.id, q) || hit(i.productName, q) || hit(i.inspector, q)).slice(0, 3),
      violations: VIOLATIONS.filter((v) => hit(v.id, q) || hit(v.title, q) || hit(v.ruleId, q)).slice(0, 3),
      products: PRODUCTS.filter((p) => hit(p.id, q) || hit(p.name, q) || hit(p.manufacturer, q)).slice(0, 3),
    };
  }, [query]);

  const resultCount = results ? results.inspections.length + results.violations.length + results.products.length : 0;

  const alerts = useMemo(
    () =>
      VIOLATIONS.filter((v) => ['OPEN', 'UNDER_REVIEW', 'ESCALATED'].includes(v.status))
        .sort((a, b) => new Date(b.detectedAt) - new Date(a.detectedAt))
        .slice(0, 5),
    [],
  );

  const go = (to) => {
    setQuery('');
    setSearchOpen(false);
    navigate(to);
  };

  return (
    <header className="sticky top-0 z-30 flex h-[60px] shrink-0 items-center gap-3 border-b border-line bg-surface/95 px-4 backdrop-blur sm:px-6">
      <button
        type="button"
        onClick={onMenuClick}
        className="-ml-1 rounded-md p-2 text-ink-500 transition-colors hover:bg-canvas hover:text-ink-900 lg:hidden"
        aria-label="Open navigation"
      >
        <Menu className="h-5 w-5" />
      </button>

      {/* Breadcrumb */}
      <nav aria-label="Breadcrumb" className="min-w-0 flex-1">
        <ol className="flex items-center gap-1.5 text-13">
          {crumbs.map((c, i) => (
            <li key={`${c.label}-${i}`} className="flex min-w-0 items-center gap-1.5">
              {i > 0 && <ChevronRight className="h-3.5 w-3.5 shrink-0 text-ink-300" strokeWidth={2} />}
              {c.to ? (
                <Link to={c.to} className="truncate text-ink-500 transition-colors hover:text-ink-900">
                  {c.label}
                </Link>
              ) : (
                <span className="truncate font-medium text-ink-900">{c.label}</span>
              )}
            </li>
          ))}
        </ol>
      </nav>

      {/* Search */}
      <div ref={searchRef} className="relative">
        <div className="hidden md:block">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-[15px] w-[15px] -translate-y-1/2 text-ink-400" />
          <input
            type="search"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSearchOpen(true);
            }}
            onFocus={() => setSearchOpen(true)}
            placeholder="Search records, cases, products"
            className="input h-9 w-[220px] pl-9 lg:w-[280px]"
            aria-label="Search the platform"
          />
        </div>
        <button
          type="button"
          onClick={() => setSearchOpen((v) => !v)}
          className="rounded-md p-2 text-ink-500 transition-colors hover:bg-canvas hover:text-ink-900 md:hidden"
          aria-label="Search"
        >
          <Search className="h-5 w-5" />
        </button>

        {searchOpen && (
          <div className="absolute right-0 top-[46px] w-[320px] overflow-hidden rounded-xl border border-line bg-surface shadow-pop animate-fade-up sm:w-[380px]">
            <div className="border-b border-line p-2 md:hidden">
              <input
                autoFocus
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search records, cases, products"
                className="input"
              />
            </div>

            {!results ? (
              <p className="px-4 py-5 text-13 text-ink-500">Type at least two characters to search.</p>
            ) : resultCount === 0 ? (
              <p className="px-4 py-5 text-13 text-ink-500">
                No records matched “<span className="text-ink-800">{query}</span>”.
              </p>
            ) : (
              <div className="max-h-[340px] overflow-y-auto scrollbar-slim py-1.5">
                <ResultGroup
                  label="Inspections"
                  icon={ClipboardList}
                  rows={results.inspections.map((i) => ({ id: i.id, primary: i.productName, to: `/inspections/${i.id}` }))}
                  onSelect={go}
                />
                <ResultGroup
                  label="Violations"
                  icon={AlertOctagon}
                  rows={results.violations.map((v) => ({ id: v.id, primary: v.title, to: `/violations/${v.id}` }))}
                  onSelect={go}
                />
                <ResultGroup
                  label="Products"
                  icon={Package}
                  rows={results.products.map((p) => ({ id: p.id, primary: p.name, to: `/products/${p.id}` }))}
                  onSelect={go}
                />
              </div>
            )}
          </div>
        )}
      </div>

      {/* Notifications */}
      <div ref={bellRef} className="relative">
        <button
          type="button"
          onClick={() => setBellOpen((v) => !v)}
          className="relative rounded-md p-2 text-ink-500 transition-colors hover:bg-canvas hover:text-ink-900"
          aria-label={`Notifications, ${alerts.length} pending`}
        >
          <Bell className="h-[18px] w-[18px]" strokeWidth={1.75} />
          {alerts.length > 0 && (
            <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-danger-600 ring-2 ring-surface" />
          )}
        </button>

        {bellOpen && (
          <div className="absolute right-0 top-[46px] w-[320px] overflow-hidden rounded-xl border border-line bg-surface shadow-pop animate-fade-up sm:w-[360px]">
            <div className="flex items-center justify-between border-b border-line px-4 py-3">
              <span className="text-13 font-semibold text-ink-900">Pending decisions</span>
              <span className="tabular text-micro font-semibold uppercase text-ink-400">{alerts.length} open</span>
            </div>
            <ul className="max-h-[320px] overflow-y-auto scrollbar-slim">
              {alerts.map((a) => (
                <li key={a.id}>
                  <Link
                    to={`/violations/${a.id}`}
                    className="flex gap-3 border-b border-line-soft px-4 py-3 transition-colors last:border-b-0 hover:bg-canvas"
                  >
                    <span
                      className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${
                        a.riskLevel === 'HIGH' ? 'bg-critical-600' : 'bg-warning-600'
                      }`}
                    />
                    <span className="min-w-0">
                      <span className="block truncate text-13 font-medium text-ink-900">{a.title}</span>
                      <span className="block truncate text-xs text-ink-500">{a.productName}</span>
                      <span className="mt-0.5 block font-mono text-[11px] text-ink-400">
                        {a.id} · {formatDateTime(a.detectedAt)}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            <Link
              to="/violations"
              className="block border-t border-line px-4 py-2.5 text-center text-13 font-medium text-brand-600 transition-colors hover:bg-canvas"
            >
              Open case queue
            </Link>
          </div>
        )}
      </div>

      <span className="hidden h-6 w-px bg-line sm:block" />

      {/* Profile */}
      <div ref={profileRef} className="relative">
        <button
          type="button"
          onClick={() => setProfileOpen((v) => !v)}
          className="flex items-center gap-2.5 rounded-md py-1 pl-1 pr-1.5 transition-colors hover:bg-canvas"
        >
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-navy-900 text-[11px] font-semibold text-white">
            {initials(user?.name || 'Officer')}
          </span>
          <span className="hidden text-left md:block">
            <span className="block text-13 font-medium leading-tight text-ink-900">{user?.name || 'Officer'}</span>
            <span className="block text-[11px] leading-tight text-ink-500">{user?.role || 'Inspecting Officer'}</span>
          </span>
          <ChevronDown className="hidden h-4 w-4 text-ink-400 md:block" strokeWidth={2} />
        </button>

        {profileOpen && (
          <div className="absolute right-0 top-[46px] w-[240px] overflow-hidden rounded-xl border border-line bg-surface shadow-pop animate-fade-up">
            <div className="border-b border-line px-4 py-3">
              <p className="text-13 font-semibold text-ink-900">{user?.name || 'Officer'}</p>
              <p className="text-xs text-ink-500">{user?.role || 'Inspecting Officer'}</p>
              <p className="mt-1.5 font-mono text-[11px] text-ink-400">
                {user?.badgeId || 'LM-INS-014'} · {user?.zone || 'Coimbatore North'}
              </p>
            </div>
            <div className="p-1.5">
              <Link to="/settings" className="flex items-center gap-2.5 rounded-md px-2.5 py-2 text-13 text-ink-700 transition-colors hover:bg-canvas hover:text-ink-900">
                <UserRound className="h-4 w-4 text-ink-400" strokeWidth={1.75} /> Profile
              </Link>
              <Link to="/settings" className="flex items-center gap-2.5 rounded-md px-2.5 py-2 text-13 text-ink-700 transition-colors hover:bg-canvas hover:text-ink-900">
                <Settings className="h-4 w-4 text-ink-400" strokeWidth={1.75} /> Settings
              </Link>
              <button
                type="button"
                onClick={() => {
                  logout();
                  navigate('/login', { replace: true });
                }}
                className="flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-13 text-ink-700 transition-colors hover:bg-canvas hover:text-danger-600"
              >
                <LogOut className="h-4 w-4 text-ink-400" strokeWidth={1.75} /> Sign out
              </button>
            </div>
          </div>
        )}
      </div>
    </header>
  );
}

function ResultGroup({ label, icon: Icon, rows, onSelect }) {
  if (!rows.length) return null;
  return (
    <div className="mb-1 last:mb-0">
      <p className="px-4 py-1.5 text-[10px] font-semibold uppercase tracking-[0.1em] text-ink-400">{label}</p>
      {rows.map((r) => (
        <button
          key={r.id}
          type="button"
          onClick={() => onSelect(r.to)}
          className="flex w-full items-center gap-3 px-4 py-2 text-left transition-colors hover:bg-canvas"
        >
          <Icon className="h-4 w-4 shrink-0 text-ink-400" strokeWidth={1.75} />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-13 text-ink-900">{r.primary}</span>
            <span className="block font-mono text-[11px] text-ink-400">{r.id}</span>
          </span>
        </button>
      ))}
    </div>
  );
}
