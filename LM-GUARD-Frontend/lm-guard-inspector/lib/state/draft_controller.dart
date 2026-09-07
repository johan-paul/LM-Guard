import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter/widgets.dart' show BuildContext;

import '../data/models/ai_evaluation.dart';
import '../data/models/checklist_item.dart';
import '../data/models/enums.dart';
import '../data/models/evidence.dart';
import '../data/models/inspection.dart';
import '../data/models/product.dart';
import '../data/models/product_history.dart';
import '../data/repositories/inspection_repository.dart';
import '../data/services/evidence_service.dart';

/// The five recorded stages of a field inspection. There is no manual
/// product identification stage - the backend identifies the product from
/// the package photo itself - and no separate findings stage: the rule
/// engine's violations, each with its own evidence region, are the findings,
/// so they and the evidence step are the same screen.
enum InspectionStep {
  information('Inspection information'),
  scan('Scan package'),
  checklist('Compliance checklist'),
  evidence('Findings & evidence'),
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

  /// On-device path of the captured package photo, kept for local preview
  /// even after it has been uploaded (only a fresh capture replaces it).
  String? _packagePhotoPath;
  /// Set once [_packagePhotoPath] has actually been uploaded - null again
  /// after a retake, until the new photo is uploaded in turn.
  String? _uploadedPackageImageUrl;
  /// Set when the camera/gallery picker itself fails (permission denied, no
  /// camera device, browser blocked it) - distinct from [_aiError], which is
  /// only ever set once a photo exists and the backend call on it failed.
  /// Without this, a picker failure before any photo is captured had nowhere
  /// to surface: [AiEvaluationPanel] only renders once [packagePhotoCaptured]
  /// is true, so the officer saw "Take photo" do nothing at all.
  String? _captureError;

  Inspection get inspection => _inspection;
  InspectionStep get step => _step;
  bool get busy => _busy;
  bool get dirty => _dirty;

  AIEvaluation? get aiEvaluation => _aiEvaluation;
  AiEvaluationStatus get aiStatus => _aiStatus;
  String? get aiError => _aiError;

  String? get packagePhotoPath => _packagePhotoPath;
  bool get packagePhotoCaptured => _packagePhotoPath != null;
  String? get captureError => _captureError;

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

  /* ---------------- Product identification ---------------- */
  //
  // There is no manual identification step: the backend identifies the
  // product from the package photo itself (COMMODITY_NAME/MANUFACTURER read
  // off the image) as part of [runAiEvaluation] and returns it as
  // AIEvaluation.identifiedProduct, merged onto the inspection there.

  /// Product History for the identified product - previous inspections,
  /// violations and repeats. Throws if no product has been identified yet
  /// (i.e. the package hasn't been scanned).
  Future<ProductHistorySummary> fetchProductHistory() {
    final Product? product = _inspection.product;
    if (product == null) {
      throw StateError('Scan the package before requesting product history');
    }
    return _repository.fetchProductHistory(product.id);
  }

  bool get productIdentified => _inspection.product != null;

  /* ---------------- Step 2: scan the package ---------------- */

  /// Captures the package photograph the AI pipeline analyses, uploads it and
  /// immediately runs the analysis - the inspector's only action is taking
  /// the photo; extraction and rule evaluation follow automatically. A
  /// retake replaces the previous photo and re-runs analysis the same way.
  Future<void> capturePackagePhoto(BuildContext context, {bool fromGallery = false}) async {
    _busy = true;
    _captureError = null;
    notifyListeners();
    EvidenceItem? captured;
    try {
      captured = fromGallery
          ? await _evidenceService.pickFromGallery(label: 'Package photo')
          : await _evidenceService.capture(context: context, label: 'Package photo');
      if (captured?.filePath != null) {
        _packagePhotoPath = captured!.filePath;
        _uploadedPackageImageUrl = null;
        _aiStatus = AiEvaluationStatus.idle;
        _aiEvaluation = null;
        _aiError = null;
        _dirty = true;
      }
    } catch (exception) {
      // A cancelled picker returns null and is handled above, not an
      // exception - this only catches genuine failures (permission denied,
      // no camera device, the browser blocking camera access outright).
      _captureError = _describeCaptureFailure(exception, fromGallery: fromGallery);
    } finally {
      _busy = false;
      notifyListeners();
    }
    if (captured?.filePath != null) {
      await runAiEvaluation();
    }
  }

