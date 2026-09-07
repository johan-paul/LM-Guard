import 'package:flutter/material.dart';

import '../../data/models/enums.dart';
import '../../data/models/inspection.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import '../utils/formatters.dart';
import 'buttons.dart';
import 'chips.dart';
import 'panels.dart';

/// The verb for the primary action on a record in a given state.
///
/// Shared so the home screen and the inspections list cannot drift apart.
String actionLabelFor(InspectionStatus status) {
  switch (status) {
    case InspectionStatus.assigned:
      return 'Start inspection';
    case InspectionStatus.inProgress:
      return 'Continue inspection';
    case InspectionStatus.draft:
      return 'Continue draft';
    case InspectionStatus.submitted:
    case InspectionStatus.completed:
      return 'View report';
  }
}

/// Official work-assignment card used on the home, inspections, drafts and
/// history screens.
class InspectionCard extends StatelessWidget {
  const InspectionCard({
    super.key,
    required this.inspection,
    this.onTap,
    this.actionLabel,
    this.onAction,
    this.showProgress = false,
  });

  final Inspection inspection;
  final VoidCallback? onTap;
  final String? actionLabel;
  final VoidCallback? onAction;
  final bool showProgress;

  @override
  Widget build(BuildContext context) {
    final bool overdue = inspection.isOverdue;
    // Hoisted so the null check and the read cannot disagree.
    final String? label = actionLabel;

    return AppPanel(
      onTap: onTap,
      leadingStripe: statusStripeColor(inspection.status),
      padding: const EdgeInsets.fromLTRB(13, 12, 13, 12),
      margin: const EdgeInsets.only(bottom: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: Text(inspection.id, style: AppText.identifier),
              ),
              PriorityChip(inspection.priority, dense: true),
            ],
          ),
          const SizedBox(height: 5),
          Text(
            inspection.establishment,
            style: AppText.recordTitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              const Padding(
                padding: EdgeInsets.only(top: 1.5),
                child: Icon(
                  Icons.place_outlined,
                  size: 14,
                  color: AppColors.inkFaint,
                ),
              ),
              const SizedBox(width: 5),
              Expanded(
                child: Text(
                  inspection.location,
                  style: AppText.caption,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          if (inspection.product != null) ...<Widget>[
            const SizedBox(height: 3),
            Row(
              children: <Widget>[
                const Icon(
                  Icons.inventory_2_outlined,
                  size: 14,
                  color: AppColors.inkFaint,
                ),
                const SizedBox(width: 5),
                Expanded(
                  child: Text(
                    inspection.productName,
                    style: AppText.caption,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ],
          if (showProgress) ...<Widget>[
            const SizedBox(height: 11),
            Row(
              children: <Widget>[
                Expanded(
                  child: ProgressRail(percent: inspection.progressPercent),
                ),
                const SizedBox(width: 9),
                Text(
                  '${inspection.progressPercent}%',
                  style: AppText.caption.copyWith(
                    fontWeight: FontWeight.w600,
                    color: AppColors.ink,
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 11),
          const Divider(height: 1),
          const SizedBox(height: 10),
          Row(
            children: <Widget>[
              StatusChip(inspection.status, dense: true),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _dueLabel(),
                  style: AppText.caption.copyWith(
                    color: overdue ? AppColors.danger : AppColors.inkMuted,
                    fontWeight: overdue ? FontWeight.w600 : FontWeight.w400,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          if (label != null && onAction != null) ...<Widget>[
            const SizedBox(height: 11),
            PrimaryButton(
              label: label,
              onPressed: onAction,
              icon: _actionIcon(),
            ),
          ],
        ],
      ),
    );
  }

  String _dueLabel() {
    if (inspection.status.isClosed) {
      final DateTime at = inspection.submittedAt ?? inspection.updatedAt;
      return 'Submitted ${Fmt.dayTime(at)}';
    }
    if (inspection.status == InspectionStatus.draft) {
      return 'Updated ${Fmt.dayTime(inspection.updatedAt)}';
    }
    return Fmt.due(inspection.dueOn);
  }

  IconData _actionIcon() {
    switch (inspection.status) {
      case InspectionStatus.assigned:
        return Icons.play_arrow_rounded;
      case InspectionStatus.inProgress:
      case InspectionStatus.draft:
        return Icons.edit_outlined;
      case InspectionStatus.submitted:
      case InspectionStatus.completed:
        return Icons.description_outlined;
    }
  }
}
