import '../mock/mock_data.dart';
import '../models/ai_evaluation.dart';
import '../models/checklist_item.dart';
import '../models/enums.dart';
import '../models/evidence.dart';
import '../models/finding.dart';
import '../models/inspection.dart';
import '../models/product.dart';
import '../models/product_history.dart';
import '../services/api_client.dart';

/// Data access for inspections.
///
/// Screens depend on this interface only, so swapping the mock store for an
/// API-backed implementation requires no UI changes.
abstract class InspectionRepository {
  /// Every inspection assigned to (or opened by) [inspectorId]. The backend
  /// enforces that an inspector can only ever be handed their own records -
  /// this parameter selects which records to ask for, it does not itself
  /// grant access to anyone else's.
  Future<List<Inspection>> fetchInspections({String? inspectorId});

  Future<Inspection?> fetchInspection(String id);

  Future<List<Product>> searchProducts(String query);

  Future<Product?> lookupBarcode(String barcode);

  Future<List<RuleReference>> fetchRules();

  Future<Inspection> saveDraft(Inspection inspection);

  Future<Inspection> submit(Inspection inspection);

  Future<Inspection> createInspection({
    required String establishment,
    required String location,
    required String zone,
    required InspectionType type,
    required Priority priority,
    required DateTime scheduledFor,
  });

  /// The product-identification step: attaches an existing or newly
  /// registered product to an inspection already opened.
  Future<Inspection> identifyProduct(String inspectionId, Product product);

  /// Aggregated inspection/violation history for the product under
  /// inspection - the Product History step.
  Future<ProductHistorySummary> fetchProductHistory(String productId);

  /// Runs the backend's AI/rule-engine analysis and returns it mapped onto
  /// the current checklist, so each line can show an advisory suggestion.
  /// Never writes to [checklist] itself - the inspector's own recorded
  /// result on each line is a separate, independent decision.
  Future<AIEvaluation> runAiEvaluation(String inspectionId, List<ChecklistItem> checklist);

  /// Uploads a captured evidence photo (requires [EvidenceItem.filePath] to
  /// be a real on-device file) and returns the server-persisted record -
  /// its id becomes the one the rest of the app treats as authoritative.
  Future<EvidenceItem> uploadEvidence(String inspectionId, EvidenceItem item);
}

/// In-memory implementation backed by [MockData].
class MockInspectionRepository implements InspectionRepository {
  MockInspectionRepository() : _store = MockData.inspections();

  final List<Inspection> _store;
  int _sequence = 130;

  @override
  Future<List<Inspection>> fetchInspections({String? inspectorId}) async {
    await Future<void>.delayed(const Duration(milliseconds: 320));
    final List<Inspection> copy = List<Inspection>.of(_store);
    copy.sort((Inspection a, Inspection b) => b.updatedAt.compareTo(a.updatedAt));
    return copy;
  }

  @override
  Future<Inspection?> fetchInspection(String id) async {
    await Future<void>.delayed(const Duration(milliseconds: 180));
    for (final Inspection inspection in _store) {
      if (inspection.id == id) return inspection;
    }
    return null;
  }

  @override
  Future<List<Product>> searchProducts(String query) async {
    await Future<void>.delayed(const Duration(milliseconds: 260));
    final String needle = query.trim().toLowerCase();
    if (needle.isEmpty) return MockData.catalogue();
    return MockData.catalogue().where((Product product) {
      return product.name.toLowerCase().contains(needle) ||
          product.brand.toLowerCase().contains(needle) ||
          product.manufacturer.toLowerCase().contains(needle) ||
          product.barcode.contains(needle);
    }).toList();
  }

  @override
  Future<Product?> lookupBarcode(String barcode) async {
    await Future<void>.delayed(const Duration(milliseconds: 900));
    final String needle = barcode.trim();
    for (final Product product in MockData.catalogue()) {
      if (product.barcode == needle) return product;
    }
    return null;
  }

  @override
  Future<List<RuleReference>> fetchRules() async {
    await Future<void>.delayed(const Duration(milliseconds: 120));
    return MockData.rules;
  }

