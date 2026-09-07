/**
 * Generated visual assets.
 *
 * Evidence images are synthesised as SVG "principal display panel" scans so that
 * every bounding box in the evidence viewer lands on real declaration text.
 * This keeps the demo fully self-contained (no external image hosts) while
 * remaining faithful to how a Legal Metrology back-panel is actually laid out.
 *
 * Canvas is a fixed 1000 x 680 coordinate space. All violation bounding boxes
 * stored in the data layer are expressed in this same space.
 */

export const LABEL_CANVAS = { width: 1000, height: 680 };

/** Named regions of the label — violation evidence boxes reference these. */
export const LABEL_REGIONS = {
  brand: { x: 44, y: 34, width: 470, height: 78 },
  netQuantity: { x: 44, y: 186, width: 336, height: 94 },
  mrp: { x: 44, y: 300, width: 336, height: 104 },
  mfgDate: { x: 44, y: 424, width: 336, height: 80 },
  batch: { x: 44, y: 524, width: 336, height: 72 },
  manufacturer: { x: 424, y: 186, width: 532, height: 136 },
  consumerCare: { x: 424, y: 342, width: 532, height: 152 },
  origin: { x: 424, y: 514, width: 300, height: 82 },
  barcode: { x: 762, y: 500, width: 194, height: 112 },
};

const esc = (s = '') =>
  String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const INK = '#1B2430';
const MUTED = '#5C6675';
const FAINT = '#9AA3B0';
const PAPER = '#F4F2ED';

function block({ x, y, label, lines, tone = INK, labelSize = 15, size = 27, weight = 600, gap = 30 }) {
  const head = `<text x="${x}" y="${y + labelSize}" font-family="Inter, Helvetica, Arial, sans-serif" font-size="${labelSize}" letter-spacing="1.6" font-weight="600" fill="${FAINT}">${esc(
    label,
  )}</text>`;
  const body = lines
    .map(
      (ln, i) =>
        `<text x="${x}" y="${y + labelSize + 30 + i * gap}" font-family="Inter, Helvetica, Arial, sans-serif" font-size="${size}" font-weight="${weight}" fill="${tone}">${esc(
          ln,
        )}</text>`,
    )
    .join('');
  return head + body;
}

function barcode(x, y) {
  let bars = '';
  let cursor = x + 10;
  const widths = [3, 5, 2, 4, 2, 6, 3, 2, 5, 3, 4, 2, 3, 6, 2, 4, 3, 5, 2, 3, 4, 2, 5, 3, 2, 4, 3, 6, 2, 3];
  widths.forEach((w, i) => {
    if (i % 2 === 0) {
      bars += `<rect x="${cursor}" y="${y + 8}" width="${w}" height="66" fill="${INK}"/>`;
    }
    cursor += w + 2;
  });
  return `${bars}<text x="${x + 10}" y="${y + 96}" font-family="'JetBrains Mono', monospace" font-size="15" letter-spacing="2" fill="${MUTED}">8 901234 567890</text>`;
}

/**
 * Renders a package back-panel label as an inline SVG data URI.
 *
 * @param {object} cfg
 * @param {string} cfg.brand           Product brand line
 * @param {string} [cfg.variant]       Sub-title / variant line
 * @param {string} [cfg.netQuantity]
 * @param {string} [cfg.mrp]
 * @param {string} [cfg.mfgDate]
 * @param {string} [cfg.batch]
 * @param {string[]} [cfg.manufacturer] Address lines
 * @param {string[]} [cfg.consumerCare] Consumer care lines
 * @param {string} [cfg.origin]
 * @param {string[]} [cfg.missing]      Region keys to render as absent
 * @param {string[]} [cfg.faded]        Region keys to render as low-legibility
 * @param {boolean} [cfg.overSticker]   Draws a re-priced sticker over the MRP
 */
