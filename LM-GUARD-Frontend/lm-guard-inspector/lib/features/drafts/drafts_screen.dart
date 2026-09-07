import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/buttons.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../core/widgets/panels.dart';
import '../../data/models/inspection.dart';
import '../../state/inspection_controller.dart';
import '../inspections/inspection_detail_screen.dart';
import '../shell/main_shell.dart';

/// Unfinished inspections held on the device.
class DraftsScreen extends StatelessWidget {
  const DraftsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final InspectionController controller = context.watch<InspectionController>();
    final List<Inspection> drafts = controller.drafts;

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: const RecordHeader(
        title: 'Draft inspections',
        subtitle: 'Records saved on this device',
      ),
      body: controller.loading
          ? const ScrollableCentre(
              child: LoadingState(message: 'Loading your drafts'),
            )
          : controller.hasError
          ? ScrollableCentre(
              child: ErrorStateView(
                message: controller.error ?? 'Your drafts could not be loaded.',
                onRetry: controller.load,
              ),
            )
          : drafts.isEmpty
          ? const ScrollableCentre(
              child: EmptyState(
                icon: Icons.edit_note_outlined,
                title: 'No drafts',
                message:
                    'Inspections you save before submitting will be held here '
                    'until they are completed.',
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
              itemCount: drafts.length + 1,
              itemBuilder: (BuildContext context, int index) {
                if (index == 0) {
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: InfoBanner(
                      message:
                          '${Fmt.plural(drafts.length, 'draft')} awaiting completion. '
                          'Drafts are not visible to the zonal office until submitted.',
                    ),
                  );
                }
                return _DraftCard(inspection: drafts[index - 1]);
              },
            ),
    );
  }
}

class _DraftCard extends StatelessWidget {
  const _DraftCard({required this.inspection});

  final Inspection inspection;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      margin: const EdgeInsets.only(bottom: 10),
      leadingStripe: AppColors.borderStrong,
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => InspectionDetailScreen(inspectionId: inspection.id),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(inspection.id, style: AppText.identifier),
          const SizedBox(height: 4),
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
                  label: 'Last updated',
                  value: Fmt.dayTime(inspection.updatedAt),
                ),
              ),
              Expanded(
                child: LabelledValue(
                  label: 'Progress',
                  value: '${inspection.progressPercent}%',
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ProgressRail(percent: inspection.progressPercent),
          const SizedBox(height: 14),
          PrimaryButton(
            label: 'Continue',
            icon: Icons.edit_outlined,
            onPressed: () =>
                openInspectionWorkflow(context, existing: inspection),
          ),
        ],
      ),
    );
  }
}