  @override
  Future<Inspection> saveDraft(Inspection inspection) async {
    await Future<void>.delayed(const Duration(milliseconds: 420));
    final Inspection saved = inspection.copyWith(
      status: inspection.status.isClosed
          ? inspection.status
          : InspectionStatus.draft,
      updatedAt: DateTime.now(),
    );
    _upsert(saved);
    return saved;
  }

  @override
  Future<Inspection> submit(Inspection inspection) async {
    await Future<void>.delayed(const Duration(milliseconds: 900));
    final DateTime now = DateTime.now();
    final Inspection submitted = inspection.copyWith(
      status: InspectionStatus.submitted,
      updatedAt: now,
      submittedAt: now,
    );
    _upsert(submitted);
    return submitted;
  }

  @override
  Future<Inspection> createInspection({
    required String establishment,
    required String location,
    required String zone,
    required InspectionType type,
    required Priority priority,
    required DateTime scheduledFor,
  }) async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
    _sequence++;
    final DateTime now = DateTime.now();
    final Inspection created = Inspection(
      id: 'INS-2026-${_sequence.toString().padLeft(5, '0')}',
      establishment: establishment,
      location: location,
      zone: zone,
      type: type,
      priority: priority,
      status: InspectionStatus.inProgress,
      assignedOn: now,
      dueOn: scheduledFor,
      updatedAt: now,
      checklist: MockData.checklistTemplate(),
    );
    _store.insert(0, created);
    return created;
  }

  @override
  Future<Inspection> identifyProduct(String inspectionId, Product product) async {
    await Future<void>.delayed(const Duration(milliseconds: 200));
    final Inspection current = await fetchInspection(inspectionId) ??
        (throw StateError('Inspection $inspectionId not found'));
    final Inspection updated = current.copyWith(product: product, updatedAt: DateTime.now());
    _upsert(updated);
    return updated;
  }

  @override
  Future<ProductHistorySummary> fetchProductHistory(String productId) async {
    await Future<void>.delayed(const Duration(milliseconds: 260));
    return const ProductHistorySummary(
      previousInspections: 0,
      compliantCount: 0,
      nonCompliantCount: 0,
      inconclusiveCount: 0,
      previousViolations: 0,
      repeatViolations: 0,
    );
  }

  @override
  Future<AIEvaluation> runAiEvaluation(String inspectionId, List<ChecklistItem> checklist) async {
    await Future<void>.delayed(const Duration(milliseconds: 800));
    return AIEvaluation(
      inspectionId: inspectionId,
      evaluationStatus: AiEvaluationStatus.completed,
      modelVersion: 'mock-1.0.0',
      evaluatedAt: DateTime.now(),
      checklistResults: checklist
          .map((ChecklistItem item) => AiChecklistResult(
                ruleId: item.ruleRef,
                ruleName: item.title,
                aiSuggestedStatus: CheckResult.compliant,
                confidenceScore: 0.9,
                explanation: 'No issue detected (mock evaluation).',
              ))
          .toList(),
    );
  }

  @override
  Future<EvidenceItem> uploadEvidence(String inspectionId, EvidenceItem item) async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
    return item;
  }

  void _upsert(Inspection inspection) {
    final int index = _store.indexWhere(
      (Inspection existing) => existing.id == inspection.id,
    );
    if (index >= 0) {
      _store[index] = inspection;
    } else {
      _store.insert(0, inspection);
    }
  }

  /// Used by the checklist step to reset an inspection to a blank template.
  static List<ChecklistItem> blankChecklist() => MockData.checklistTemplate();
}

/// Maps the six-step workflow's fixed LMPC checklist items onto the
/// declaration fields the backend's rule engine actually evaluates, so a
/// real AI result can inform a real checklist line despite the two using
/// different rule-code namespaces (the checklist's codes are this app's
/// own; the backend's are whatever ruleset is configured). Items with no
/// corresponding backend field (font legibility, packaging condition) are
/// not assessable by image analysis and are reported as such rather than
/// guessed at.
const Map<String, String> _checklistFieldMapping = <String, String>{
  'LMPC-DECL-001': 'CONSUMER_CARE',
  'LMPC-PRC-003': 'MRP',
  'LMPC-QTY-002': 'NET_QUANTITY',
  'LMPC-IDN-005': 'MANUFACTURER',
  'LMPC-ORG-004': 'ORIGIN',
  'LMPC-CHR-006': 'MANUFACTURE_DATE',
};

