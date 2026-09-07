import 'package:flutter/foundation.dart';

import '../data/models/enums.dart';
import '../data/models/inspection.dart';
import '../data/repositories/inspection_repository.dart';

/// Owns the officer's work queue and keeps every screen in step.
class InspectionController extends ChangeNotifier {
  InspectionController(this.repository);

  final InspectionRepository repository;

  List<Inspection> _all = <Inspection>[];
  bool _loading = true;
  String? _error;

  List<Inspection> get all => _all;
  bool get loading => _loading;
  String? get error => _error;
  bool get hasError => _error != null;

  /// True once a load has finished and returned nothing at all — distinct from
  /// "still loading" and from "loaded, but every record is filtered out".
  bool get isEmpty => !_loading && _error == null && _all.isEmpty;

  Future<void> load({String? inspectorId}) async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _all = await repository.fetchInspections(inspectorId: inspectorId);
    } catch (_) {
      _error = 'The work queue could not be loaded.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  /* ---------------- Derived views ---------------- */

  List<Inspection> get todaysAssignments {
    final List<Inspection> rows = _all
        .where((Inspection i) => i.status.isOpenWork)
        .toList();
    rows.sort((Inspection a, Inspection b) {
      final int byPriority =
          _priorityRank(b.priority).compareTo(_priorityRank(a.priority));
      if (byPriority != 0) return byPriority;
      return a.dueOn.compareTo(b.dueOn);
    });
    return rows;
  }

  /// The single most urgent piece of open work: highest priority first, then
  /// the earliest due date. Null when the queue holds no open assignments.
  ///
  /// Returned as a nullable rather than a sentinel record so callers are
  /// forced by the type system to render an empty state instead of reading
  /// fields off a placeholder.
  Inspection? get nextAssignment {
    final List<Inspection> open = todaysAssignments;
    return open.isEmpty ? null : open.first;
  }

  /// Open work excluding the record already surfaced as [nextAssignment].
  List<Inspection> get followingAssignments {
    final List<Inspection> open = todaysAssignments;
    return open.length <= 1 ? const <Inspection>[] : open.sublist(1);
  }

  List<Inspection> get drafts => _all
      .where((Inspection i) => i.status == InspectionStatus.draft)
      .toList();

  List<Inspection> get history {
    final List<Inspection> rows =
        _all.where((Inspection i) => i.status.isClosed).toList();
    rows.sort((Inspection a, Inspection b) {
      final DateTime left = a.submittedAt ?? a.updatedAt;
      final DateTime right = b.submittedAt ?? b.updatedAt;
      return right.compareTo(left);
    });
    return rows;
  }

  int get assignedCount =>
      _all.where((Inspection i) => i.status == InspectionStatus.assigned).length;

  int get inProgressCount => _all
      .where((Inspection i) => i.status == InspectionStatus.inProgress)
      .length;

  int get draftCount => drafts.length;

  int get completedTodayCount => _all.where((Inspection i) {
        final DateTime? at = i.submittedAt;
        if (at == null) return false;
        final DateTime now = DateTime.now();
        return at.year == now.year && at.month == now.month && at.day == now.day;
      }).length;

  int get overdueCount =>
      _all.where((Inspection i) => i.isOverdue).length;

  /// Filtered list for the My Inspections screen.
  List<Inspection> filter({
    required String query,
    InspectionStatus? status,
  }) {
    final String needle = query.trim().toLowerCase();
    return _all.where((Inspection i) {
      if (status != null && i.status != status) return false;
      if (needle.isEmpty) return true;
      return i.id.toLowerCase().contains(needle) ||
          i.establishment.toLowerCase().contains(needle) ||
          i.location.toLowerCase().contains(needle) ||
          i.productName.toLowerCase().contains(needle);
    }).toList();
  }

  Inspection? byId(String id) {
    for (final Inspection inspection in _all) {
      if (inspection.id == id) return inspection;
    }
    return null;
  }

  /* ---------------- Mutations ---------------- */

  Future<Inspection> saveDraft(Inspection inspection) async {
    final Inspection saved = await repository.saveDraft(inspection);
    _replace(saved);
    return saved;
  }

  Future<Inspection> submit(Inspection inspection) async {
    final Inspection submitted = await repository.submit(inspection);
    _replace(submitted);
    return submitted;
  }

  /// Marks an assigned record as picked up so the queue reflects field state.
  Future<Inspection> beginWork(Inspection inspection) async {
    if (inspection.status != InspectionStatus.assigned) return inspection;
    final Inspection started = inspection.copyWith(
      status: InspectionStatus.inProgress,
      updatedAt: DateTime.now(),
    );
    _replace(started);
    return started;
  }

  void adopt(Inspection inspection) => _replace(inspection);

  void _replace(Inspection inspection) {
    final int index =
        _all.indexWhere((Inspection existing) => existing.id == inspection.id);
    if (index >= 0) {
      _all[index] = inspection;
    } else {
      _all.insert(0, inspection);
    }
    notifyListeners();
  }

  static int _priorityRank(Priority priority) {
    switch (priority) {
      case Priority.high:
        return 3;
      case Priority.medium:
        return 2;
      case Priority.low:
        return 1;
    }
  }
}
