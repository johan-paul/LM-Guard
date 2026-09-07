import 'enums.dart';

/// A recorded non-compliance raised by the inspecting officer.
class Finding {
  const Finding({
    required this.id,
    required this.sequence,
    required this.ruleRef,
    required this.ruleName,
    required this.severity,
    required this.description,
    this.status = FindingStatus.open,
    this.note = '',
    this.evidenceIds = const <String>[],
  });

  final String id;
  final int sequence;
  final String ruleRef;
  final String ruleName;
  final Severity severity;
  final String description;
  final FindingStatus status;
  final String note;
  final List<String> evidenceIds;

  String get reference => 'FINDING #${sequence.toString().padLeft(2, '0')}';

  Finding copyWith({
    String? ruleRef,
    String? ruleName,
    Severity? severity,
    String? description,
    FindingStatus? status,
    String? note,
    List<String>? evidenceIds,
  }) {
    return Finding(
      id: id,
      sequence: sequence,
      ruleRef: ruleRef ?? this.ruleRef,
      ruleName: ruleName ?? this.ruleName,
      severity: severity ?? this.severity,
      description: description ?? this.description,
      status: status ?? this.status,
      note: note ?? this.note,
      evidenceIds: evidenceIds ?? this.evidenceIds,
    );
  }

  factory Finding.fromJson(Map<String, dynamic> json) {
    return Finding(
      id: json['id'] as String,
      sequence: json['sequence'] as int? ?? 1,
      ruleRef: json['ruleRef'] as String? ?? '',
      ruleName: json['ruleName'] as String? ?? '',
      severity: Severity.fromWire(json['severity'] as String? ?? 'MEDIUM'),
      description: json['description'] as String? ?? '',
      status: FindingStatus.values.firstWhere(
        (FindingStatus value) => value.wireValue == json['status'],
        orElse: () => FindingStatus.open,
      ),
      note: json['note'] as String? ?? '',
      evidenceIds: (json['evidenceIds'] as List<dynamic>? ?? <dynamic>[])
          .map((dynamic value) => value as String)
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'sequence': sequence,
        'ruleRef': ruleRef,
        'ruleName': ruleName,
        'severity': severity.wireValue,
        'description': description,
        'status': status.wireValue,
        'note': note,
        'evidenceIds': evidenceIds,
      };
}

/// Reference list of rules an officer can cite when raising a finding.
class RuleReference {
  const RuleReference(this.ref, this.name, this.category);

  final String ref;
  final String name;
  final String category;
}
