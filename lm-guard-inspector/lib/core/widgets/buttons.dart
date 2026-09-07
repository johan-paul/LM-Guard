import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import '../theme/app_theme.dart';

/// Solid action button. Full width by default — field officers tap these
/// one-handed, so the target stays large.
class PrimaryButton extends StatelessWidget {
  const PrimaryButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
    this.busy = false,
    this.expand = true,
    this.color = AppColors.accent,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool busy;
  final bool expand;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final bool disabled = onPressed == null || busy;

    // A Row lays its non-flex children out with an unbounded main axis, so a
    // plain Text never wraps or shrinks and overflows the button on a narrow
    // handset — two of these side by side at 360 dp is the tight case. When
    // the button fills its slot the label is made flexible so it ellipsises
    // instead. When it hugs its content (expand: false) the Row sizes to the
    // text, so no flex child is wanted — and the incoming width may itself be
    // unbounded, which is where Flexible would be illegal.
    final Widget labelText = Text(
      label,
      style: AppText.button.copyWith(color: Colors.white),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.center,
    );

    final Widget child = Row(
      mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        if (busy)
          const SizedBox(
            width: 16,
            height: 16,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
            ),
          )
        else if (icon != null)
          Icon(icon, size: 18, color: Colors.white),
        if (busy || icon != null) const SizedBox(width: 9),
        if (expand) Flexible(child: labelText) else labelText,
      ],
    );

    return SizedBox(
      width: expand ? double.infinity : null,
      height: AppSizes.touchTarget,
      child: Material(
        // ignore: deprecated_member_use
        color: disabled ? color.withOpacity(0.45) : color,
        borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
        child: InkWell(
          onTap: disabled ? null : onPressed,
          borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Center(child: child),
          ),
        ),
      ),
    );
  }
}

/// Outlined action button for secondary choices.
class SecondaryButton extends StatelessWidget {
  const SecondaryButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
    this.expand = true,
    this.foreground = AppColors.ink,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool expand;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    final bool disabled = onPressed == null;
    final Color tint = disabled ? AppColors.inkFaint : foreground;

    // See PrimaryButton — same overflow guard.
    final Widget labelText = Text(
      label,
      style: AppText.button.copyWith(color: tint),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.center,
    );

    return SizedBox(
      width: expand ? double.infinity : null,
      height: AppSizes.touchTarget,
      child: Material(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
        child: InkWell(
          onTap: onPressed,
          borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
          child: Ink(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
              border: Border.all(color: AppColors.borderStrong),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  if (icon != null) ...<Widget>[
                    Icon(icon, size: 18, color: tint),
                    const SizedBox(width: 9),
                  ],
                  if (expand) Flexible(child: labelText) else labelText,
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Quiet inline action, used inside cards and list rows.
class QuietButton extends StatelessWidget {
  const QuietButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.color = AppColors.accent,
  });

  final String label;
  final VoidCallback onPressed;
  final IconData? icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(4),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            if (icon != null) ...<Widget>[
              Icon(icon, size: 15, color: color),
              const SizedBox(width: 5),
            ],
            Text(
              label,
              style: TextStyle(
                fontSize: 13.5,
                fontWeight: FontWeight.w600,
                color: color,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
