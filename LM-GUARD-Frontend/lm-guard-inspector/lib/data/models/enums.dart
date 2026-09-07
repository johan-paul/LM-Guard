/// Workflow vocabularies shared by the inspector application.
///
/// `wireValue` is the string the backend contract uses, so these enums can be
/// serialised directly once the API is connected.

enum InspectionStatus {
  assigned('ASSIGNED', 'Assigned'),
  inProgress('IN_PROGRESS', 'In Progress'),
  draft('DRAFT', 'Draft'),
  submitted('SUBMITTED', 'Submitted'),
  completed('COMPLETED', 'Completed');

  const InspectionStatus(this.wireValue, this.label);

  final String wireValue;
  final String label;

  bool get isOpenWork =>
      this == InspectionStatus.assigned ||
      this == InspectionStatus.inProgress ||
      this == InspectionStatus.draft;

  bool get isClosed =>
      this == InspectionStatus.submitted || this == InspectionStatus.completed;

  static InspectionStatus fromWire(String value) {
    return InspectionStatus.values.firstWhere(
      (InspectionStatus status) => status.wireValue == value,
      orElse: () => InspectionStatus.assigned,
    );
  }
}

enum Priority {
  low('LOW', 'Low'),
  medium('MEDIUM', 'Medium'),
  high('HIGH', 'High');

  const Priority(this.wireValue, this.label);

  final String wireValue;
  final String label;

  static Priority fromWire(String value) {
    return Priority.values.firstWhere(
      (Priority priority) => priority.wireValue == value,
      orElse: () => Priority.medium,
    );
  }
}

enum Severity {
  low('LOW', 'Low'),
  medium('MEDIUM', 'Medium'),
  high('HIGH', 'High'),
  critical('CRITICAL', 'Critical');

  const Severity(this.wireValue, this.label);

  final String wireValue;
  final String label;

  static Severity fromWire(String value) {
    return Severity.values.firstWhere(
      (Severity severity) => severity.wireValue == value,
      orElse: () => Severity.medium,
    );
  }
}

/// Outcome recorded against a single checklist line.
enum CheckResult {
  pending('PENDING', 'Not checked'),
  compliant('COMPLIANT', 'Compliant'),
  nonCompliant('NON_COMPLIANT', 'Non-compliant'),
  notApplicable('NOT_APPLICABLE', 'Not applicable');

  const CheckResult(this.wireValue, this.label);

  final String wireValue;
  final String label;
}

enum FindingStatus {
  open('OPEN', 'Open'),
  resolved('RESOLVED', 'Resolved');

  const FindingStatus(this.wireValue, this.label);

  final String wireValue;
  final String label;
}

enum EvidenceKind {
  photo('PHOTO', 'Photograph'),
  document('DOCUMENT', 'Document'),
  note('NOTE', 'Field note');

  const EvidenceKind(this.wireValue, this.label);

  final String wireValue;
  final String label;
}

enum InspectionType {
  routine('ROUTINE', 'Routine market inspection'),
  complaint('COMPLAINT', 'Consumer complaint'),
  followUp('FOLLOW_UP', 'Follow-up verification'),
  drive('DRIVE', 'Special enforcement drive');

  const InspectionType(this.wireValue, this.label);

  final String wireValue;
  final String label;
}

/// The inspector's own final decision on the inspection - independent of
/// whatever the AI/rule engine suggested. Maps directly onto the backend's
/// verdict states (the only three that are actual compliance verdicts).
enum FinalDecision {
  compliant('COMPLIANT', 'Compliant'),
  nonCompliant('NON_COMPLIANT', 'Non-compliant'),
  inconclusive('INCONCLUSIVE', 'Inconclusive');

  const FinalDecision(this.wireValue, this.label);

  final String wireValue;
  final String label;
}

/// State of the advisory AI evaluation panel embedded in the compliance
/// checklist step. Never controls [CheckResult] directly - the inspector's
/// own decision on each line is what gets recorded.
enum AiEvaluationStatus {
  idle('IDLE', 'Idle'),
  pending('PENDING', 'Pending'),
  processing('PROCESSING', 'Processing'),
  completed('COMPLETED', 'Completed'),
  failed('FAILED', 'Failed');

  const AiEvaluationStatus(this.wireValue, this.label);

  final String wireValue;
  final String label;
}
