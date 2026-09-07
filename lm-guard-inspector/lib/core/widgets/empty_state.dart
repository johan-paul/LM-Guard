import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import 'buttons.dart';

/// Neutral placeholder for an empty list or filtered result set.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.title,
    required this.message,
    this.icon = Icons.inbox_outlined,
    this.action,
    this.compact = false,
  });

  final String title;
  final String message;
  final IconData icon;
  final Widget? action;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final Widget? trailingAction = action;

    return Center(
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: 28,
          vertical: compact ? 26 : 44,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Container(
              width: 46,
              height: 46,
              decoration: BoxDecoration(
                color: AppColors.surfaceMuted,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.border),
              ),
              child: Icon(icon, size: 22, color: AppColors.inkFaint),
            ),
            const SizedBox(height: 14),
            Text(
              title,
              textAlign: TextAlign.center,
              style: AppText.sectionTitle,
            ),
            const SizedBox(height: 5),
            Text(
              message,
              textAlign: TextAlign.center,
              style: AppText.caption,
            ),
            if (trailingAction != null) ...<Widget>[
              const SizedBox(height: 18),
              trailingAction,
            ],
          ],
        ),
      ),
    );
  }
}

/// Centres a message inside a scrollable that fills the available height.
///
/// Pull-to-refresh needs a scrollable child, so an empty or failed list must
/// still scroll — otherwise the officer's only way to retry is to restart the
/// application. Falls back to unconstrained height if the parent is unbounded,
/// so it can never introduce an infinite constraint.
class ScrollableCentre extends StatelessWidget {
  const ScrollableCentre({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        return SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: ConstrainedBox(
            constraints: BoxConstraints(
              minHeight: constraints.hasBoundedHeight ? constraints.maxHeight : 0,
            ),
            child: Center(child: child),
          ),
        );
      },
    );
  }
}

/// Shown when a load fails. Always offers a way back — a field officer with
/// no connectivity still needs to be able to retry without restarting.
class ErrorStateView extends StatelessWidget {
  const ErrorStateView({
    super.key,
    required this.message,
    this.onRetry,
    this.title = 'Could not load',
    this.compact = false,
  });

  final String message;
  final Future<void> Function()? onRetry;
  final String title;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final Future<void> Function()? retry = onRetry;

    return Center(
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: 28,
          vertical: compact ? 26 : 44,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Container(
              width: 46,
              height: 46,
              decoration: BoxDecoration(
                color: AppColors.dangerSoft,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.dangerSoft),
              ),
              child: const Icon(
                Icons.cloud_off_outlined,
                size: 22,
                color: AppColors.danger,
              ),
            ),
            const SizedBox(height: 14),
            Text(title, textAlign: TextAlign.center, style: AppText.sectionTitle),
            const SizedBox(height: 5),
            Text(message, textAlign: TextAlign.center, style: AppText.caption),
            if (retry != null) ...<Widget>[
              const SizedBox(height: 18),
              SecondaryButton(
                label: 'Try again',
                icon: Icons.refresh,
                expand: false,
                onPressed: () => retry(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Simple centred loading indicator with a caption.
class LoadingState extends StatelessWidget {
  const LoadingState({super.key, this.message = 'Loading'});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2.4),
          ),
          const SizedBox(height: 12),
          Text(message, style: AppText.caption),
        ],
      ),
    );
  }
}