  String _describeCaptureFailure(Object exception, {required bool fromGallery}) {
    if (exception is PlatformException) {
      switch (exception.code) {
        case 'camera_access_denied':
          return 'Camera access was denied. Allow camera permission for this app '
              '(check your browser/device settings) and try again.';
        case 'photo_access_denied':
          return 'Photo library access was denied. Allow photo permission for '
              'this app and try again.';
        case 'no_available_camera':
          return 'No camera was found on this device.';
      }
    }
    return fromGallery
        ? 'Could not open the photo library. Try again, or use the camera instead.'
        : 'Could not open the camera. Check that camera permission is granted '
            'for this browser/app, or use Upload instead.';
  }

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

  /// Uploads the captured package photo (if not already uploaded) and runs
  /// the backend's AI/rule-engine analysis. Every checklist line the AI has
  /// an assessment for is filled in with that result immediately - the
  /// officer reviews and corrects, rather than starting from a blank
  /// checklist and accepting suggestions one at a time. This is the one path
  /// that actually populates the checklist: without a package photo, the
  /// backend has nothing to analyse and every line stays unanswered.
  Future<void> runAiEvaluation() async {
    if (!packagePhotoCaptured) return;

    _aiStatus = AiEvaluationStatus.processing;
    _aiError = null;
    notifyListeners();
    try {
      if (_uploadedPackageImageUrl == null && _packagePhotoPath != null) {
        _uploadedPackageImageUrl =
            await _repository.uploadPackageImage(_inspection.id, _packagePhotoPath!);
      }
      _aiEvaluation = await _repository.runAiEvaluation(_inspection.id, _inspection.checklist);
      _aiStatus = _aiEvaluation!.evaluationStatus;
      if (_aiStatus == AiEvaluationStatus.failed) {
        _aiError = _aiEvaluation!.message;
      } else {
        if (_aiEvaluation!.identifiedProduct != null) {
          // The backend identifies the product from the photo itself - there
          // is no manual identification step to have set this beforehand.
          _inspection = _inspection.copyWith(
            product: _aiEvaluation!.identifiedProduct,
            updatedAt: DateTime.now(),
          );
        }
        _inspection = _inspection.copyWith(
          checklist: _inspection.checklist.map((ChecklistItem item) {
            final AiChecklistResult? suggestion = _aiEvaluation!.checklistResults
                .cast<AiChecklistResult?>()
                .firstWhere((AiChecklistResult? r) => r?.ruleId == item.ruleRef, orElse: () => null);
            // A line the rule engine has nothing to say about (e.g. one that needs a physical
            // measurement no photo can provide) is marked Not applicable, same as every other
            // AI suggestion - previously this branch discarded that verdict and left the item on
            // Pending forever, which blocked submission and looked like the AI had failed on
            // exactly the same two lines every single time.
            if (suggestion == null) return item;
            return item.copyWith(result: suggestion.aiSuggestedStatus);
          }).toList(),
          // Pre-fills the review step's final decision with the rule
          // engine's own verdict - advisory, same as every other AI output
          // here: the officer's own submit is still what makes it
          // authoritative, and can change this before then.
          finalDecision: _decisionFromWire(_aiEvaluation!.suggestedFinalStatus) ?? _inspection.finalDecision,
          updatedAt: DateTime.now(),
        );
      }
    } catch (exception) {
      _aiStatus = AiEvaluationStatus.failed;
      _aiError = 'The AI evaluation could not be completed.';
    } finally {
      notifyListeners();
    }
  }

  FinalDecision? _decisionFromWire(String? wire) {
    switch (wire) {
      case 'COMPLIANT':
        return FinalDecision.compliant;
      case 'NON_COMPLIANT':
        return FinalDecision.nonCompliant;
      case 'INCONCLUSIVE':
        return FinalDecision.inconclusive;
      default:
        return null;
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

  /* ---------------- Step 4: findings & evidence ---------------- */
  //
  // Nothing to record here either: draft.aiEvaluation.violations *is* this
  // step's content, generated by [runAiEvaluation] - each violation is
  // already its own finding, backed by the rule engine's own evidence
  // region on the package photo.

  /* ---------------- Step 5: review ---------------- */

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
      informationComplete &&
      productIdentified &&
      packagePhotoCaptured &&
      checklistComplete &&
      _inspection.finalDecision != null;

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