export function packageLabel(cfg = {}) {
  const {
    brand = 'PACKAGED COMMODITY',
    variant = '',
    netQuantity = '500 g',
    mrp = '₹99.00',
    mfgDate = '06/2026',
    batch = 'B-2026-0455',
    manufacturer = ['Manufacturer Pvt Ltd', 'Industrial Estate, India'],
    consumerCare = ['care@example.in', '1800-000-0000'],
    origin = 'India',
    missing = [],
    faded = [],
    overSticker = false,
  } = cfg;

  const isMissing = (k) => missing.includes(k);
  const toneOf = (k) => (faded.includes(k) ? FAINT : INK);

  const R = LABEL_REGIONS;
  let body = '';

  /* Brand header */
  body += `<text x="${R.brand.x}" y="${R.brand.y + 40}" font-family="Inter, Helvetica, Arial, sans-serif" font-size="42" font-weight="700" letter-spacing="-0.5" fill="${INK}">${esc(
    brand,
  )}</text>`;
  if (variant) {
    body += `<text x="${R.brand.x}" y="${R.brand.y + 70}" font-family="Inter, Helvetica, Arial, sans-serif" font-size="20" font-weight="500" letter-spacing="0.5" fill="${MUTED}">${esc(
      variant,
    )}</text>`;
  }
  body += `<rect x="44" y="146" width="912" height="1.5" fill="#D9D5CC"/>`;

  /* Left column */
  body += block({
    x: R.netQuantity.x,
    y: R.netQuantity.y,
    label: 'NET QUANTITY',
    lines: isMissing('netQuantity') ? ['—'] : [netQuantity],
    tone: isMissing('netQuantity') ? FAINT : toneOf('netQuantity'),
    size: 30,
    weight: 700,
  });

  body += block({
    x: R.mrp.x,
    y: R.mrp.y,
    label: 'MAXIMUM RETAIL PRICE',
    lines: isMissing('mrp') ? ['—'] : [mrp, '(incl. of all taxes)'],
    tone: isMissing('mrp') ? FAINT : toneOf('mrp'),
    size: 30,
    weight: 700,
    gap: 32,
  });
  if (overSticker) {
    body += `<g transform="rotate(-2 ${R.mrp.x + 150} ${R.mrp.y + 60})">
      <rect x="${R.mrp.x - 6}" y="${R.mrp.y + 26}" width="248" height="60" rx="3" fill="#FBE9A8" stroke="#D6BE63" stroke-width="1.5"/>
      <text x="${R.mrp.x + 10}" y="${R.mrp.y + 68}" font-family="Inter, Helvetica, Arial, sans-serif" font-size="28" font-weight="700" fill="#1B2430">₹149.00</text>
    </g>`;
  }
  if (faded.includes('mrp')) {
    body += `<rect x="${R.mrp.x - 8}" y="${R.mrp.y + 24}" width="250" height="62" fill="#F4F2ED" opacity="0.45"/>`;
  }

  body += block({
    x: R.mfgDate.x,
    y: R.mfgDate.y,
    label: 'MFD / PKD',
    lines: isMissing('mfgDate') ? ['—'] : [mfgDate],
    tone: isMissing('mfgDate') ? FAINT : toneOf('mfgDate'),
    size: 24,
    weight: 600,
  });

  body += block({
    x: R.batch.x,
    y: R.batch.y,
    label: 'BATCH NO.',
    lines: [batch],
    tone: MUTED,
    size: 20,
    weight: 500,
  });

  /* Right column */
  body += block({
    x: R.manufacturer.x,
    y: R.manufacturer.y,
    label: 'MANUFACTURED & PACKED BY',
    lines: isMissing('manufacturer') ? ['—'] : manufacturer,
    tone: isMissing('manufacturer') ? FAINT : toneOf('manufacturer'),
    size: 19,
    weight: 500,
    gap: 26,
  });

  body += block({
    x: R.consumerCare.x,
    y: R.consumerCare.y,
    label: 'CONSUMER CARE',
    lines: isMissing('consumerCare') ? ['— not printed —'] : consumerCare,
    tone: isMissing('consumerCare') ? FAINT : toneOf('consumerCare'),
    size: faded.includes('consumerCare') ? 14 : 19,
    weight: 500,
    gap: faded.includes('consumerCare') ? 20 : 26,
  });

  body += block({
    x: R.origin.x,
    y: R.origin.y,
    label: 'COUNTRY OF ORIGIN',
    lines: isMissing('origin') ? ['—'] : [origin],
    tone: isMissing('origin') ? FAINT : toneOf('origin'),
    size: 22,
    weight: 600,
  });

  body += barcode(R.barcode.x, R.barcode.y);

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="680" viewBox="0 0 1000 680">
  <defs>
    <linearGradient id="pg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#FBFAF7"/>
      <stop offset="55%" stop-color="${PAPER}"/>
      <stop offset="100%" stop-color="#E9E6DE"/>
    </linearGradient>
    <filter id="grain"><feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="3" result="n"/><feColorMatrix in="n" type="saturate" values="0"/><feComponentTransfer><feFuncA type="linear" slope="0.06"/></feComponentTransfer></filter>
  </defs>
  <rect width="1000" height="680" fill="url(#pg)"/>
  <rect width="1000" height="680" filter="url(#grain)" opacity="0.5"/>
  <rect x="16" y="16" width="968" height="648" rx="6" fill="none" stroke="#D9D5CC" stroke-width="2"/>
  ${body}
</svg>`;

  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

/* ------------------------------------------------------------------ */
/* Product thumbnails — abstract package silhouettes, one per category  */
/* ------------------------------------------------------------------ */

const SHAPES = {
  bottle: `<path d="M56 30h24v14c0 4 2 6 5 9 5 5 8 10 8 18v45c0 6-4 10-10 10H53c-6 0-10-4-10-10V71c0-8 3-13 8-18 3-3 5-5 5-9V30z" fill="#1D4066"/><rect x="52" y="26" width="32" height="10" rx="2" fill="#0B1F33"/><rect x="45" y="80" width="46" height="26" rx="2" fill="#F6F8FB" opacity="0.9"/>`,
  pouch: `<path d="M36 34h64c3 0 5 2 5 5l-5 82c0 3-3 5-6 5H42c-3 0-6-2-6-5l-5-82c0-3 2-5 5-5z" fill="#1D4066"/><rect x="36" y="28" width="64" height="10" rx="2" fill="#0B1F33"/><rect x="44" y="66" width="48" height="24" rx="2" fill="#F6F8FB" opacity="0.9"/>`,
  carton: `<path d="M40 46h56v76c0 3-2 5-5 5H45c-3 0-5-2-5-5V46z" fill="#1D4066"/><path d="M40 46l28-18 28 18-28 12-28-12z" fill="#25507E"/><rect x="48" y="74" width="40" height="22" rx="2" fill="#F6F8FB" opacity="0.9"/>`,
  sack: `<path d="M42 44c0-4 4-8 10-9l16-3 16 3c6 1 10 5 10 9v70c0 5-4 9-9 9H51c-5 0-9-4-9-9V44z" fill="#1D4066"/><path d="M56 32h24v8H56z" fill="#0B1F33"/><rect x="50" y="72" width="36" height="24" rx="2" fill="#F6F8FB" opacity="0.9"/>`,
  box: `<rect x="36" y="42" width="64" height="82" rx="4" fill="#1D4066"/><rect x="36" y="42" width="64" height="18" rx="4" fill="#25507E"/><rect x="46" y="76" width="44" height="24" rx="2" fill="#F6F8FB" opacity="0.9"/>`,
  can: `<rect x="46" y="34" width="44" height="94" rx="8" fill="#1D4066"/><ellipse cx="68" cy="36" rx="22" ry="7" fill="#25507E"/><rect x="46" y="74" width="44" height="24" fill="#F6F8FB" opacity="0.9"/>`,
  tube: `<path d="M50 40h36v76c0 5-4 9-9 9H59c-5 0-9-4-9-9V40z" fill="#1D4066"/><rect x="58" y="26" width="20" height="16" rx="3" fill="#0B1F33"/><rect x="50" y="72" width="36" height="22" fill="#F6F8FB" opacity="0.9"/>`,
};

export const CATEGORY_SHAPE = {
  'Edible Oils': 'bottle',
  'Bakery & Biscuits': 'pouch',
  'Packaged Water': 'bottle',
  'Personal Care': 'tube',
  'Staples & Grains': 'sack',
  Confectionery: 'box',
  'Dairy Products': 'carton',
  Beverages: 'can',
  'Spices & Masala': 'pouch',
  'Household Care': 'bottle',
};

/** Flat package thumbnail as a data URI — no photography, no illustration clutter. */
export function productThumb(category = 'Staples & Grains') {
  const shape = SHAPES[CATEGORY_SHAPE[category] || 'box'];
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="136" height="156" viewBox="0 0 136 156">
  <rect width="136" height="156" fill="#EEF1F5"/>
  <rect x="0.5" y="0.5" width="135" height="155" fill="none" stroke="#E4E7EC"/>
  ${shape}
  <rect x="0" y="132" width="136" height="24" fill="#E4E8EE"/>
</svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}
