import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/fields.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../core/widgets/inspection_card.dart';
import '../../data/models/enums.dart';
import '../../data/models/inspection.dart';
import '../../state/inspection_controller.dart';
import '../shell/main_shell.dart';
import 'inspection_detail_screen.dart';

/// Every record assigned to the officer, filterable by state.
class MyInspectionsScreen extends StatefulWidget {
  const MyInspectionsScreen({super.key});

  @override
  State<MyInspectionsScreen> createState() => _MyInspectionsScreenState();
}

class _MyInspectionsScreenState extends State<MyInspectionsScreen> {
  static const List<String> _filterLabels = <String>[
    'All',
    'Assigned',
    'In progress',
    'Draft',
    'Submitted',
    'Completed',
  ];

  static const List<InspectionStatus?> _filterValues = <InspectionStatus?>[
    null,
    InspectionStatus.assigned,
    InspectionStatus.inProgress,
    InspectionStatus.draft,
    InspectionStatus.submitted,
    InspectionStatus.completed,
  ];

  String _query = '';
  int _filter = 0;

  @override
  Widget build(BuildContext context) {
    final InspectionController controller = context.watch<InspectionController>();
    final List<Inspection> rows = controller.filter(
      query: _query,
      status: _filterValues[_filter],
    );

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: const GovHeader(
        title: 'My Inspections',
        subtitle: 'Assigned records and their current state',
      ),
      body: Column(
        children: <Widget>[
          Container(
            color: AppColors.surface,
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(
              children: <Widget>[
                AppSearchField(
                  hint: 'Search by ID, establishment or product',
                  onChanged: (String value) => setState(() => _query = value),
                ),
                const SizedBox(height: 12),
                FilterChipsRow(
                  labels: _filterLabels,
                  selectedIndex: _filter,
                  onSelected: (int index) => setState(() => _filter = index),
                ),
              ],
            ),
          ),
          Container(height: 1, color: AppColors.border),
          Expanded(
            child: RefreshIndicator(
              onRefresh: controller.load,
              child: _body(context, controller, rows),
            ),
          ),
        ],
      ),
    );
  }

  /// Loading, error, empty and populated — each a scrollable, so pull to
  /// refresh works in every state rather than only when records exist.
  Widget _body(
    BuildContext context,
    InspectionController controller,
    List<Inspection> rows,
  ) {
    if (controller.loading) {
      return const ScrollableCentre(
        child: LoadingState(message: 'Loading your inspections'),
      );
    }

    if (controller.hasError) {
      return ScrollableCentre(
        child: ErrorStateView(
          message: controller.error ?? 'Your inspections could not be loaded.',
          onRetry: controller.load,
        ),
      );
    }

    if (rows.isEmpty) {
      return ScrollableCentre(
        child: EmptyState(
          icon: Icons.assignment_outlined,
          title: 'No inspections found',
          message: _query.isEmpty
              ? 'Nothing is filed under '
                  '${_filterLabels[_filter].toLowerCase()}.'
              : 'Nothing matched "$_query".',
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 20),
      physics: const AlwaysScrollableScrollPhysics(),
      // One header row plus one card per record. rows is never null and the
      // index is always translated back inside range.
      itemCount: rows.length + 1,
      itemBuilder: (BuildContext context, int index) {
        if (index == 0) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Text(
              Fmt.plural(rows.length, 'record'),
              style: AppText.label,
            ),
          );
        }
        final Inspection inspection = rows[index - 1];
        return InspectionCard(
          inspection: inspection,
          showProgress: inspection.status == InspectionStatus.draft ||
              inspection.status == InspectionStatus.inProgress,
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) =>
                  InspectionDetailScreen(inspectionId: inspection.id),
            ),
          ),
          actionLabel: actionLabelFor(inspection.status),
          onAction: () => _onAction(context, inspection),
        );
      },
    );
  }

  void _onAction(BuildContext context, Inspection inspection) {
    if (inspection.status.isClosed) {
      Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => InspectionDetailScreen(inspectionId: inspection.id),
        ),
      );
      return;
    }
    openInspectionWorkflow(context, existing: inspection);
  }
}
