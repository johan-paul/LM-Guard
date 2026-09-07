import 'package:flutter/foundation.dart';

import '../data/models/ai_evaluation.dart';
import '../data/models/checklist_item.dart';
import '../data/models/enums.dart';
import '../data/models/evidence.dart';
import '../data/models/finding.dart';
import '../data/models/inspection.dart';
import '../data/models/product.dart';
import '../data/models/product_history.dart';
import '../data/repositories/inspection_repository.dart';
import '../data/services/evidence_service.dart';

/// The six recorded stages of a field inspection.
enum InspectionStep {
  information('Inspection information'),
  product('Product identification'),
  checklist('Compliance checklist'),
  evidence('Evidence capture'),
  findings('Findings & violations'),
  review('Review & submit');

  const InspectionStep(this.title);

  final String title;

  int get number => index + 1;
}

/// Working copy of one inspection while an officer is in the field.
///
/// Everything the wizard edits lives here, so a step widget stays presentation
/// only and the draft survives navigation between steps.
class DraftController extends ChangeNotifier {
  DraftController({
    required Inspection inspection,
    required InspectionRepository repository,
    required EvidenceService evidenceService,
    InspectionStep initialStep = InspectionStep.information,
  })  : _inspection = inspection,
        _repository = repository,
        _evidenceService = evidenceService,
        _step = initialStep;

  final InspectionRepository _repository;
  final EvidenceService _evidenceService;

  Inspection _inspection;
  InspectionStep _step;
  bool _busy = false;
  bool _dirty = false;

  AIEvaluation? _aiEvaluation;
  AiEvaluationStatus _aiStatus = AiEvaluationStatus.idle;
  String? _aiError;

  Inspection get inspection => _inspection;
  InspectionStep get step => _step;
  bool get busy => _busy;
  bool get dirty => _dirty;

  AIEvaluation? get aiEvaluation => _aiEvaluation;
  AiEvaluationStatus get aiStatus => _aiStatus;
  String? get aiError => _aiError;

  int get stepNumber => _step.number;
  int get stepCount => InspectionStep.values.length;
  bool get isFirstStep => _step.index == 0;
  bool get isLastStep => _step.index == InspectionStep.values.length - 1;

  /* ---------------- Navigation ---------------- */

  void goTo(InspectionStep step) {
    if (_step == step) return;
    _step = step;
    notifyListeners();
  }

  void next() {
    if (isLastStep) return;
    _step = InspectionStep.values[_step.index + 1];
    notifyListeners();
  }

  void back() {
    if (isFirstStep) return;
    _step = InspectionStep.values[_step.index - 1];
    notifyListeners();
  }

  /* ---------------- Step 1: information ---------------- */

