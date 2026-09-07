import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/buttons.dart';
import '../../core/widgets/chips.dart';
import '../../core/widgets/checklist_panel.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/evidence_thumb.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../core/widgets/panels.dart';
import '../../core/widgets/product_panel.dart';
import '../../data/models/enums.dart';
import '../../data/models/evidence.dart';
import '../../data/models/finding.dart';
import '../../data/models/inspection.dart';
import '../../state/inspection_controller.dart';
import '../shell/main_shell.dart';

/// The full record: particulars, product, checklist, findings and evidence.
/// Doubles as the submitted report view.
class InspectionDetailScreen extends StatelessWidget {
  const InspectionDetailScreen({super.key, required this.inspectionId});

  final String inspectionId;

  @override
  Widget build(BuildContext context) {
    final InspectionController controller = context.watch<InspectionController>();
    final Inspection? record = controller.byId(inspectionId);

    if (record == null) {
      return Scaffold(
        backgroundColor: AppColors.canvas,
        appBar: const RecordHeader(title: 'Inspection'),
        body: EmptyState(
          icon: Icons.search_off,
          title: 'Record not available',
          message: 'No inspection could be found for $inspectionId.',
        ),
      );
    }

    final bool closed = record.status.isClosed;

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: RecordHeader(
        title: closed ? 'Inspection report' : 'Inspection record',
        subtitle: record.id,
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        children: <Widget>[
          _summaryPanel(record),

          const SizedBox(height: 20),
          const SectionHeading('Product details'),
          ProductPanel(product: record.product),

          const SizedBox(height: 20),
          SectionHeading(
            'Compliance checklist',
            trailing: Text(
              '${record.answeredChecks} of ${record.checklist.length}',
              style: AppText.caption,
            ),
          ),
          ChecklistPanel(items: record.checklist),

          const SizedBox(height: 20),
          SectionHeading(
            'Findings',
            trailing: Text('${record.findings.length}', style: AppText.caption),
          ),
          if (record.findings.isEmpty)
            const AppPanel(
              child: Text(
                'No violations recorded against this inspection.',
                style: AppText.bodyMuted,
              ),
            )
          else
            ...record.findings.map(
              (Finding finding) => AppPanel(
                margin: const EdgeInsets.only(bottom: 8),
                leadingStripe: finding.severity == Severity.critical
                    ? AppColors.critical
                    : AppColors.danger,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        Expanded(
                          child:
                              Text(finding.reference, style: AppText.identifier),
                        ),
                        SeverityChip(finding.severity, dense: true),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(finding.description, style: AppText.body),
                    const SizedBox(height: 6),
                    Text(
                      '${finding.ruleRef} · ${finding.ruleName}',
                      style: AppText.caption,
                    ),
                  ],
                ),
              ),
            ),

          const SizedBox(height: 20),
          SectionHeading(
            'Evidence',
            trailing: Text(
              Fmt.plural(record.evidence.length, 'item'),
              style: AppText.caption,
            ),
          ),
          if (record.evidence.isEmpty)
            const AppPanel(
              child: Text('No evidence captured.', style: AppText.bodyMuted),
            )
          else
            ...record.evidence.map(
              (EvidenceItem item) => AppPanel(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(11),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    EvidenceThumb(item: item, size: 54),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(item.label, style: AppText.recordTitle),
                          const SizedBox(height: 2),
                          Text(Fmt.dayTime(item.capturedAt), style: AppText.caption),
                          if (item.description.isNotEmpty) ...<Widget>[
                            const SizedBox(height: 4),
                            Text(item.description, style: AppText.caption),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),

          if (record.officerNotes.isNotEmpty) ...<Widget>[
            const SizedBox(height: 20),
            const SectionHeading('Inspector notes'),
            AppPanel(
              child: Text(record.officerNotes, style: AppText.body),
            ),
          ],

          const SizedBox(height: 22),
          if (!closed)
            PrimaryButton(
              label: record.status == InspectionStatus.assigned
                  ? 'Start inspection'
                  : 'Continue inspection',
              icon: Icons.play_arrow_rounded,
              onPressed: () => openInspectionWorkflow(context, existing: record),
            )
          else
            const InfoBanner(
              icon: Icons.lock_outline,
              message:
                  'This record has been submitted to the zonal office and is '
                  'locked on this device.',
            ),
        ],
      ),
    );
  }

  Widget _summaryPanel(Inspection record) {
    return AppPanel(
      leadingStripe: statusStripeColor(record.status),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(child: Text(record.id, style: AppText.identifier)),
              StatusChip(record.status),
            ],
          ),
          const SizedBox(height: 6),
          Text(record.establishment, style: AppText.pageTitle),
          const SizedBox(height: 6),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              const Padding(
                padding: EdgeInsets.only(top: 2),
                child: Icon(
                  Icons.place_outlined,
                  size: 15,
                  color: AppColors.inkFaint,
                ),
              ),
              const SizedBox(width: 6),
              Expanded(child: Text(record.location, style: AppText.bodyMuted)),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: LabelledValue(label: 'Type', value: record.type.label),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text('Priority'.toUpperCase(), style: AppText.label),
                    const SizedBox(height: 5),
                    PriorityChip(record.priority),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: LabelledValue(
                  label: 'Assigned',
                  value: Fmt.date(record.assignedOn),
                ),
              ),
              Expanded(
                child: LabelledValue(
                  label: record.status.isClosed ? 'Submitted' : 'Due',
                  value: record.status.isClosed
                      ? Fmt.dayTime(record.submittedAt ?? record.updatedAt)
                      : Fmt.dayTime(record.dueOn),
                ),
              ),
            ],
          ),
          if (!record.status.isClosed) ...<Widget>[
            const SizedBox(height: 14),
            Row(
              children: <Widget>[
                Expanded(child: ProgressRail(percent: record.progressPercent)),
                const SizedBox(width: 10),
                Text(
                  '${record.progressPercent}%',
                  style: AppText.caption.copyWith(
                    fontWeight: FontWeight.w600,
                    color: AppColors.ink,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
