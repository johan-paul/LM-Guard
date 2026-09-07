import jsPDF from 'jspdf';

/* Palette mirrored from the design system so the export reads as the same product. */
const NAVY = [11, 31, 51];
const INK = [23, 32, 51];
const MUTED = [102, 112, 133];
const LINE = [228, 231, 236];
const SUCCESS = [22, 163, 74];
const WARNING = [217, 119, 6];
const DANGER = [220, 38, 38];

const STATUS_TEXT = {
  COMPLIANT: 'COMPLIANT',
  NON_COMPLIANT: 'VIOLATION',
  REVIEW_REQUIRED: 'REVIEW',
  INCONCLUSIVE: 'INCONCLUSIVE',
  VIOLATION: 'VIOLATION',
};

const statusColor = (status) => {
  if (status === 'COMPLIANT') return SUCCESS;
  if (status === 'NON_COMPLIANT' || status === 'VIOLATION') return DANGER;
  if (status === 'REVIEW_REQUIRED' || status === 'REVIEW') return WARNING;
  return MUTED;
};

/**
 * Generates a structured compliance report for an inspection record.
 * Text-only by design: the report is a summary of findings, and evidence stays
 * in the platform where its provenance is preserved.
 */
export function generateInspectionPDF(inspection, inspectorName = 'Inspecting Officer') {
  const doc = new jsPDF({ unit: 'pt', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const M = 48;
  let y = 0;

  const line = (yPos) => {
    doc.setDrawColor(...LINE);
    doc.setLineWidth(0.75);
    doc.line(M, yPos, pageWidth - M, yPos);
  };

  const ensureSpace = (needed) => {
    if (y + needed > pageHeight - 70) {
      doc.addPage();
      y = M;
    }
  };

  /* ---------- Masthead ---------- */
  doc.setFillColor(...NAVY);
  doc.rect(0, 0, pageWidth, 92, 'F');

  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(17);
  doc.text('LM-GUARD', M, 40);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  doc.setTextColor(180, 198, 220);
  doc.text('AI-Assisted Legal Metrology · Compliance Report', M, 56);

  doc.setFontSize(8);
  doc.text(`Ruleset ${inspection.rulesetVersion || '2026.1'}`, pageWidth - M, 40, { align: 'right' });
  doc.text(new Date().toLocaleString('en-IN'), pageWidth - M, 56, { align: 'right' });

  y = 124;

  /* ---------- Record header ---------- */
  doc.setTextColor(...INK);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(15);
  doc.text(doc.splitTextToSize(inspection.productName || 'Inspection record', pageWidth - M * 2 - 110), M, y);

  const badge = STATUS_TEXT[inspection.status] || inspection.status;
  const bc = statusColor(inspection.status);
  doc.setFillColor(bc[0], bc[1], bc[2]);
  doc.roundedRect(pageWidth - M - 92, y - 13, 92, 19, 3, 3, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8.5);
  doc.text(badge, pageWidth - M - 46, y, { align: 'center' });

  y += 22;
  doc.setFont('courier', 'normal');
  doc.setFontSize(9.5);
  doc.setTextColor(...MUTED);
  doc.text(inspection.id, M, y);

  y += 24;
  line(y);
  y += 22;

  /* ---------- Record particulars ---------- */
  const particulars = [
    ['Manufacturer', inspection.manufacturer],
    ['Category', inspection.category],
    ['Inspector', inspection.inspector],
    ['Zone', inspection.zone],
    ['Recorded', new Date(inspection.date).toLocaleString('en-IN')],
    ['Risk score', `${inspection.riskScore} / 100`],
  ];

  doc.setFontSize(9);
  particulars.forEach(([label, value], i) => {
    const col = i % 2;
    const x = M + col * ((pageWidth - M * 2) / 2);
    if (col === 0 && i > 0) y += 30;
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...MUTED);
    doc.text(String(label).toUpperCase(), x, y);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...INK);
    doc.text(doc.splitTextToSize(String(value || '—'), (pageWidth - M * 2) / 2 - 14), x, y + 13);
  });

  y += 36;
  line(y);
  y += 24;

  /* ---------- Compliance summary ---------- */
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.setTextColor(...INK);
  doc.text('Compliance summary', M, y);
  y += 16;

  doc.setFontSize(7.5);
  doc.setTextColor(...MUTED);
  doc.text('DECLARATION', M, y);
  doc.text('DETECTED VALUE', M + 150, y);
  doc.text('CONFIDENCE', pageWidth - M - 118, y);
  doc.text('OUTCOME', pageWidth - M, y, { align: 'right' });
  y += 8;
  line(y);
  y += 15;

  (inspection.declarations || []).forEach((d) => {
    ensureSpace(30);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.setTextColor(...INK);
    doc.text(doc.splitTextToSize(d.label, 140), M, y);

    doc.setTextColor(...MUTED);
    doc.text(doc.splitTextToSize(String(d.detected || '—'), 210), M + 150, y);

    doc.setTextColor(...INK);
    doc.text(`${Math.round((d.confidence || 0) * 100)}%`, pageWidth - M - 118, y);

    const c = statusColor(d.status);
    doc.setTextColor(c[0], c[1], c[2]);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8);
    doc.text(STATUS_TEXT[d.status] || d.status, pageWidth - M, y, { align: 'right' });

    y += 22;
    doc.setDrawColor(238, 241, 245);
    doc.line(M, y - 8, pageWidth - M, y - 8);
  });

  y += 10;

  /* ---------- Findings ---------- */
  const violations = inspection.violations || [];
  ensureSpace(60);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.setTextColor(...INK);
  doc.text(`Findings raised (${violations.length})`, M, y);
  y += 18;

  if (!violations.length) {
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.setTextColor(...MUTED);
    doc.text('No violations were raised against this record under the active ruleset.', M, y);
    y += 18;
  } else {
    violations.forEach((v) => {
      ensureSpace(78);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(9.5);
      doc.setTextColor(...INK);
      doc.text(doc.splitTextToSize(v.title, pageWidth - M * 2), M, y);
      y += 14;

      doc.setFont('courier', 'normal');
      doc.setFontSize(8);
      doc.setTextColor(...MUTED);
      doc.text(`${v.id} · rule ${v.ruleId} · confidence ${Math.round(v.confidence * 100)}%`, M, y);
      y += 14;

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(9);
      doc.setTextColor(...INK);
      doc.text(doc.splitTextToSize(`Detected: ${v.detectedValue}`, pageWidth - M * 2), M, y);
      y += 13;
      doc.setTextColor(...MUTED);
      doc.text(doc.splitTextToSize(`Expected: ${v.expectedValue}`, pageWidth - M * 2), M, y);
      y += 22;
    });
  }

  /* ---------- Attestation ---------- */
  ensureSpace(96);
  line(y);
  y += 20;

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(...INK);
  doc.text('Reviewed by', M, y);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...MUTED);
  doc.text(inspectorName || 'Inspecting Officer', M, y + 14);

  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...INK);
  doc.text('Signature', pageWidth - M - 180, y);
  doc.setDrawColor(...LINE);
  doc.line(pageWidth - M - 180, y + 26, pageWidth - M, y + 26);

  /* ---------- Footers ---------- */
  const pages = doc.internal.getNumberOfPages();
  for (let p = 1; p <= pages; p += 1) {
    doc.setPage(p);
    doc.setDrawColor(...LINE);
    doc.line(M, pageHeight - 46, pageWidth - M, pageHeight - 46);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7.5);
    doc.setTextColor(...MUTED);
    doc.text(
      'Generated by LM-GUARD. Findings are produced by OCR extraction and a demonstration ruleset; enforcement decisions rest with the inspecting officer.',
      M,
      pageHeight - 32,
      { maxWidth: pageWidth - M * 2 - 60 },
    );
    doc.text(`${p} / ${pages}`, pageWidth - M, pageHeight - 32, { align: 'right' });
  }

  doc.save(`${inspection.id}-compliance-report.pdf`);
}

export default generateInspectionPDF;
