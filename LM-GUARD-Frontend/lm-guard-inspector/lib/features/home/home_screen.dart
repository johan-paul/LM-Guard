import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/buttons.dart';
import '../../core/widgets/chips.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../core/widgets/inspection_card.dart';
import '../../core/widgets/panels.dart';
import '../../data/models/enums.dart';
import '../../data/models/inspection.dart';
import '../../data/models/inspector.dart';
import '../../state/auth_controller.dart';
import '../../state/draft_controller.dart';
import '../../state/inspection_controller.dart';
import '../drafts/drafts_screen.dart';
import '../inspections/inspection_detail_screen.dart';
import '../shell/main_shell.dart';

/// "What work do I need to complete today?"
///
/// Every section below renders in all four data states — loading, loaded,
/// empty and failed — so the officer never sees a blank panel.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.onOpenTab});

  final ValueChanged<int> onOpenTab;

  /// Fallback identity used only in the instant between sign-in and the
  /// officer record arriving. Every field is a real string, so no screen ever
  /// reads a null.
  static const Inspector _unknownOfficer = Inspector(
    id: '—',
    name: 'Officer',
    designation: 'Inspecting Officer',
    zone: '—',
    email: '',
    phone: '',
    office: '',
  );

  @override
  Widget build(BuildContext context) {
    final Inspector officer = context.select<AuthController, Inspector?>(
          (AuthController a) => a.inspector,
        ) ??
        _unknownOfficer;
    final InspectionController controller =
        context.watch<InspectionController>();

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: GovHeader(
        title: 'LM-GUARD',
        subtitle: 'Field Inspection Application',
        actions: <Widget>[
          _NotificationBell(count: controller.overdueCount),
          Padding(
            padding: const EdgeInsets.only(right: 14, left: 2),
            child: GestureDetector(
              onTap: () => onOpenTab(3),
              child: OfficerAvatar(initials: Fmt.initials(officer.name)),
            ),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: controller.load,
        child: ListView(
          // Always scrollable so pull-to-refresh works even when a section is
          // showing an empty or error state.
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.zero,
          children: <Widget>[
            _WelcomeBand(officer: officer),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  const SectionHeading("Today's work summary"),
                  _SummaryGrid(
                    controller: controller,
                    onOpenTab: onOpenTab,
                  ),

                  const SizedBox(height: 22),
                  const SectionHeading('Next assigned inspection'),
                  _NextAssignmentSection(controller: controller),

                  const SizedBox(height: 22),
                  const SectionHeading('Quick actions'),
                  _QuickActions(
                    controller: controller,
                    onOpenTab: onOpenTab,
                  ),

                  const SizedBox(height: 22),
                  SectionHeading(
                    'Assigned inspections',
                    trailing: QuietButton(
                      label: 'View all',
                      onPressed: () => onOpenTab(1),
                    ),
                  ),
                  _AssignedList(controller: controller),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/* -------------------------------------------------------------------------
   Welcome
   ------------------------------------------------------------------------- */

class _WelcomeBand extends StatelessWidget {
  const _WelcomeBand({required this.officer});

  final Inspector officer;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      color: AppColors.navy,
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            '${Fmt.greeting()}, ${officer.name}',
            style: const TextStyle(
              fontSize: 19,
              height: 1.25,
              fontWeight: FontWeight.w600,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            Fmt.date(DateTime.now()),
            style: const TextStyle(
              fontSize: 12.5,
              color: AppColors.onNavyMuted,
            ),
          ),
          const SizedBox(height: 16),
          Container(height: 1, color: Colors.white.withOpacity(0.12)),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(child: NavyStat(label: 'Officer ID', value: officer.id)),
              Expanded(
                flex: 2,
                child: NavyStat(label: 'Assigned zone', value: officer.zone),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/* -------------------------------------------------------------------------
   Work summary
   ------------------------------------------------------------------------- */

class _SummaryGrid extends StatelessWidget {
  const _SummaryGrid({required this.controller, required this.onOpenTab});

  final InspectionController controller;
  final ValueChanged<int> onOpenTab;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: <Widget>[
        EqualHeightRow(
          children: <Widget>[
            SummaryTile(
              label: 'Assigned',
              value: controller.assignedCount,
              accent: AppColors.info,
              onTap: () => onOpenTab(1),
            ),
            SummaryTile(
              label: 'In progress',
              value: controller.inProgressCount,
              accent: AppColors.warning,
              onTap: () => onOpenTab(1),
            ),
          ],
        ),
        const SizedBox(height: 10),
        EqualHeightRow(
          children: <Widget>[
            SummaryTile(
              label: 'Pending drafts',
              value: controller.draftCount,
              accent: AppColors.neutral,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(builder: (_) => const DraftsScreen()),
              ),
            ),
            SummaryTile(
              label: 'Completed today',
              value: controller.completedTodayCount,
              accent: AppColors.success,
              onTap: () => onOpenTab(2),
            ),
          ],
        ),
      ],
    );
  }
}

/* -------------------------------------------------------------------------
   Next assigned inspection
   ------------------------------------------------------------------------- */

class _NextAssignmentSection extends StatelessWidget {
  const _NextAssignmentSection({required this.controller});

  final InspectionController controller;

  @override
  Widget build(BuildContext context) {
    if (controller.loading) {
      return const AppPanel(
        child: SizedBox(
          height: 116,
          child: LoadingState(message: 'Loading your next assignment'),
        ),
      );
    }

    if (controller.hasError) {
      return AppPanel(
        child: ErrorStateView(
          compact: true,
          message: controller.error ?? 'The work queue could not be loaded.',
          onRetry: controller.load,
        ),
      );
    }

    final Inspection? next = controller.nextAssignment;
    if (next == null) {
      return const AppPanel(
        child: EmptyState(
          compact: true,
          icon: Icons.task_alt_outlined,
          title: 'No open assignments',
          message:
              'Your queue is clear. New assignments from the zonal office will '
              'appear here.',
        ),
      );
    }

    return _NextAssignmentCard(inspection: next);
  }
}

class _NextAssignmentCard extends StatelessWidget {
  const _NextAssignmentCard({required this.inspection});

  final Inspection inspection;

  @override
  Widget build(BuildContext context) {
    final bool overdue = inspection.isOverdue;

    return AppPanel(
      leadingStripe: statusStripeColor(inspection.status),
      padding: const EdgeInsets.fromLTRB(14, 13, 14, 13),
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
          const SizedBox(height: 6),
          Text(
            inspection.establishment,
            style: AppText.sectionTitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 6),
          _IconLine(
            icon: Icons.place_outlined,
            text: inspection.location.isEmpty
                ? 'Location not recorded'
                : inspection.location,
          ),
          const SizedBox(height: 3),
          _IconLine(
            icon: Icons.schedule,
            text: '${Fmt.day(inspection.dueOn)} · ${Fmt.time(inspection.dueOn)}',
            tone: overdue ? AppColors.danger : null,
          ),
          const SizedBox(height: 3),
          _IconLine(
            icon: Icons.fact_check_outlined,
            text: inspection.type.label,
          ),
          const SizedBox(height: 12),
          const Divider(height: 1),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: SecondaryButton(
                  label: 'View inspection',
                  icon: Icons.description_outlined,
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) =>
                          InspectionDetailScreen(inspectionId: inspection.id),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: PrimaryButton(
                  label: inspection.status == InspectionStatus.assigned
                      ? 'Start'
                      : 'Continue',
                  icon: Icons.play_arrow_rounded,
                  onPressed: () =>
                      openInspectionWorkflow(context, existing: inspection),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _IconLine extends StatelessWidget {
  const _IconLine({required this.icon, required this.text, this.tone});

  final IconData icon;
  final String text;
  final Color? tone;

  @override
  Widget build(BuildContext context) {
    final Color color = tone ?? AppColors.inkMuted;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.only(top: 1.5),
          child: Icon(icon, size: 14, color: tone ?? AppColors.inkFaint),
        ),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            text,
            style: AppText.caption.copyWith(
              color: color,
              fontWeight: tone == null ? FontWeight.w400 : FontWeight.w600,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }
}

/* -------------------------------------------------------------------------
   Quick actions
   ------------------------------------------------------------------------- */

class _QuickActions extends StatelessWidget {
  const _QuickActions({required this.controller, required this.onOpenTab});

  final InspectionController controller;
  final ValueChanged<int> onOpenTab;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: <Widget>[
        EqualHeightRow(
          children: <Widget>[
            _ActionTile(
              icon: Icons.add_circle_outline,
              label: 'Start inspection',
              primary: true,
              onTap: () => openInspectionWorkflow(context),
            ),
            _ActionTile(
              icon: Icons.qr_code_scanner,
              label: 'Scan product',
              onTap: () => openInspectionWorkflow(
                context,
                initialStep: InspectionStep.scan,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        EqualHeightRow(
          children: <Widget>[
            _ActionTile(
              icon: Icons.assignment_outlined,
              label: 'Assigned work',
              onTap: () => onOpenTab(1),
            ),
            _ActionTile(
              icon: Icons.history,
              label: 'Inspection history',
              onTap: () => onOpenTab(2),
            ),
          ],
        ),
        if (controller.draftCount > 0) ...<Widget>[
          const SizedBox(height: 10),
          SecondaryButton(
            label: 'Continue ${Fmt.plural(controller.draftCount, 'draft')}',
            icon: Icons.edit_note_outlined,
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(builder: (_) => const DraftsScreen()),
            ),
          ),
        ],
      ],
    );
  }
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.label,
    required this.onTap,
    this.primary = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool primary;

  @override
  Widget build(BuildContext context) {
    if (!primary) {
      return AppPanel(
        onTap: onTap,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
        child: _content(AppColors.accent, AppColors.ink),
      );
    }

    // The one primary action on the screen carries the accent fill.
    return Material(
      color: AppColors.accent,
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
          child: _content(Colors.white, Colors.white),
        ),
      ),
    );
  }

  Widget _content(Color iconColor, Color textColor) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(icon, size: 20, color: iconColor),
        const SizedBox(height: 9),
        Text(
          label,
          style: AppText.body.copyWith(
            fontWeight: FontWeight.w600,
            color: textColor,
            height: 1.25,
          ),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}

/* -------------------------------------------------------------------------
   Assigned list
   ------------------------------------------------------------------------- */

class _AssignedList extends StatelessWidget {
  const _AssignedList({required this.controller});

  final InspectionController controller;

  @override
  Widget build(BuildContext context) {
    if (controller.loading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 34),
        child: LoadingState(message: 'Loading your work queue'),
      );
    }

    if (controller.hasError) {
      return AppPanel(
        child: ErrorStateView(
          compact: true,
          message: controller.error ?? 'The work queue could not be loaded.',
          onRetry: controller.load,
        ),
      );
    }

    final List<Inspection> rows = controller.followingAssignments;
    if (rows.isEmpty) {
      return const AppPanel(
        child: EmptyState(
          compact: true,
          icon: Icons.inbox_outlined,
          title: 'Nothing else queued',
          message:
              'Records you start or save as a draft will also be listed here.',
        ),
      );
    }

    // A fixed slice, rendered as plain children of the page's own ListView —
    // never a nested scrollable, which would fight the outer scroll.
    return Column(
      children: rows
          .take(4)
          .map(
            (Inspection inspection) => InspectionCard(
              inspection: inspection,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) =>
                      InspectionDetailScreen(inspectionId: inspection.id),
                ),
              ),
              actionLabel: actionLabelFor(inspection.status),
              onAction: () =>
                  openInspectionWorkflow(context, existing: inspection),
            ),
          )
          .toList(),
    );
  }
}

/* -------------------------------------------------------------------------
   Header actions
   ------------------------------------------------------------------------- */

class _NotificationBell extends StatelessWidget {
  const _NotificationBell({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      onPressed: () {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              count == 0
                  ? 'No overdue assignments.'
                  : '${Fmt.plural(count, 'assignment')} past the due date.',
            ),
          ),
        );
      },
      tooltip: 'Notifications',
      icon: Stack(
        clipBehavior: Clip.none,
        children: <Widget>[
          const Icon(Icons.notifications_none, size: 22),
          if (count > 0)
            Positioned(
              right: -1,
              top: -1,
              child: Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: AppColors.danger,
                  shape: BoxShape.circle,
                  border: Border.all(color: AppColors.navy, width: 1.4),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
