import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import AppSidebar from '../components/AppSidebar';
import TopHeader from '../components/TopHeader';
import { VIOLATIONS } from '../data/violations';

/**
 * Application shell: fixed navigation rail, sticky header, scrolling content.
 */
export default function AppLayout({ children }) {
  const [navOpen, setNavOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    setNavOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    document.body.style.overflow = navOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [navOpen]);

  const counters = {
    openViolations: VIOLATIONS.filter((v) => ['OPEN', 'UNDER_REVIEW'].includes(v.status)).length,
  };

  return (
    <div className="min-h-screen bg-canvas">
      <AppSidebar open={navOpen} onClose={() => setNavOpen(false)} counters={counters} />

      <div className="flex min-h-screen flex-col lg:pl-[260px]">
        <TopHeader onMenuClick={() => setNavOpen(true)} />
        <main className="flex-1 px-4 py-6 sm:px-6 sm:py-7 lg:px-8">
          <div className="mx-auto w-full max-w-[1440px] animate-fade-in">{children}</div>
        </main>
        <footer className="border-t border-line px-4 py-4 sm:px-6 lg:px-8">
          <div className="mx-auto flex w-full max-w-[1440px] flex-col gap-1.5 text-[11px] text-ink-400 sm:flex-row sm:items-center sm:justify-between">
            <p>LM-GUARD · Legal Metrology compliance intelligence · Ruleset LMPC 2026.1</p>
            <p>AI observes. Rules validate. Humans decide.</p>
          </div>
        </footer>
      </div>
    </div>
  );
}