/// Real backend-backed implementation. Screens never see this type directly -
/// they depend on [InspectionRepository].
class ApiInspectionRepository implements InspectionRepository {
  ApiInspectionRepository(this._client);

  final ApiClient _client;

  @override
  Future<List<Inspection>> fetchInspections({String? inspectorId}) async {
    final Map<String, String> query = <String, String>{'size': '100'};
    if (inspectorId != null) query['inspectorId'] = inspectorId;
    final Map<String, dynamic> page = await _client.get(_withQuery(ApiRoutes.inspections, query)) as Map<String, dynamic>;
    final List<dynamic> items = page['items'] as List<dynamic>? ?? <dynamic>[];
    return items.map((dynamic json) => _inspectionFromSummary(json as Map<String, dynamic>)).toList();
  }

  /// Loads the case fields plus everything the inspector has recorded so far -
  /// checklist, findings, evidence - so reopening an inspection (a fresh app
  /// launch, or just navigating back to it) shows exactly what was saved,
  /// not a blank workflow. These live on separate endpoints from the
  /// inspection detail itself (see `ChecklistService`/`FindingService`/
  /// `InspectionEvidenceService` on the backend), so this is four round trips
  /// rather than one - run concurrently to keep it to one round-trip's worth
  /// of latency.
  @override
  Future<Inspection?> fetchInspection(String id) async {
    final List<dynamic> results = await Future.wait(<Future<dynamic>>[
      _client.get(ApiRoutes.inspection(id)),
      _fetchChecklist(id),
      _fetchFindings(id),
      _fetchOfficerEvidence(id),
    ]);

    final Inspection base = _inspectionFromDetail(results[0] as Map<String, dynamic>);
    final List<Finding> findings = results[2] as List<Finding>;
    _syncedFindingIds.addAll(findings.map((Finding f) => f.id));

    return base.copyWith(
      checklist: results[1] as List<ChecklistItem>,
      findings: findings,
      evidence: results[3] as List<EvidenceItem>,
    );
  }

  Future<List<ChecklistItem>> _fetchChecklist(String inspectionId) async {
    final List<dynamic> rows = await _client.get(ApiRoutes.checklist(inspectionId)) as List<dynamic>;
    return rows.map((dynamic row) {
      final Map<String, dynamic> r = row as Map<String, dynamic>;
      return ChecklistItem(
        // The item code (e.g. "LMPC-DECL-001"), not the row's own database id,
        // is this app's stable identifier for a checklist line - it's what
        // `_checklistFieldMapping` and every screen key off.
        id: r['itemCode'] as String,
        ruleRef: r['itemCode'] as String? ?? '',
        title: r['title'] as String? ?? '',
        guidance: r['guidance'] as String? ?? '',
        result: CheckResult.values.firstWhere(
          (CheckResult value) => value.wireValue == r['result'],
          orElse: () => CheckResult.pending,
        ),
        note: r['note'] as String? ?? '',
      );
    }).toList();
  }

  Future<List<Finding>> _fetchFindings(String inspectionId) async {
    final List<dynamic> rows = await _client.get(ApiRoutes.findings(inspectionId)) as List<dynamic>;
    return rows.map((dynamic row) => Finding.fromJson(row as Map<String, dynamic>)).toList();
  }

  Future<List<EvidenceItem>> _fetchOfficerEvidence(String inspectionId) async {
    final List<dynamic> rows = await _client.get(ApiRoutes.officerEvidence(inspectionId)) as List<dynamic>;
    return rows.map((dynamic row) {
      final Map<String, dynamic> r = row as Map<String, dynamic>;
      return EvidenceItem(
        id: r['id'] as String,
        // Not tracked server-side (every officer-evidence upload is a photo
        // today); defaulting rather than guessing at a value never sent.
        kind: EvidenceKind.photo,
        label: r['label'] as String? ?? '',
        capturedAt: DateTime.tryParse(r['capturedAt'] as String? ?? '') ?? DateTime.now(),
        description: r['description'] as String? ?? '',
        imageUrl: r['imageUrl'] as String?,
      );
    }).toList();
  }

