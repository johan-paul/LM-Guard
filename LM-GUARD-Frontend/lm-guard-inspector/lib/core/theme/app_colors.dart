import 'package:flutter/material.dart';

/// LM-GUARD colour tokens.
///
/// The brand navy and accent blue are shared with the administrator web
/// console so both applications read as one system. Status colours carry
/// meaning only — they are never used for decoration.
class AppColors {
  const AppColors._();

  // Brand — shared with the admin console
  static const Color navy = Color(0xFF0B1F33);
  static const Color navyMuted = Color(0xFF16324F);
  static const Color navyLight = Color(0xFF1D4066);
  static const Color accent = Color(0xFF2563EB);
  static const Color accentDark = Color(0xFF1D4ED8);
  static const Color accentSoft = Color(0xFFEFF4FF);

  // Neutral surfaces
  static const Color canvas = Color(0xFFF6F8FB);
  static const Color surface = Color(0xFFFFFFFF);

  /// Backdrop behind the application frame on wide viewports (Flutter web on
  /// a desktop browser). Never visible on a handset.
  static const Color shell = Color(0xFFDCE3EC);
  static const Color surfaceMuted = Color(0xFFF2F4F7);
  static const Color border = Color(0xFFE4E7EC);
  static const Color borderStrong = Color(0xFFD0D5DD);

  // Text
  static const Color ink = Color(0xFF172033);
  static const Color inkStrong = Color(0xFF0F1729);
  static const Color inkMuted = Color(0xFF667085);
  static const Color inkFaint = Color(0xFF8A93A5);
  static const Color onNavy = Color(0xFFFFFFFF);
  static const Color onNavyMuted = Color(0xFFB6C4D4);

  // Status — compliance and workflow meaning only
  static const Color success = Color(0xFF16A34A);
  static const Color successSoft = Color(0xFFECFDF3);
  static const Color warning = Color(0xFFD97706);
  static const Color warningSoft = Color(0xFFFFFAEB);
  static const Color danger = Color(0xFFDC2626);
  static const Color dangerSoft = Color(0xFFFEF3F2);
  static const Color critical = Color(0xFFB42318);
  static const Color info = Color(0xFF2563EB);
  static const Color infoSoft = Color(0xFFEFF4FF);
  static const Color neutral = Color(0xFF475467);
  static const Color neutralSoft = Color(0xFFF2F4F7);
}
