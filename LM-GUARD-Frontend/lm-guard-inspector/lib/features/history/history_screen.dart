import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/buttons.dart';
import '../../core/widgets/chips.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/fields.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../core/widgets/panels.dart';
import '../../data/models/enums.dart';
import '../../data/models/inspection.dart';
import '../../state/inspection_controller.dart';
import '../inspections/inspection_detail_screen.dart';

/// Submitted and completed records, with their findings.
class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  static const List<String> _statusLabels = <String>[
    'All',
    'Submitted',
    'Completed',
  ];

  static const List<String> _periodLabels = <String>[
    'All time',
    'Last 7 days',
    'Last 30 days',
  ];

  static const List<int?> _periodDays = <int?>[null, 7, 30];

  String _query = '';
  int _status = 0;
  int _period = 0;

  @override
  Widget build(BuildContext context) {
    final InspectionController controller = context.watch<InspectionController>();
    final List<Inspection> rows = _apply(controller.history);

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: const GovHeader(
        title: 'Inspection History',
        subtitle: 'Records submitted from this device',
      ),
      body: Column(
        children: <Widget>[
          Container(
            color: AppColors.surface,
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(
              children: <Widget>[
                AppSearchField(
                  hint: 'Search submitted records',
                  onChanged: (String value) => setState(() => _query = value),
                ),
                const SizedBox(height: 12),
                FilterChipsRow(
                  labels: _statusLabels,
                  selectedIndex: _status,
                  onSelected: (int index) => setState(() => _status = index),
                ),
                const SizedBox(height: 8),
                FilterChipsRow(
                  labels: _periodLabels,
                  selectedIndex: _period,
                  onSelected: (int index) => setState(() => _period = index),
                ),
              ],
            ),
          ),
          Container(height: 1, color: AppColors.border),
          Expanded(
            child: RefreshIndicator(
              onRefresh: controller.load,
              child: _body(controller, rows),
            ),
          ),
        ],
      ),
    );
  }

  Widget _body(InspectionController controller, List<Inspection> rows) {
    if (controller.loading) {
      return const ScrollableCentre(
        child: LoadingState(message: 'Loading history'),
      );
    }

    if (controller.hasError) {
      return ScrollableCentre(
        child: ErrorStateView(
          message: controller.error ?? 'Your history could not be loaded.',
          onRetry: controller.load,
        ),
      );
    }

    if (rows.isEmpty) {
      return const ScrollableCentre(
        child: EmptyState(
          icon: Icons.history,
          title: 'No records in this period',
          message:
              'Submitted inspections will be listed here with their findings.',
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 20),
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: rows.length,
      itemBuilder: (BuildContext context, int index) =>
          _HistoryCard(inspection: rows[index]),
    );
  }

  List<Inspection> _apply(List<Inspection> source) {
    final String needle = _query.trim().toLowerCase();
    final int? days = _periodDays[_period];

    return source.where((Inspection record) {
      if (_status == 1 && record.status != InspectionStatus.submitted) {
        return false;
      }
      if (_status == 2 && record.status != InspectionStatus.completed) {
        return false;
      }
      if (days != null) {
        final DateTime at = record.submittedAt ?? record.updatedAt;
        if (Fmt.daysFromToday(at) > days) return false;
      }
      if (needle.isEmpty) return true;
      return record.id.toLowerCase().contains(needle) ||
          record.establishment.toLowerCase().contains(needle) ||
          record.productName.toLowerCase().contains(needle);
    }).toList();
  }
}

class _HistoryCard extends StatelessWidget {
  const _HistoryCard({required this.inspection});

  final Inspection inspection;

  @override
  Widget build(BuildContext context) {
    final DateTime at = inspection.submittedAt ?? inspection.updatedAt;

    return AppPanel(
      margin: const EdgeInsets.only(bottom: 10),
      leadingStripe: statusStripeColor(inspection.status),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(child: Text(inspection.id, style: AppText.identifier)),
              StatusChip(inspection.status, dense: true),
            ],
          ),
          const SizedBox(height: 5),
          Text(
            inspection.establishment,
            style: AppText.recordTitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 3),
          Text(
            inspection.productName,
            style: AppText.caption,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: LabelledValue(
                  label: 'Submitted',
                  value: Fmt.dayTime(at),
                ),
              ),
              Expanded(
                child: LabelledValue(
                  label: 'Result',
                  value: inspection.findings.isEmpty
                      ? 'Compliant'
                      : Fmt.plural(inspection.findings.length, 'violation'),
                  valueStyle: AppText.body.copyWith(
                    fontWeight: FontWeight.w500,
                    color: inspection.findings.isEmpty
                        ? AppColors.success
                        : AppColors.danger,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          SecondaryButton(
            label: 'View report',
            icon: Icons.description_outlined,
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) =>
                    InspectionDetailScreen(inspectionId: inspection.id),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
