import 'package:flutter/material.dart';

import '../../data/models/enums.dart';
import '../theme/app_colors.dart';

/// A compact status pill. Colour carries meaning only — never decoration.
class AppChip extends StatelessWidget {
  const AppChip({
    super.key,
    required this.label,
    required this.foreground,
    required this.background,
    this.borderColor,
    this.icon,
    this.dense = false,
  });

  final String label;
  final Color foreground;
  final Color background;
  final Color? borderColor;
  final IconData? icon;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: dense ? 6 : 8,
        vertical: dense ? 2 : 3,
      ),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: borderColor ?? background),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          if (icon != null) ...<Widget>[
            Icon(icon, size: dense ? 11 : 12, color: foreground),
            const SizedBox(width: 4),
          ],
          Text(
            label.toUpperCase(),
            style: TextStyle(
              fontSize: dense ? 9.5 : 10.5,
              height: 1.3,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.5,
              color: foreground,
            ),
          ),
        ],
      ),
    );
  }
}

class StatusChip extends StatelessWidget {
  const StatusChip(this.status, {super.key, this.dense = false});

  final InspectionStatus status;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    late final Color fg;
    late final Color bg;
    late final IconData icon;

    switch (status) {
      case InspectionStatus.assigned:
        fg = AppColors.info;
        bg = AppColors.infoSoft;
        icon = Icons.assignment_outlined;
        break;
      case InspectionStatus.inProgress:
        fg = AppColors.warning;
        bg = AppColors.warningSoft;
        icon = Icons.pending_actions_outlined;
        break;
      case InspectionStatus.draft:
        fg = AppColors.neutral;
        bg = AppColors.neutralSoft;
        icon = Icons.edit_note_outlined;
        break;
      case InspectionStatus.submitted:
        fg = AppColors.navyMuted;
        bg = AppColors.surfaceMuted;
        icon = Icons.upload_file_outlined;
        break;
      case InspectionStatus.completed:
        fg = AppColors.success;
        bg = AppColors.successSoft;
        icon = Icons.check_circle_outline;
        break;
    }

    return AppChip(
      label: status.label,
      foreground: fg,
      background: bg,
      icon: icon,
      dense: dense,
    );
  }
}

class PriorityChip extends StatelessWidget {
  const PriorityChip(this.priority, {super.key, this.dense = false});

  final Priority priority;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    late final Color fg;
    late final Color bg;

    switch (priority) {
      case Priority.high:
        fg = AppColors.critical;
        bg = AppColors.dangerSoft;
        break;
      case Priority.medium:
        fg = AppColors.warning;
        bg = AppColors.warningSoft;
        break;
      case Priority.low:
        fg = AppColors.neutral;
        bg = AppColors.neutralSoft;
        break;
    }

    return AppChip(
      label: '${priority.label} priority',
      foreground: fg,
      background: bg,
      dense: dense,
    );
  }
}

class SeverityChip extends StatelessWidget {
  const SeverityChip(this.severity, {super.key, this.dense = false});

  final Severity severity;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    late final Color fg;
    late final Color bg;

    switch (severity) {
      case Severity.critical:
        fg = Colors.white;
        bg = AppColors.critical;
        break;
      case Severity.high:
        fg = AppColors.danger;
        bg = AppColors.dangerSoft;
        break;
      case Severity.medium:
        fg = AppColors.warning;
        bg = AppColors.warningSoft;
        break;
      case Severity.low:
        fg = AppColors.neutral;
        bg = AppColors.neutralSoft;
        break;
    }

    return AppChip(
      label: severity.label,
      foreground: fg,
      background: bg,
      borderColor: severity == Severity.critical ? AppColors.critical : null,
      dense: dense,
    );
  }
}

class CheckResultChip extends StatelessWidget {
  const CheckResultChip(this.result, {super.key});

  final CheckResult result;

  @override
  Widget build(BuildContext context) {
    late final Color fg;
    late final Color bg;
    late final IconData icon;

    switch (result) {
      case CheckResult.compliant:
        fg = AppColors.success;
        bg = AppColors.successSoft;
        icon = Icons.check;
        break;
      case CheckResult.nonCompliant:
        fg = AppColors.danger;
        bg = AppColors.dangerSoft;
        icon = Icons.close;
        break;
      case CheckResult.notApplicable:
        fg = AppColors.neutral;
        bg = AppColors.neutralSoft;
        icon = Icons.remove;
        break;
      case CheckResult.pending:
        fg = AppColors.inkFaint;
        bg = AppColors.surfaceMuted;
        icon = Icons.radio_button_unchecked;
        break;
    }

    return AppChip(
      label: result.label,
      foreground: fg,
      background: bg,
      icon: icon,
      dense: true,
    );
  }
}

/// Colour used for the leading stripe on assignment cards.
Color statusStripeColor(InspectionStatus status) {
  switch (status) {
    case InspectionStatus.assigned:
      return AppColors.info;
    case InspectionStatus.inProgress:
      return AppColors.warning;
    case InspectionStatus.draft:
      return AppColors.borderStrong;
    case InspectionStatus.submitted:
      return AppColors.navyMuted;
    case InspectionStatus.completed:
      return AppColors.success;
  }
}
