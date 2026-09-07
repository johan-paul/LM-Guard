import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { ShieldCheck, LogOut, X } from 'lucide-react';
import { NAV_GROUPS, SETTINGS_ITEM } from '../config/navigation';
import { useAuth } from '../context/AuthContext';
import { initials } from '../utils/format';

/**
 * Fixed navy navigation rail. Off-canvas below lg, static from lg upwards.
 */
export default function AppSidebar({ open, onClose, counters = {} }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleSignOut = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <>
      {/* Scrim — mobile only */}
      <div
        className={`fixed inset-0 z-40 bg-navy-950/50 backdrop-blur-[1px] transition-opacity duration-200 lg:hidden ${
          open ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-[260px] flex-col bg-navy-900 transition-transform duration-250 ease-out-expo lg:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
        aria-label="Primary"
      >
        {/* Brand */}
        <div className="flex h-[60px] shrink-0 items-center gap-2.5 border-b border-white/[0.07] px-4">
          <span className="flex h-8 w-8 items-center justify-center rounded-md bg-brand-600/15 ring-1 ring-inset ring-brand-500/30">
            <ShieldCheck className="h-[18px] w-[18px] text-brand-400" strokeWidth={2} />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-[15px] font-semibold leading-tight tracking-tight text-white">LM-GUARD</span>
            <span className="block truncate text-[10.5px] leading-tight text-navy-100/55">
              AI-Assisted Legal Metrology
            </span>
          </span>
          <button
            type="button"
            onClick={onClose}
            className="-mr-1 rounded p-1.5 text-navy-100/60 hover:bg-white/[0.06] hover:text-white lg:hidden"
            aria-label="Close navigation"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Navigation */}
        <nav className="scrollbar-slim mt-5 flex-1 overflow-y-auto px-3 pb-4">
          {NAV_GROUPS.map((group) => (
            <div key={group.label} className="mb-5">
              <p className="mb-1.5 px-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-navy-100/35">
                {group.label}
              </p>
              <ul className="space-y-0.5">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const badge = item.badgeKey ? counters[item.badgeKey] : null;
                  return (
                    <li key={item.to}>
                      <NavLink
                        to={item.to}
                        onClick={onClose}
                        className={({ isActive }) => `nav-item ${isActive ? 'nav-item-active' : ''}`}
                      >
                        <Icon className="h-[17px] w-[17px] shrink-0" strokeWidth={1.75} />
                        <span className="flex-1 truncate">{item.label}</span>
                        {badge ? (
                          <span className="tabular rounded-sm bg-white/[0.09] px-1.5 py-0.5 font-mono text-[10.5px] font-medium text-navy-100/80">
                            {badge}
                          </span>
                        ) : null}
                      </NavLink>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>

        {/* Ruleset status */}
        <div className="mx-3 mb-3 rounded-md border border-white/[0.07] bg-white/[0.03] px-3 py-2.5">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-semibold uppercase tracking-[0.1em] text-navy-100/40">Ruleset</span>
            <span className="flex items-center gap-1.5 text-[10.5px] font-medium text-success-600">
              <span className="h-1.5 w-1.5 rounded-full bg-success-600" />
              Active
            </span>
          </div>
          <p className="mt-1 font-mono text-xs text-navy-100/80">LMPC 2026.1</p>
        </div>

        {/* Settings + profile */}
        <div className="border-t border-white/[0.07] px-3 py-3">
          <NavLink
            to={SETTINGS_ITEM.to}
            onClick={onClose}
            className={({ isActive }) => `nav-item mb-2 ${isActive ? 'nav-item-active' : ''}`}
          >
            <SETTINGS_ITEM.icon className="h-[17px] w-[17px] shrink-0" strokeWidth={1.75} />
            <span className="flex-1 truncate">{SETTINGS_ITEM.label}</span>
          </NavLink>

          <div className="flex items-center gap-2.5 rounded-md px-2 py-2">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-navy-700 text-[11px] font-semibold text-white ring-1 ring-inset ring-white/10">
              {initials(user?.name || 'Officer')}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-13 font-medium leading-tight text-white">
                {user?.name || 'Officer'}
              </span>
              <span className="block truncate text-[11px] leading-tight text-navy-100/50">
                {user?.role || 'Inspecting Officer'}
              </span>
            </span>
            <button
              type="button"
              onClick={handleSignOut}
              className="rounded p-1.5 text-navy-100/50 transition-colors hover:bg-white/[0.06] hover:text-white"
              aria-label="Sign out"
              title="Sign out"
            >
              <LogOut className="h-4 w-4" strokeWidth={1.75} />
            </button>
          </div>
        </div>
      </aside>
    </>
  );
}
