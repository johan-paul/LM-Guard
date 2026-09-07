import React from 'react';
import { ShieldCheck } from 'lucide-react';

const PIPELINE = [
  { step: 'Package', detail: 'Field capture of the display panel' },
  { step: 'AI analysis', detail: 'OCR extraction of printed declarations' },
  { step: 'Structured facts', detail: 'Normalised, comparable declaration fields' },
  { step: 'Rule validation', detail: 'Deterministic evaluation, versioned ruleset' },
  { step: 'Evidence', detail: 'Every finding bound to a region of the panel' },
  { step: 'Human review', detail: 'The officer records the decision' },
];

/** Institutional panel shown beside the sign-in and registration forms. */
export default function AuthAside() {
  return (
    <aside className="relative hidden flex-col justify-between overflow-hidden bg-navy-900 px-10 py-12 lg:flex">
      <div className="pointer-events-none absolute inset-0 grid-lines opacity-60" aria-hidden="true" />

      <div className="relative">
        <div className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-md bg-brand-600/15 ring-1 ring-inset ring-brand-500/30">
            <ShieldCheck className="h-5 w-5 text-brand-400" strokeWidth={2} />
          </span>
          <div>
            <p className="text-[15px] font-semibold leading-tight tracking-tight text-white">LM-GUARD</p>
            <p className="text-[11px] leading-tight text-navy-100/55">AI-Assisted Legal Metrology</p>
          </div>
        </div>
      </div>

      <div className="relative max-w-md">
        <p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-brand-400">Compliance intelligence</p>
        <h2 className="mt-3 text-[30px] font-semibold leading-[1.15] tracking-tight text-white">
          See it. Verify it.
          <br />
          Prove it. Act on it.
        </h2>
        <p className="mt-4 text-sm leading-relaxed text-navy-100/60">
          An evidence-first inspection platform for packaged commodities. AI observes, rules validate, and the
          inspecting officer decides.
        </p>

        <ol className="mt-8 space-y-3">
          {PIPELINE.map((p, i) => (
            <li key={p.step} className="flex items-start gap-3">
              <span className="mt-0.5 w-5 shrink-0 font-mono text-[11px] text-brand-400">
                {String(i + 1).padStart(2, '0')}
              </span>
              <span className="min-w-0">
                <span className="block text-13 font-medium text-white/90">{p.step}</span>
                <span className="block text-xs text-navy-100/45">{p.detail}</span>
              </span>
            </li>
          ))}
        </ol>
      </div>

      <div className="relative flex items-center gap-4 border-t border-white/[0.07] pt-5">
        <span className="font-mono text-[11px] text-navy-100/45">Ruleset LMPC 2026.1</span>
        <span className="h-1 w-1 rounded-full bg-navy-100/25" />
        <span className="font-mono text-[11px] text-navy-100/45">Demonstration environment</span>
      </div>
    </aside>
  );
}