  @override
  Future<List<Product>> searchProducts(String query) async {
    final Map<String, dynamic> page = await _client.get(
      _withQuery(ApiRoutes.products, <String, String>{'search': query, 'size': '20'}),
    ) as Map<String, dynamic>;
    final List<dynamic> items = page['items'] as List<dynamic>? ?? <dynamic>[];
    return items.map((dynamic json) => _productFromJson(json as Map<String, dynamic>)).toList();
  }

  @override
  Future<Product?> lookupBarcode(String barcode) async {
    final String needle = barcode.trim();
    final List<Product> results = await searchProducts(needle);
    for (final Product product in results) {
      if (product.barcode == needle) return product;
    }
    return null;
  }

  @override
  Future<List<RuleReference>> fetchRules() async {
    final Map<String, dynamic> ruleSet = await _client.get(ApiRoutes.rules) as Map<String, dynamic>;
    final List<dynamic> rules = ruleSet['rules'] as List<dynamic>? ?? <dynamic>[];
    return rules
        .map((dynamic json) {
          final Map<String, dynamic> r = json as Map<String, dynamic>;
          return RuleReference(
            r['ruleCode'] as String? ?? '',
            r['ruleName'] as String? ?? '',
            r['fieldName'] as String? ?? '',
          );
        })
        .toList();
  }

  /// Persists the whole in-progress inspection: case fields via the existing
  /// inspection record, checklist via a bulk upsert, findings via individual
  /// creates for any not yet known to the backend (a locally-generated id
  /// signals "not yet created there"), and working notes - so none of it is
  /// lost if the app closes before the inspector submits.
  @override
  Future<Inspection> saveDraft(Inspection inspection) async {
    await _pushChecklist(inspection);
    await _pushNewFindings(inspection);
    await _pushNotes(inspection);
    return (await fetchInspection(inspection.id)) ?? inspection;
  }

  Future<void> _pushNotes(Inspection inspection) async {
    await _client.patch(ApiRoutes.notes(inspection.id), <String, dynamic>{
      'notes': inspection.officerNotes,
    });
  }

  @override
  Future<Inspection> submit(Inspection inspection) async {
    if (inspection.finalDecision == null) {
      throw StateError('The inspector must record a final decision before submitting');
    }
    await _pushChecklist(inspection);
    await _pushNewFindings(inspection);

    final Map<String, dynamic> json = await _client.post(ApiRoutes.submitInspection(inspection.id), <String, dynamic>{
      'finalDecision': inspection.finalDecision!.wireValue,
      if (inspection.officerNotes.isNotEmpty) 'notes': inspection.officerNotes,
    }) as Map<String, dynamic>;
    return _inspectionFromDetail(json).copyWith(
      checklist: inspection.checklist,
      findings: inspection.findings,
      evidence: inspection.evidence,
      officerNotes: inspection.officerNotes,
      submittedAt: DateTime.now(),
      finalDecision: inspection.finalDecision,
    );
  }

  Future<void> _pushChecklist(Inspection inspection) async {
    if (inspection.checklist.isEmpty) return;
    await _client.put(ApiRoutes.checklist(inspection.id), <String, dynamic>{
      'items': inspection.checklist
          .map((ChecklistItem item) => <String, dynamic>{
                'itemCode': item.id,
                'title': item.title,
                'guidance': item.guidance,
                'result': item.result.wireValue,
                if (item.note.isNotEmpty) 'note': item.note,
              })
          .toList(),
    });
  }

  /// Findings created locally carry an id this repository generated
  /// (`FND-...`, from [DraftController.addFinding]); the backend assigns its
  /// own id on create, so anything not already recognised is pushed once.
  final Set<String> _syncedFindingIds = <String>{};

