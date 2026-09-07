import 'enums.dart';

/// One line of the statutory declaration checklist.
class ChecklistItem {
  const ChecklistItem({
    required this.id,
    required this.ruleRef,
    required this.title,
    required this.guidance,
    this.result = CheckResult.pending,
    this.note = '',
  });

  final String id;
  final String ruleRef;
  final String title;
  final String guidance;
  final CheckResult result;
  final String note;

  bool get isAnswered => result != CheckResult.pending;
  bool get isFailed => result == CheckResult.nonCompliant;

  ChecklistItem copyWith({CheckResult? result, String? note}) {
    return ChecklistItem(
      id: id,
      ruleRef: ruleRef,
      title: title,
      guidance: guidance,
      result: result ?? this.result,
      note: note ?? this.note,
    );
  }

  factory ChecklistItem.fromJson(Map<String, dynamic> json) {
    return ChecklistItem(
      id: json['id'] as String,
      ruleRef: json['ruleRef'] as String? ?? '',
      title: json['title'] as String,
      guidance: json['guidance'] as String? ?? '',
      result: CheckResult.values.firstWhere(
        (CheckResult value) => value.wireValue == json['result'],
        orElse: () => CheckResult.pending,
      ),
      note: json['note'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'ruleRef': ruleRef,
        'title': title,
        'guidance': guidance,
        'result': result.wireValue,
        'note': note,
      };
}
