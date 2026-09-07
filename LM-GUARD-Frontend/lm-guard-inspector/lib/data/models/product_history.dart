/// One prior completed inspection of the product under inspection now.
class PreviousInspectionEntry {
  const PreviousInspectionEntry({
    required this.inspectionId,
    required this.inspectedOn,
    required this.result,
    this.establishment,
    this.inspectorName,
    this.violationCount = 0,
  });

  final String inspectionId;
  final DateTime inspectedOn;

  /// "Compliant" / "Non-compliant" / "Inconclusive" - already a display label,
  /// not a wire enum, since it comes straight off the backend's verdict.
  final String result;
  final String? establishment;
  final String? inspectorName;
  final int violationCount;
}

/// Aggregated inspection/violation history for the product under inspection -
/// what the Product History step shows before the inspector records their own
/// findings. Complaint history is not tracked anywhere in this system yet
/// (no consumer-complaint intake exists in LM-GUARD) so it is deliberately
/// absent here rather than fabricated.
class ProductHistorySummary {
  const ProductHistorySummary({
    required this.previousInspections,
    required this.compliantCount,
    required this.nonCompliantCount,
    required this.inconclusiveCount,
    required this.previousViolations,
    required this.repeatViolations,
    this.riskLevel,
    this.recentInspections = const <PreviousInspectionEntry>[],
  });

  final int previousInspections;
  final int compliantCount;
  final int nonCompliantCount;
  final int inconclusiveCount;
  final int previousViolations;
  final int repeatViolations;
  final String? riskLevel;
  final List<PreviousInspectionEntry> recentInspections;

  bool get hasRepeatIssue => repeatViolations > 0;

  String get riskLabel {
    switch (riskLevel) {
      case 'HIGH':
        return 'High';
      case 'MEDIUM':
        return 'Medium';
      case 'LOW':
        return 'Low';
      default:
        return 'Not assessed';
    }
  }
}