  Future<void> _pushNewFindings(Inspection inspection) async {
    for (final Finding finding in inspection.findings) {
      if (_syncedFindingIds.contains(finding.id)) continue;
      await _client.post(ApiRoutes.findings(inspection.id), <String, dynamic>{
        'ruleRef': finding.ruleRef,
        'ruleName': finding.ruleName,
        'severity': _severityToBackend(finding.severity),
        'description': finding.description,
        if (finding.note.isNotEmpty) 'note': finding.note,
      });
      _syncedFindingIds.add(finding.id);
    }
  }

  /// Backend's Severity is a 3-value scale (MINOR/MAJOR/CRITICAL); this
  /// app's is 4-value (low/medium/high/critical) - medium and high both
  /// collapse to MAJOR rather than inventing a fourth backend value.
  String _severityToBackend(Severity severity) {
    switch (severity) {
      case Severity.low:
        return 'MINOR';
      case Severity.critical:
        return 'CRITICAL';
      case Severity.medium:
      case Severity.high:
        return 'MAJOR';
    }
  }

  @override
  Future<Inspection> createInspection({
    required String establishment,
    required String location,
    required String zone,
    required InspectionType type,
    required Priority priority,
    required DateTime scheduledFor,
  }) async {
    final Map<String, dynamic> json = await _client.post(ApiRoutes.inspections, <String, dynamic>{
      'establishment': establishment,
      if (location.isNotEmpty) 'address': location,
      'inspectionType': type.wireValue,
      'priority': priority.wireValue,
      // .toUtc() first: a bare local-time ISO string (no offset/Z) fails Jackson's
      // java.time.Instant deserialization on the backend with a 400.
      'dueDate': scheduledFor.toUtc().toIso8601String(),
    }) as Map<String, dynamic>;
    return _inspectionFromDetail(json).copyWith(checklist: MockInspectionRepository.blankChecklist());
  }

  @override
  Future<Inspection> identifyProduct(String inspectionId, Product product) async {
    final Map<String, dynamic> body = product.id.startsWith('PRD-FIELD-')
        // A product entered manually in the field (product_step.dart's
        // "Enter manually" mode) has no backend id yet - register it inline.
        ? <String, dynamic>{
            'productName': product.name,
            if (product.brand.isNotEmpty) 'brand': product.brand,
            if (product.barcode.isNotEmpty) 'barcode': product.barcode,
          }
        : <String, dynamic>{'productId': product.id};

    final Map<String, dynamic> json = await _client.patch(ApiRoutes.identifyProduct(inspectionId), body) as Map<String, dynamic>;
    return _inspectionFromDetail(json);
  }

  @override
  Future<ProductHistorySummary> fetchProductHistory(String productId) async {
    final Map<String, dynamic> json = await _client.get(ApiRoutes.productInspectionSummary(productId)) as Map<String, dynamic>;
    final List<dynamic> recent = json['recentInspections'] as List<dynamic>? ?? <dynamic>[];
    return ProductHistorySummary(
      previousInspections: json['previousInspections'] as int? ?? 0,
      compliantCount: json['compliantCount'] as int? ?? 0,
      nonCompliantCount: json['nonCompliantCount'] as int? ?? 0,
      inconclusiveCount: json['inconclusiveCount'] as int? ?? 0,
      previousViolations: json['previousViolations'] as int? ?? 0,
      repeatViolations: json['repeatViolations'] as int? ?? 0,
      riskLevel: json['latestRiskLevel'] as String?,
      recentInspections: recent.map((dynamic row) {
        final Map<String, dynamic> r = row as Map<String, dynamic>;
        return PreviousInspectionEntry(
          inspectionId: r['inspectionId'] as String? ?? '',
          inspectedOn: DateTime.tryParse(r['createdAt'] as String? ?? '') ?? DateTime.now(),
          result: _verdictLabel(r['status'] as String?),
          establishment: r['establishment'] as String?,
          inspectorName: r['inspectorName'] as String?,
        );
      }).toList(),
    );
  }

