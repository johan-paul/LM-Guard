import 'enums.dart';
import 'product.dart';

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

/// Pixel region of the package image a piece of evidence points to, in the
/// original photo's own coordinate space - a display widget must scale this
/// against the image's natural size, not whatever size it happens to render
/// at.
class AiBoundingBox {
  const AiBoundingBox({
    required this.x,
    required this.y,
    required this.width,
    required this.height,
  });

  final double x;
  final double y;
  final double width;
  final double height;
}

/// One rule violation the backend's rule engine raised from the AI's reading
/// of the package - the evidence behind a finding, not something the officer
/// captured. [boundingBox] is null when the violation is an absence (nothing
/// on the package to point a rectangle at, e.g. a missing declaration) rather
/// than a present-but-wrong value.
class AiViolationEvidence {
  const AiViolationEvidence({
    required this.ruleCode,
    required this.fieldName,
    required this.finding,
    required this.severity,
    this.remediation,
    this.confidence = 0,
    this.boundingBox,
  });

  final String ruleCode;
  final String fieldName;
  final String finding;
  /// Raw backend value (MINOR/MAJOR/CRITICAL) - kept as-is rather than mapped
  /// onto the app's own 4-value Severity enum, which uses a different wire
  /// vocabulary (LOW/MEDIUM/HIGH/CRITICAL) and would need a lossy guess at
  /// the mapping between the two.
  final String severity;
  final String? remediation;
  final double confidence;
  final AiBoundingBox? boundingBox;
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
    this.identifiedProduct,
    this.packageImageUrl,
    this.violations = const <AiViolationEvidence>[],
    this.suggestedFinalStatus,
    this.warnings = const <String>[],
  });

  final String inspectionId;
  final AiEvaluationStatus evaluationStatus;
  final List<AiChecklistResult> checklistResults;
  final DateTime? evaluatedAt;
  final String? modelVersion;
  final String? message;

  /// The product the backend identified from the package photo itself - there
  /// is no separate manual identification step any more. Null only while no
  /// analysis has completed yet.
  final Product? identifiedProduct;

  /// The analysed package photo - what [violations]' bounding boxes are
  /// drawn over.
  final String? packageImageUrl;

  /// The rule engine's overall verdict (COMPLIANT/NON_COMPLIANT/
  /// INCONCLUSIVE) - purely advisory, pre-filling the review step's final
  /// decision so the officer starts from the AI's determination rather than
  /// a blank choice. Never becomes the inspection's authoritative outcome by
  /// itself; only the officer's own submit does that.
  final String? suggestedFinalStatus;

  /// Every rule violation the rule engine raised, each carrying its own
  /// evidence region - this is what the Evidence step renders. The AI/rule
  /// engine produces this; nothing here is captured by the officer.
  final List<AiViolationEvidence> violations;

  /// Non-fatal problems from this specific analysis run worth telling the
  /// officer about directly - most importantly the semantic (VLM) step being
  /// unavailable (rate-limited, quota exhausted, network failure), in which
  /// case free-text fields like manufacturer name could not be identified at
  /// all and every other field fell back to OCR pattern matching only. That
  /// is a materially weaker result than usual, not a random accuracy dip, so
  /// it must not be silent.
  final List<String> warnings;

  int get flaggedCount =>
      checklistResults.where((AiChecklistResult r) => r.aiSuggestedStatus == CheckResult.nonCompliant).length;

  int get lowConfidenceCount =>
      checklistResults.where((AiChecklistResult r) => r.confidenceScore < 0.7).length;
}
