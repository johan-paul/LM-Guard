import React, { useState, useRef, useEffect } from 'react';
import { MoreHorizontal } from 'lucide-react';

/**
 * Compact row action menu.
 * items: [{ label, icon, onSelect, tone?: 'default' | 'danger' | 'success', divider? }]
 */
export default function RowMenu({ items, label = 'Row actions' }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onClick = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const tones = {
    default: 'text-ink-700 hover:text-ink-900',
    danger: 'text-danger-600 hover:text-danger-700',
    success: 'text-success-700 hover:text-success-700',
  };

  return (
    <span ref={ref} className="relative inline-block">
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setOpen((v) => !v);
        }}
        aria-label={label}
        aria-expanded={open}
        className={`rounded-md p-1.5 transition-colors ${open ? 'bg-canvas text-ink-900' : 'text-ink-400 hover:bg-canvas hover:text-ink-900'}`}
      >
        <MoreHorizontal className="h-4 w-4" strokeWidth={2} />
      </button>

      {open && (
        <div className="absolute right-0 top-9 z-20 w-[188px] overflow-hidden rounded-lg border border-line bg-surface p-1.5 text-left shadow-pop animate-fade-up">
          {items.map((item, i) => {
            const Icon = item.icon;
            return (
              <React.Fragment key={item.label}>
                {item.divider && i > 0 && <span className="my-1 block h-px bg-line" />}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setOpen(false);
                    item.onSelect();
                  }}
                  className={`flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-13 transition-colors hover:bg-canvas ${
                    tones[item.tone || 'default']
                  }`}
                >
                  {Icon && <Icon className="h-3.5 w-3.5 shrink-0 opacity-70" strokeWidth={1.9} />}
                  {item.label}
                </button>
              </React.Fragment>
            );
          })}
        </div>
      )}
    </span>
  );
}