  String _verdictLabel(String? status) {
    switch (status) {
      case 'COMPLIANT':
        return 'Compliant';
      case 'NON_COMPLIANT':
        return 'Non-compliant';
      case 'INCONCLUSIVE':
        return 'Inconclusive';
      default:
        return 'Unresolved';
    }
  }

  @override
  Future<AIEvaluation> runAiEvaluation(String inspectionId, List<ChecklistItem> checklist) async {
    final Map<String, dynamic> json;
    try {
      json = await _client.post(ApiRoutes.analyze(inspectionId), const <String, dynamic>{}) as Map<String, dynamic>;
    } on ApiException catch (exception) {
      return AIEvaluation(
        inspectionId: inspectionId,
        evaluationStatus: AiEvaluationStatus.failed,
        message: exception.message,
      );
    }

    final List<dynamic> fields = json['fields'] as List<dynamic>? ?? <dynamic>[];
    final List<dynamic> violations = json['violations'] as List<dynamic>? ?? <dynamic>[];

    final List<AiChecklistResult> results = checklist.map((ChecklistItem item) {
      final String? backendField = _checklistFieldMapping[item.id];
      if (backendField == null) {
        return AiChecklistResult(
          ruleId: item.ruleRef,
          ruleName: item.title,
          aiSuggestedStatus: CheckResult.notApplicable,
          confidenceScore: 0,
          explanation: 'Not assessable by automated image analysis - record this line manually.',
        );
      }

      final Map<String, dynamic>? violation = violations.cast<Map<String, dynamic>?>().firstWhere(
            (Map<String, dynamic>? v) => v?['fieldName'] == backendField,
            orElse: () => null,
          );
      if (violation != null) {
        final bool inconclusive = violation['status'] == 'INCONCLUSIVE';
        return AiChecklistResult(
          ruleId: item.ruleRef,
          ruleName: item.title,
          aiSuggestedStatus: inconclusive ? CheckResult.notApplicable : CheckResult.nonCompliant,
          confidenceScore: (violation['decisionConfidence'] as num?)?.toDouble() ?? 0,
          explanation: violation['finding'] as String? ?? 'A possible issue was detected.',
          detectedIssue: violation['observedValue'] as String?,
        );
      }

      final Map<String, dynamic>? field = fields.cast<Map<String, dynamic>?>().firstWhere(
            (Map<String, dynamic>? f) => f?['name'] == backendField,
            orElse: () => null,
          );
      return AiChecklistResult(
        ruleId: item.ruleRef,
        ruleName: item.title,
        aiSuggestedStatus: CheckResult.compliant,
        confidenceScore: (field?['confidence'] as num?)?.toDouble() ?? 0,
        explanation: 'No issue detected for this declaration.',
      );
    }).toList();

    return AIEvaluation(
      inspectionId: inspectionId,
      evaluationStatus: AiEvaluationStatus.completed,
      modelVersion: json['aiProvider'] as String?,
      evaluatedAt: DateTime.tryParse(json['analyzedAt'] as String? ?? ''),
      checklistResults: results,
    );
  }

  @override
  Future<EvidenceItem> uploadEvidence(String inspectionId, EvidenceItem item) async {
    final String? filePath = item.filePath;
    if (filePath == null) {
      throw StateError('Evidence item ${item.id} has no on-device file to upload');
    }
    final Map<String, dynamic> json = await _client.uploadFile(
      ApiRoutes.officerEvidence(inspectionId),
      filePath,
      query: <String, String>{
        if (item.label.isNotEmpty) 'label': item.label,
        if (item.description.isNotEmpty) 'description': item.description,
      },
    ) as Map<String, dynamic>;

    return EvidenceItem(
      id: json['id'] as String,
      kind: item.kind,
      label: json['label'] as String? ?? item.label,
      capturedAt: DateTime.tryParse(json['capturedAt'] as String? ?? '') ?? item.capturedAt,
      description: json['description'] as String? ?? item.description,
      filePath: filePath,
      imageUrl: json['imageUrl'] as String?,
    );
  }

  // ------------------------------------------------------------------
  // JSON mapping
  // ------------------------------------------------------------------

