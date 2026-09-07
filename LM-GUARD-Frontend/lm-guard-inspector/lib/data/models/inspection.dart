import 'checklist_item.dart';
import 'enums.dart';
import 'evidence.dart';
import 'finding.dart';
import 'product.dart';

/// A field inspection assignment and everything recorded against it.
class Inspection {
  const Inspection({
    required this.id,
    required this.establishment,
    required this.location,
    required this.zone,
    required this.type,
    required this.priority,
    required this.status,
    required this.assignedOn,
    required this.dueOn,
    required this.updatedAt,
    this.product,
    this.checklist = const <ChecklistItem>[],
    this.findings = const <Finding>[],
    this.evidence = const <EvidenceItem>[],
    this.officerNotes = '',
    this.submittedAt,
    this.finalDecision,
  });

  final String id;
  final String establishment;
  final String location;
  final String zone;
  final InspectionType type;
  final Priority priority;
  final InspectionStatus status;
  final DateTime assignedOn;
  final DateTime dueOn;
  final DateTime updatedAt;
  final Product? product;
  final List<ChecklistItem> checklist;
  final List<Finding> findings;
  final List<EvidenceItem> evidence;
  final String officerNotes;
  final DateTime? submittedAt;

  /// The inspector's own, final compliance verdict - independent of any AI
  /// suggestion. Must be set before submission; the review step gates on it.
  final FinalDecision? finalDecision;

  String get productName => product?.name ?? 'Product not identified';

  int get answeredChecks =>
      checklist.where((ChecklistItem item) => item.isAnswered).length;

  int get failedChecks =>
      checklist.where((ChecklistItem item) => item.isFailed).length;

  int get openFindings => findings
      .where((Finding finding) => finding.status == FindingStatus.open)
      .length;

  /// Completion across the four recorded stages: product identified,
  /// checklist answered, evidence captured, findings reconciled.
  int get progressPercent {
    if (status == InspectionStatus.submitted ||
        status == InspectionStatus.completed) {
      return 100;
    }

    final double checklistShare = checklist.isEmpty
        ? 0
        : answeredChecks / checklist.length;

    double score = 0;
    if (product != null) score += 0.25;
    score += checklistShare * 0.45;
    if (evidence.isNotEmpty) score += 0.20;
    if (failedChecks == 0 || findings.isNotEmpty) score += 0.10;

    return (score * 100).round().clamp(0, 100);
  }

  bool get isOverdue =>
      status.isOpenWork && DateTime.now().isAfter(dueOn) && !_isSameDay(dueOn);

  static bool _isSameDay(DateTime value) {
    final DateTime now = DateTime.now();
    return value.year == now.year &&
        value.month == now.month &&
        value.day == now.day;
  }

  Inspection copyWith({
    String? establishment,
    String? location,
    String? zone,
    InspectionType? type,
    Priority? priority,
    InspectionStatus? status,
    DateTime? dueOn,
    DateTime? updatedAt,
    Product? product,
    List<ChecklistItem>? checklist,
    List<Finding>? findings,
    List<EvidenceItem>? evidence,
    String? officerNotes,
    DateTime? submittedAt,
    bool clearProduct = false,
    FinalDecision? finalDecision,
  }) {
    return Inspection(
      id: id,
      establishment: establishment ?? this.establishment,
      location: location ?? this.location,
      zone: zone ?? this.zone,
      type: type ?? this.type,
      priority: priority ?? this.priority,
      status: status ?? this.status,
      assignedOn: assignedOn,
      dueOn: dueOn ?? this.dueOn,
      updatedAt: updatedAt ?? this.updatedAt,
      product: clearProduct ? null : (product ?? this.product),
      checklist: checklist ?? this.checklist,
      findings: findings ?? this.findings,
      evidence: evidence ?? this.evidence,
      officerNotes: officerNotes ?? this.officerNotes,
      submittedAt: submittedAt ?? this.submittedAt,
      finalDecision: finalDecision ?? this.finalDecision,
    );
  }

  factory Inspection.fromJson(Map<String, dynamic> json) {
    return Inspection(
      id: json['id'] as String,
      establishment: json['establishment'] as String? ?? '',
      location: json['location'] as String? ?? '',
      zone: json['zone'] as String? ?? '',
      type: InspectionType.values.firstWhere(
        (InspectionType value) => value.wireValue == json['type'],
        orElse: () => InspectionType.routine,
      ),
      priority: Priority.fromWire(json['priority'] as String? ?? 'MEDIUM'),
      status: InspectionStatus.fromWire(
        json['status'] as String? ?? 'ASSIGNED',
      ),
      assignedOn: DateTime.parse(json['assignedOn'] as String),
      dueOn: DateTime.parse(json['dueOn'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
      product: json['product'] == null
          ? null
          : Product.fromJson(json['product'] as Map<String, dynamic>),
      checklist: (json['checklist'] as List<dynamic>? ?? <dynamic>[])
          .map((dynamic item) =>
              ChecklistItem.fromJson(item as Map<String, dynamic>))
          .toList(),
      findings: (json['findings'] as List<dynamic>? ?? <dynamic>[])
          .map((dynamic item) => Finding.fromJson(item as Map<String, dynamic>))
          .toList(),
      evidence: (json['evidence'] as List<dynamic>? ?? <dynamic>[])
          .map((dynamic item) =>
              EvidenceItem.fromJson(item as Map<String, dynamic>))
          .toList(),
      officerNotes: json['officerNotes'] as String? ?? '',
      submittedAt: json['submittedAt'] == null
          ? null
          : DateTime.parse(json['submittedAt'] as String),
      finalDecision: json['finalDecision'] == null
          ? null
          : FinalDecision.values.firstWhere(
              (FinalDecision d) => d.wireValue == json['finalDecision'],
              orElse: () => FinalDecision.inconclusive,
            ),
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'establishment': establishment,
        'location': location,
        'zone': zone,
        'type': type.wireValue,
        'priority': priority.wireValue,
        'status': status.wireValue,
        'assignedOn': assignedOn.toIso8601String(),
        'dueOn': dueOn.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
        'product': product?.toJson(),
        'checklist': checklist.map((ChecklistItem i) => i.toJson()).toList(),
        'findings': findings.map((Finding f) => f.toJson()).toList(),
        'evidence': evidence.map((EvidenceItem e) => e.toJson()).toList(),
        'officerNotes': officerNotes,
        'submittedAt': submittedAt?.toIso8601String(),
        'finalDecision': finalDecision?.wireValue,
      };
}