  void updateInformation({
    String? establishment,
    String? location,
    String? zone,
    InspectionType? type,
    Priority? priority,
    DateTime? dueOn,
  }) {
    _inspection = _inspection.copyWith(
      establishment: establishment,
      location: location,
      zone: zone,
      type: type,
      priority: priority,
      dueOn: dueOn,
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  bool get informationComplete =>
      _inspection.establishment.trim().isNotEmpty &&
      _inspection.location.trim().isNotEmpty;

  /* ---------------- Step 2: product ---------------- */

  /// Attaches the identified product both locally and on the backend - the
  /// server needs to know the product before AI analysis or product history
  /// can be requested for this inspection.
  Future<void> setProduct(Product product) async {
    _inspection = _inspection.copyWith(product: product, updatedAt: DateTime.now());
    _markDirty();
    try {
      _inspection = await _repository.identifyProduct(_inspection.id, product);
      notifyListeners();
    } catch (_) {
      // Local state already reflects the choice; the next saveDraft()/submit()
      // retries the sync. Nothing the product step needs to react to here.
    }
  }

  /// Product History for the identified product - previous inspections,
  /// violations and repeats. Throws if no product has been identified yet.
  Future<ProductHistorySummary> fetchProductHistory() {
    final Product? product = _inspection.product;
    if (product == null) {
      throw StateError('Identify a product before requesting its history');
    }
    return _repository.fetchProductHistory(product.id);
  }

  /// Drops the identified commodity so the officer can select another.
  void clearProduct() {
    _inspection = _inspection.copyWith(
      clearProduct: true,
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  Future<List<Product>> searchProducts(String query) =>
      _repository.searchProducts(query);

  Future<Product?> lookupBarcode(String barcode) =>
      _repository.lookupBarcode(barcode);

  bool get productIdentified => _inspection.product != null;

  /* ---------------- Step 3: checklist ---------------- */

  void setCheckResult(String itemId, CheckResult result) {
    final List<ChecklistItem> updated = _inspection.checklist
        .map((ChecklistItem item) =>
            item.id == itemId ? item.copyWith(result: result) : item)
        .toList();
    _inspection = _inspection.copyWith(
      checklist: updated,
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  void setCheckNote(String itemId, String note) {
    final List<ChecklistItem> updated = _inspection.checklist
        .map((ChecklistItem item) =>
            item.id == itemId ? item.copyWith(note: note) : item)
        .toList();
    _inspection = _inspection.copyWith(
      checklist: updated,
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  bool get checklistComplete =>
      _inspection.checklist.isNotEmpty &&
      _inspection.checklist.every((ChecklistItem item) => item.isAnswered);

  /// Runs the backend's AI/rule-engine analysis and stores it as an advisory
  /// suggestion per checklist line. Never writes to the checklist itself -
  /// the inspector must call [acceptAiSuggestion] (or decide manually) for
  /// that.
  Future<void> runAiEvaluation() async {
    final Product? product = _inspection.product;
    if (product == null) return;

    _aiStatus = AiEvaluationStatus.processing;
    _aiError = null;
    notifyListeners();
    try {
      _aiEvaluation = await _repository.runAiEvaluation(_inspection.id, _inspection.checklist);
      _aiStatus = _aiEvaluation!.evaluationStatus;
      if (_aiStatus == AiEvaluationStatus.failed) {
        _aiError = _aiEvaluation!.message;
      }
    } catch (exception) {
      _aiStatus = AiEvaluationStatus.failed;
      _aiError = 'The AI evaluation could not be completed.';
    } finally {
      notifyListeners();
    }
  }

  /// Copies the AI's suggestion for one checklist line (identified by the
  /// item's own id, matching [setCheckResult]'s convention) onto the
  /// inspector's own decision. A one-way convenience, not automatic: the
  /// inspector still took the action, and can just as easily record
  /// something different.
  void acceptAiSuggestion(String itemId) {
    final ChecklistItem? item = _inspection.checklist
        .cast<ChecklistItem?>()
        .firstWhere((ChecklistItem? i) => i?.id == itemId, orElse: () => null);
    if (item == null || _aiEvaluation == null) return;

    final AiChecklistResult? suggestion = _aiEvaluation!.checklistResults
        .cast<AiChecklistResult?>()
        .firstWhere((AiChecklistResult? r) => r?.ruleId == item.ruleRef, orElse: () => null);
    if (suggestion == null) return;
    setCheckResult(itemId, suggestion.aiSuggestedStatus);
  }

  /// The AI's suggestion for the checklist item with this rule reference, if
  /// an evaluation has completed.
  AiChecklistResult? aiResultFor(String ruleRef) {
    return _aiEvaluation?.checklistResults
        .cast<AiChecklistResult?>()
        .firstWhere((AiChecklistResult? r) => r?.ruleId == ruleRef, orElse: () => null);
  }

  /* ---------------- Step 4: evidence ---------------- */

  Future<void> captureEvidence({
    required String label,
    EvidenceKind kind = EvidenceKind.photo,
    bool fromGallery = false,
  }) async {
    _busy = true;
    notifyListeners();
    try {
      final EvidenceItem? captured = fromGallery
          ? await _evidenceService.pickFromGallery(label: label)
          : await _evidenceService.capture(label: label, kind: kind);
      if (captured != null) {
        // A real on-device file (the camera/gallery service, not the mock
        // placeholder one) is uploaded immediately - the id the rest of the
        // app uses becomes whatever the backend assigned it.
        EvidenceItem resolved = captured;
        if (captured.filePath != null) {
          try {
            resolved = await _repository.uploadEvidence(_inspection.id, captured);
          } catch (_) {
            // Keep the locally-captured item even if the upload failed; the
            // officer hasn't lost the photo, and it isn't retried here.
          }
        }
        _inspection = _inspection.copyWith(
          evidence: <EvidenceItem>[..._inspection.evidence, resolved],
          updatedAt: DateTime.now(),
        );
        _dirty = true;
      }
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  void updateEvidence(String id, {String? label, String? description}) {
    final List<EvidenceItem> updated = _inspection.evidence
        .map((EvidenceItem item) => item.id == id
            ? item.copyWith(label: label, description: description)
            : item)
        .toList();
    _inspection = _inspection.copyWith(
      evidence: updated,
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  void removeEvidence(String id) {
    _inspection = _inspection.copyWith(
      evidence: _inspection.evidence
          .where((EvidenceItem item) => item.id != id)
          .toList(),
      findings: _inspection.findings
          .map((Finding finding) => finding.copyWith(
                evidenceIds: finding.evidenceIds
                    .where((String ref) => ref != id)
                    .toList(),
              ))
          .toList(),
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  /* ---------------- Step 5: findings ---------------- */

  Future<List<RuleReference>> fetchRules() => _repository.fetchRules();

  void addFinding({
    required String ruleRef,
    required String ruleName,
    required Severity severity,
    required String description,
    String note = '',
    List<String> evidenceIds = const <String>[],
  }) {
    final int sequence = _inspection.findings.length + 1;
    final Finding finding = Finding(
      id: 'FND-${DateTime.now().millisecondsSinceEpoch % 100000}',
      sequence: sequence,
      ruleRef: ruleRef,
      ruleName: ruleName,
      severity: severity,
      description: description,
      note: note,
      evidenceIds: evidenceIds,
    );
    _inspection = _inspection.copyWith(
      findings: <Finding>[..._inspection.findings, finding],
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  void removeFinding(String id) {
    final List<Finding> remaining = _inspection.findings
        .where((Finding finding) => finding.id != id)
        .toList();
    // Keep the printed sequence contiguous after a removal.
    final List<Finding> resequenced = <Finding>[];
    for (int i = 0; i < remaining.length; i++) {
      final Finding finding = remaining[i];
      resequenced.add(
        Finding(
          id: finding.id,
          sequence: i + 1,
          ruleRef: finding.ruleRef,
          ruleName: finding.ruleName,
          severity: finding.severity,
          description: finding.description,
          status: finding.status,
          note: finding.note,
          evidenceIds: finding.evidenceIds,
        ),
      );
    }
    _inspection = _inspection.copyWith(
      findings: resequenced,
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  /// Checklist lines marked non-compliant with no finding raised against them.
  List<ChecklistItem> get unreconciledFailures {
    final Set<String> cited =
        _inspection.findings.map((Finding f) => f.ruleRef).toSet();
    return _inspection.checklist
        .where((ChecklistItem item) => item.isFailed && !cited.contains(item.ruleRef))
        .toList();
  }

  /* ---------------- Step 6: review ---------------- */

  void setOfficerNotes(String notes) {
    _inspection = _inspection.copyWith(
      officerNotes: notes,
      updatedAt: DateTime.now(),
    );
    _markDirty();
  }

  /// The inspector's own, final compliance verdict - independent of
  /// whatever the AI suggested. Required before [submit] is allowed.
  void setFinalDecision(FinalDecision decision) {
    _inspection = _inspection.copyWith(finalDecision: decision, updatedAt: DateTime.now());
    _markDirty();
  }

  bool get readyToSubmit =>
      informationComplete && productIdentified && checklistComplete && _inspection.finalDecision != null;

  Future<Inspection> saveDraft() async {
    _busy = true;
    notifyListeners();
    try {
      _inspection = await _repository.saveDraft(_inspection);
      _dirty = false;
      return _inspection;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<Inspection> submit() async {
    _busy = true;
    notifyListeners();
    try {
      _inspection = await _repository.submit(_inspection);
      _dirty = false;
      return _inspection;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  void _markDirty() {
    _dirty = true;
    notifyListeners();
  }
}