  String _withQuery(String path, Map<String, String> query) {
    final String qs = query.entries.map((e) => '${e.key}=${Uri.encodeQueryComponent(e.value)}').join('&');
    return '$path?$qs';
  }

  Product _productFromJson(Map<String, dynamic> json) {
    final DateTime created = DateTime.tryParse(json['createdAt'] as String? ?? '') ?? DateTime.now();
    return Product(
      id: json['id'] as String,
      name: json['productName'] as String? ?? '',
      brand: json['brand'] as String? ?? '',
      manufacturer: json['brand'] as String? ?? '',
      category: json['category'] as String? ?? '',
      batchNumber: '',
      manufacturedOn: created,
      expiresOn: created.add(const Duration(days: 730)),
      barcode: json['barcode'] as String? ?? '',
      netQuantity: '',
      mrp: '',
    );
  }

  Inspection _inspectionFromSummary(Map<String, dynamic> json) {
    return Inspection(
      id: json['inspectionId'] as String,
      establishment: json['establishment'] as String? ?? '',
      location: '',
      zone: json['zoneName'] as String? ?? '',
      type: _inspectionTypeFromWire(json['inspectionType'] as String?),
      priority: Priority.fromWire(json['priority'] as String? ?? 'MEDIUM'),
      status: _statusFromBackend(json['status'] as String?, json['completedAt'] != null),
      assignedOn: DateTime.tryParse(json['createdAt'] as String? ?? '') ?? DateTime.now(),
      dueOn: DateTime.tryParse(json['dueDate'] as String? ?? '') ?? DateTime.now(),
      updatedAt: DateTime.tryParse(json['createdAt'] as String? ?? '') ?? DateTime.now(),
      submittedAt: DateTime.tryParse(json['completedAt'] as String? ?? ''),
    );
  }

  Inspection _inspectionFromDetail(Map<String, dynamic> json) {
    final Map<String, dynamic>? product = json['product'] as Map<String, dynamic>?;
    return Inspection(
      id: json['inspectionId'] as String,
      establishment: json['establishment'] as String? ?? '',
      location: json['address'] as String? ?? '',
      zone: json['zoneName'] as String? ?? '',
      type: _inspectionTypeFromWire(json['inspectionType'] as String?),
      priority: Priority.fromWire(json['priority'] as String? ?? 'MEDIUM'),
      status: _statusFromBackend(json['status'] as String?, json['completedAt'] != null),
      assignedOn: DateTime.tryParse(json['createdAt'] as String? ?? '') ?? DateTime.now(),
      dueOn: DateTime.tryParse(json['dueDate'] as String? ?? '') ?? DateTime.now(),
      updatedAt: DateTime.tryParse(json['createdAt'] as String? ?? '') ?? DateTime.now(),
      product: product == null ? null : _productFromJson(product),
      officerNotes: json['notes'] as String? ?? '',
      submittedAt: DateTime.tryParse(json['completedAt'] as String? ?? ''),
    );
  }

  InspectionType _inspectionTypeFromWire(String? value) {
    return InspectionType.values.firstWhere(
      (InspectionType t) => t.wireValue == value,
      orElse: () => InspectionType.routine,
    );
  }

  /// Backend statuses PENDING/IN_PROGRESS/PROCESSING have no submission yet;
  /// COMPLIANT/NON_COMPLIANT/INCONCLUSIVE (any of them) mean the inspector
  /// already submitted - this app's own `submitted`/`completed` distinction
  /// doesn't exist server-side, so a decided inspection reads as `completed`.
  InspectionStatus _statusFromBackend(String? backendStatus, bool hasCompletedAt) {
    if (hasCompletedAt || backendStatus == 'COMPLIANT' || backendStatus == 'NON_COMPLIANT' || backendStatus == 'INCONCLUSIVE') {
      return InspectionStatus.completed;
    }
    if (backendStatus == 'IN_PROGRESS' || backendStatus == 'PROCESSING') {
      return InspectionStatus.inProgress;
    }
    return InspectionStatus.assigned;
  }
}
