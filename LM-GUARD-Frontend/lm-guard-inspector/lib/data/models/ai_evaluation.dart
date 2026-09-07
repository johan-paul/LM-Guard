import 'enums.dart';

/// The rule engine's suggestion for one checklist line - advisory only. The
/// inspector's own [CheckResult] on that line is a separate, independent
/// field the inspector sets themselves; accepting a suggestion just copies
/// this value onto it, it is never written automatically.
class AiChecklistResult {
  const AiChecklistResult({
    required this.ruleId,
    required this.ruleName,
    required this.aiSuggestedStatus,
    required this.confidenceScore,
    required this.explanation,
    this.detectedIssue,
  });

  final String ruleId;
  final String ruleName;
  final CheckResult aiSuggestedStatus;
  final double confidenceScore;
  final String explanation;
  final String? detectedIssue;
}

/// Result of running the backend's AI/rule-engine analysis for the package
/// image attached to an inspection. [checklistResults] is derived client-side
/// from the backend's violations list, matched to the checklist by rule
/// reference - the backend has no checklist concept of its own.
class AIEvaluation {
  const AIEvaluation({
    required this.inspectionId,
    required this.evaluationStatus,
    this.checklistResults = const <AiChecklistResult>[],
    this.evaluatedAt,
    this.modelVersion,
    this.message,
  });

  final String inspectionId;
  final AiEvaluationStatus evaluationStatus;
  final List<AiChecklistResult> checklistResults;
  final DateTime? evaluatedAt;
  final String? modelVersion;
  final String? message;

  int get flaggedCount =>
      checklistResults.where((AiChecklistResult r) => r.aiSuggestedStatus == CheckResult.nonCompliant).length;

  int get lowConfidenceCount =>
      checklistResults.where((AiChecklistResult r) => r.confidenceScore < 0.7).length;
}
