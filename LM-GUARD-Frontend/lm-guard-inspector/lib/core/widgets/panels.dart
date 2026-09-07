import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import '../theme/app_theme.dart';

/// White surface with a hairline border — the base container for every card.
class AppPanel extends StatelessWidget {
  const AppPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(14),
    this.onTap,
    this.leadingStripe,
    this.margin,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final Color? leadingStripe;
  final EdgeInsetsGeometry? margin;

  @override
  Widget build(BuildContext context) {
    final Color? stripe = leadingStripe;

    // The status stripe is painted as a left border rather than as a stretched
    // Row child.
    //
    // A horizontal Row with CrossAxisAlignment.stretch lays every child out at
    // `constraints.maxHeight`, which is `double.infinity` inside a ListView or
    // any other vertically unbounded parent. That is an unsatisfiable
    // constraint: the child reports an infinite size, the layout assertion
    // fires, and the enclosing sliver aborts — leaving a blank content area.
    //
    // A border takes its height from the child it decorates, so the panel is
    // safe at any height, bounded or not. Container folds the border's
    // dimensions into the child's padding, so the content is inset by the
    // stripe width automatically.
    final Widget content = stripe == null
        ? Padding(padding: padding, child: child)
        : Container(
            decoration: BoxDecoration(
              border: Border(left: BorderSide(color: stripe, width: 4)),
            ),
            padding: padding,
            child: child,
          );

    // One Material provides the surface, the hairline border, the rounded
    // clip and — when the panel is tappable — a visible ink response. The
    // previous arrangement placed the Material *behind* an opaque container,
    // so the ripple never showed.
    final Widget surface = Material(
      color: AppColors.surface,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        side: const BorderSide(color: AppColors.border),
        borderRadius: BorderRadius.circular(AppSizes.radius),
      ),
      child: onTap == null ? content : InkWell(onTap: onTap, child: content),
    );

    final EdgeInsetsGeometry? outerMargin = margin;
    return outerMargin == null
        ? surface
        : Padding(padding: outerMargin, child: surface);
  }
}

/// A row whose children all take the height of the tallest one.
///
/// Use this instead of `Row(crossAxisAlignment: CrossAxisAlignment.stretch)`.
/// A stretched Row forces every child to `constraints.maxHeight`, which is
/// infinite inside a ListView or any unbounded parent and aborts the layout.
/// [IntrinsicHeight] measures the children first and then imposes a finite
/// tight height, so the same visual result is reached safely.
class EqualHeightRow extends StatelessWidget {
  const EqualHeightRow({super.key, required this.children, this.spacing = 10});

  final List<Widget> children;
  final double spacing;

  @override
  Widget build(BuildContext context) {
    if (children.isEmpty) return const SizedBox.shrink();

    final List<Widget> spaced = <Widget>[];
    for (int i = 0; i < children.length; i++) {
      if (i > 0) spaced.add(SizedBox(width: spacing));
      spaced.add(Expanded(child: children[i]));
    }

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: spaced,
      ),
    );
  }
}

/// Uppercase micro-heading above a group of content.
class SectionHeading extends StatelessWidget {
  const SectionHeading(
    this.title, {
    super.key,
    this.trailing,
    this.padding = const EdgeInsets.only(bottom: 10),
  });

  final String title;
  final Widget? trailing;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final Widget? trailingWidget = trailing;

    return Padding(
      padding: padding,
      child: Row(
        children: <Widget>[
          Expanded(child: Text(title.toUpperCase(), style: AppText.label)),
          if (trailingWidget != null) trailingWidget,
        ],
      ),
    );
  }
}

/// Label above a value — the standard read-only field presentation.
class LabelledValue extends StatelessWidget {
  const LabelledValue({
    super.key,
    required this.label,
    required this.value,
    this.valueStyle,
  });

  final String label;
  final String value;
  final TextStyle? valueStyle;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(label.toUpperCase(), style: AppText.label),
        const SizedBox(height: 3),
        Text(
          value.isEmpty ? '—' : value,
          style: valueStyle ??
              AppText.body.copyWith(fontWeight: FontWeight.w500),
        ),
      ],
    );
  }
}

/// Compact operational figure used on the home screen work summary.
class SummaryTile extends StatelessWidget {
  const SummaryTile({
    super.key,
    required this.label,
    required this.value,
    this.accent,
    this.onTap,
  });

  final String label;
  final int value;
  final Color? accent;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      onTap: onTap,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Text(
            value.toString(),
            style: AppText.figure.copyWith(color: accent ?? AppColors.inkStrong),
          ),
          const SizedBox(height: 2),
          Text(
            label.toUpperCase(),
            style: AppText.label.copyWith(fontSize: 10, letterSpacing: 0.6),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}

/// Neutral or cautionary strip used for guidance and validation messages.
class InfoBanner extends StatelessWidget {
  const InfoBanner({
    super.key,
    required this.message,
    this.icon = Icons.info_outline,
    this.tone = BannerTone.neutral,
  });

  final String message;
  final IconData icon;
  final BannerTone tone;

  @override
  Widget build(BuildContext context) {
    late final Color fg;
    late final Color bg;
    late final Color border;

    switch (tone) {
      case BannerTone.neutral:
        fg = AppColors.inkMuted;
        bg = AppColors.surfaceMuted;
        border = AppColors.border;
        break;
      case BannerTone.info:
        fg = AppColors.accentDark;
        bg = AppColors.accentSoft;
        border = AppColors.accentSoft;
        break;
      case BannerTone.warning:
        fg = AppColors.warning;
        bg = AppColors.warningSoft;
        border = AppColors.warningSoft;
        break;
      case BannerTone.danger:
        fg = AppColors.danger;
        bg = AppColors.dangerSoft;
        border = AppColors.dangerSoft;
        break;
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
        border: Border.all(color: border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Icon(icon, size: 16, color: fg),
          const SizedBox(width: 9),
          Expanded(
            child: Text(
              message,
              style: AppText.caption.copyWith(color: fg, height: 1.45),
            ),
          ),
        ],
      ),
    );
  }
}

enum BannerTone { neutral, info, warning, danger }

/// Thin progress rail used on drafts and the workflow header.
class ProgressRail extends StatelessWidget {
  const ProgressRail({
    super.key,
    required this.percent,
    this.color = AppColors.accent,
    this.height = 4,
  });

  final int percent;
  final Color color;
  final double height;

  @override
  Widget build(BuildContext context) {
    final double value = (percent.clamp(0, 100)) / 100;
    return ClipRRect(
      borderRadius: BorderRadius.circular(height),
      child: LinearProgressIndicator(
        value: value,
        minHeight: height,
        backgroundColor: AppColors.border,
        valueColor: AlwaysStoppedAnimation<Color>(color),
      ),
    );
  }
}
