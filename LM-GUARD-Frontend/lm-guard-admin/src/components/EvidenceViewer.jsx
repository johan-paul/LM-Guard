import React, { useState, useRef, useCallback, useEffect } from 'react';
import { ZoomIn, ZoomOut, Maximize2, Focus, Frame, ChevronLeft, ChevronRight, Crosshair } from 'lucide-react';
import { LABEL_CANVAS } from '../data/assets';

const ZOOM_STEPS = [1, 1.4, 2, 3];

/**
 * Evidence surface.
 *
 * Renders the captured package artwork with the rule engine's bounding boxes
 * overlaid in the label coordinate space, so a finding can be traced to the
 * exact region of the panel it came from.
 */
export default function EvidenceViewer({
  image,
  boxes = [],
  caption,
  reference,
  canvas = LABEL_CANVAS,
  className = '',
  minHeight = 'min-h-[320px]',
}) {
  const [zoomIdx, setZoomIdx] = useState(0);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [showBoxes, setShowBoxes] = useState(true);
  const [isolate, setIsolate] = useState(true);
  const [activeIdx, setActiveIdx] = useState(0);
  const dragState = useRef(null);

  const zoom = ZOOM_STEPS[zoomIdx];
  const active = boxes[activeIdx];

  useEffect(() => {
    setActiveIdx(0);
    setZoomIdx(0);
    setOffset({ x: 0, y: 0 });
  }, [image]);

  const resetView = useCallback(() => {
    setZoomIdx(0);
    setOffset({ x: 0, y: 0 });
  }, []);

  /* Panning: track pointer deltas against the drag origin, clamped to the zoom. */
  const handleMove = (e) => {
    const st = dragState.current;
    if (!st) return;
    const limit = 160 * (zoom - 1) + 40;
    setOffset({
      x: Math.max(-limit, Math.min(limit, st.ox + (e.clientX - st.px))),
      y: Math.max(-limit, Math.min(limit, st.oy + (e.clientY - st.py))),
    });
  };

  const handleDown = (e) => {
    if (zoom === 1) return;
    dragState.current = { px: e.clientX, py: e.clientY, ox: offset.x, oy: offset.y };
    e.currentTarget.setPointerCapture?.(e.pointerId);
  };

  const handleUp = () => {
    dragState.current = null;
  };

  const pct = (v, total) => `${(v / total) * 100}%`;

  return (
    <div className={`flex h-full flex-col ${className}`}>
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-2.5">
        <span className="flex items-center gap-1.5 text-micro font-semibold uppercase text-ink-500">
          <Crosshair className="h-3.5 w-3.5 text-brand-600" strokeWidth={2} />
          Evidence
        </span>

        {boxes.length > 1 && (
          <span className="flex items-center gap-1 rounded-md border border-line bg-canvas p-0.5">
            <button
              type="button"
              onClick={() => setActiveIdx((i) => (i - 1 + boxes.length) % boxes.length)}
              className="rounded p-1 text-ink-500 hover:bg-surface hover:text-ink-900"
              aria-label="Previous region"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <span className="tabular px-1 font-mono text-[11px] text-ink-600">
              {activeIdx + 1}/{boxes.length}
            </span>
            <button
              type="button"
              onClick={() => setActiveIdx((i) => (i + 1) % boxes.length)}
              className="rounded p-1 text-ink-500 hover:bg-surface hover:text-ink-900"
              aria-label="Next region"
            >
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </span>
        )}

        <div className="ml-auto flex items-center gap-1">
          <ToolButton active={showBoxes} onClick={() => setShowBoxes((v) => !v)} label="Toggle regions" icon={Frame} />
          <ToolButton
            active={isolate}
            onClick={() => setIsolate((v) => !v)}
            label="Isolate active region"
            icon={Focus}
            disabled={!showBoxes || !active}
          />
          <span className="mx-1 h-5 w-px bg-line" />
          <ToolButton
            onClick={() => setZoomIdx((i) => Math.max(0, i - 1))}
            label="Zoom out"
            icon={ZoomOut}
            disabled={zoomIdx === 0}
          />
          <span className="tabular w-10 text-center font-mono text-[11px] text-ink-600">{zoom.toFixed(1)}×</span>
          <ToolButton
            onClick={() => setZoomIdx((i) => Math.min(ZOOM_STEPS.length - 1, i + 1))}
            label="Zoom in"
            icon={ZoomIn}
            disabled={zoomIdx === ZOOM_STEPS.length - 1}
          />
          <ToolButton onClick={resetView} label="Reset view" icon={Maximize2} disabled={zoom === 1 && offset.x === 0} />
        </div>
      </div>

      {/* Stage */}
      <div
        className={`relative flex flex-1 items-center justify-center overflow-hidden bg-navy-950 p-4 ${minHeight} ${
          zoom > 1 ? 'cursor-grab active:cursor-grabbing' : ''
        }`}
        onPointerDown={handleDown}
        onPointerMove={handleMove}
        onPointerUp={handleUp}
        onPointerLeave={handleUp}
      >
        <div
          className="w-full transition-transform duration-200 ease-out-expo"
          style={{ transform: `translate(${offset.x}px, ${offset.y}px) scale(${zoom})` }}
        >
          <div className="relative mx-auto w-full max-w-[760px]">
            <img
              src={image}
              alt={caption || 'Package principal display panel'}
              className="block h-auto w-full select-none rounded-sm shadow-modal"
              draggable={false}
            />

            {showBoxes &&
              boxes.map((b, i) => {
                const isActive = i === activeIdx;
                return (
                  <button
                    key={`${b.region || 'box'}-${i}`}
                    type="button"
                    onClick={() => setActiveIdx(i)}
                    className="absolute block rounded-[2px] transition-all duration-200"
                    style={{
                      left: pct(b.x, canvas.width),
                      top: pct(b.y, canvas.height),
                      width: pct(b.width, canvas.width),
                      height: pct(b.height, canvas.height),
                      border: `1.5px solid ${isActive ? '#DC2626' : 'rgba(255,255,255,0.55)'}`,
                      background: isActive ? 'rgba(220,38,38,0.10)' : 'rgba(255,255,255,0.04)',
                      boxShadow: isActive && isolate ? '0 0 0 9999px rgba(7,21,35,0.62)' : 'none',
                    }}
                    aria-label={b.label || `Evidence region ${i + 1}`}
                  >
                    {/* Corner ticks */}
                    {isActive && (
                      <>
                        <Corner className="-left-px -top-px border-l-2 border-t-2" />
                        <Corner className="-right-px -top-px border-r-2 border-t-2" />
                        <Corner className="-bottom-px -left-px border-b-2 border-l-2" />
                        <Corner className="-bottom-px -right-px border-b-2 border-r-2" />
                      </>
                    )}
                    {b.label && (
                      <span
                        className={`absolute -top-[7px] left-0 -translate-y-full whitespace-nowrap rounded-sm px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wider ${
                          isActive ? 'bg-danger-600 text-white' : 'bg-navy-900/80 text-white/80'
                        }`}
                        style={{ fontSize: `${9 / zoom + 2}px` }}
                      >
                        {b.label}
                      </span>
                    )}
                  </button>
                );
              })}
          </div>
        </div>

        {/* Frame corners on the stage itself */}
        <div className="pointer-events-none absolute inset-0 ring-1 ring-inset ring-white/[0.06]" />
      </div>

      {/* Caption strip */}
      {(active?.note || caption || reference) && (
        <div className="flex flex-wrap items-center gap-x-6 gap-y-1.5 border-t border-line bg-canvas/60 px-4 py-2.5">
          {active?.note && <p className="min-w-0 flex-1 text-xs text-ink-600">{active.note}</p>}
          {!active?.note && caption && <p className="min-w-0 flex-1 text-xs text-ink-600">{caption}</p>}
          {reference && <p className="shrink-0 font-mono text-[11px] text-ink-400">{reference}</p>}
        </div>
      )}
    </div>
  );
}

function Corner({ className }) {
  return <span className={`absolute h-2 w-2 border-danger-600 ${className}`} aria-hidden="true" />;
}

function ToolButton({ icon: Icon, label, onClick, active, disabled }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      aria-pressed={active}
      className={`rounded-md p-1.5 transition-colors disabled:opacity-35 ${
        active ? 'bg-brand-50 text-brand-700' : 'text-ink-500 hover:bg-canvas hover:text-ink-900'
      }`}
    >
      <Icon className="h-[15px] w-[15px]" strokeWidth={1.9} />
    </button>
  );
}
