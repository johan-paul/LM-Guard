import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// The LM-GUARD shield mark, drawn in code so no asset is required.
class LmGuardMark extends StatelessWidget {
  const LmGuardMark({super.key, this.size = 34, this.onNavy = true});

  final double size;
  final bool onNavy;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: onNavy ? Colors.white.withOpacity(0.10) : AppColors.navy,
        borderRadius: BorderRadius.circular(size * 0.22),
        border: Border.all(
          color: onNavy ? Colors.white.withOpacity(0.22) : AppColors.navy,
        ),
      ),
      child: Icon(
        Icons.verified_user_outlined,
        size: size * 0.56,
        color: onNavy ? Colors.white : Colors.white,
      ),
    );
  }
}

/// Navy header used on every primary screen: brand mark, title and the
/// officer's posting, with optional trailing actions.
class GovHeader extends StatelessWidget implements PreferredSizeWidget {
  const GovHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.actions,
    this.showMark = true,
    this.leading,
  });

  final String title;
  final String? subtitle;
  final List<Widget>? actions;
  final bool showMark;
  final Widget? leading;

  @override
  Size get preferredSize => const Size.fromHeight(60);

  @override
  Widget build(BuildContext context) {
    final String? sub = subtitle;

    return AppBar(
      automaticallyImplyLeading: false,
      toolbarHeight: 60,
      titleSpacing: 16,
      leading: leading,
      title: Row(
        children: <Widget>[
          if (showMark && leading == null) ...<Widget>[
            const LmGuardMark(size: 32),
            const SizedBox(width: 11),
          ],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 15.5,
                    height: 1.2,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
                if (sub != null)
                  Text(
                    sub,
                    style: const TextStyle(
                      fontSize: 11.5,
                      height: 1.3,
                      color: AppColors.onNavyMuted,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
          ),
        ],
      ),
      actions: actions,
    );
  }
}

/// Header for secondary screens — back arrow, title, optional record id.
class RecordHeader extends StatelessWidget implements PreferredSizeWidget {
  const RecordHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.actions,
    this.onBack,
  });

  final String title;
  final String? subtitle;
  final List<Widget>? actions;
  final VoidCallback? onBack;

  @override
  Size get preferredSize => const Size.fromHeight(60);

  @override
  Widget build(BuildContext context) {
    final String? sub = subtitle;

    return AppBar(
      toolbarHeight: 60,
      leading: IconButton(
        icon: const Icon(Icons.arrow_back, size: 21),
        onPressed: onBack ?? () => Navigator.of(context).maybePop(),
        tooltip: 'Back',
      ),
      titleSpacing: 0,
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Text(
            title,
            style: const TextStyle(
              fontSize: 15.5,
              height: 1.2,
              fontWeight: FontWeight.w600,
              color: Colors.white,
            ),
            overflow: TextOverflow.ellipsis,
          ),
          if (sub != null)
            Text(
              sub,
              style: const TextStyle(
                fontSize: 11.5,
                height: 1.3,
                color: AppColors.onNavyMuted,
              ),
              overflow: TextOverflow.ellipsis,
            ),
        ],
      ),
      actions: actions,
    );
  }
}

/// Circular initials badge for the signed-in officer.
class OfficerAvatar extends StatelessWidget {
  const OfficerAvatar({
    super.key,
    required this.initials,
    this.size = 34,
    this.onNavy = true,
  });

  final String initials;
  final double size;
  final bool onNavy;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: onNavy ? Colors.white.withOpacity(0.12) : AppColors.navy,
        shape: BoxShape.circle,
        border: Border.all(
          color: onNavy ? Colors.white.withOpacity(0.22) : Colors.transparent,
        ),
      ),
      child: Text(
        initials,
        style: TextStyle(
          fontSize: size * 0.34,
          fontWeight: FontWeight.w700,
          color: Colors.white,
        ),
      ),
    );
  }
}

/// Key/value line used inside navy surfaces.
class NavyStat extends StatelessWidget {
  const NavyStat({super.key, required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Text(
          label.toUpperCase(),
          style: AppText.label.copyWith(
            color: AppColors.onNavyMuted,
            fontSize: 10,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: const TextStyle(
            fontSize: 13.5,
            fontWeight: FontWeight.w600,
            color: Colors.white,
          ),
        ),
      ],
    );
  }
}
