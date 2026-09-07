import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Type scale for the field application.
///
/// Sizes are deliberately restrained and readable outdoors: no oversized
/// display type, no decorative weights.
class AppText {
  const AppText._();

  /// Screen title, e.g. "My Inspections".
  static const TextStyle pageTitle = TextStyle(
    fontSize: 22,
    height: 1.25,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.2,
    color: AppColors.inkStrong,
  );

  /// Card or section heading.
  static const TextStyle sectionTitle = TextStyle(
    fontSize: 15,
    height: 1.3,
    fontWeight: FontWeight.w600,
    color: AppColors.inkStrong,
  );

  /// Primary record line, e.g. an establishment name.
  static const TextStyle recordTitle = TextStyle(
    fontSize: 15,
    height: 1.35,
    fontWeight: FontWeight.w600,
    color: AppColors.inkStrong,
  );

  /// Standard body copy.
  static const TextStyle body = TextStyle(
    fontSize: 14,
    height: 1.45,
    color: AppColors.ink,
  );

  /// Secondary body copy.
  static const TextStyle bodyMuted = TextStyle(
    fontSize: 14,
    height: 1.45,
    color: AppColors.inkMuted,
  );

  /// Supporting detail.
  static const TextStyle caption = TextStyle(
    fontSize: 12.5,
    height: 1.4,
    color: AppColors.inkMuted,
  );

  /// Uppercase field label / micro heading.
  static const TextStyle label = TextStyle(
    fontSize: 11,
    height: 1.4,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.7,
    color: AppColors.inkMuted,
  );

  /// Record identifiers — tabular, slightly tracked.
  static const TextStyle identifier = TextStyle(
    fontSize: 12.5,
    height: 1.35,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.3,
    fontFeatures: <FontFeature>[FontFeature.tabularFigures()],
    color: AppColors.inkMuted,
  );

  /// Large operational figure on summary tiles.
  static const TextStyle figure = TextStyle(
    fontSize: 24,
    height: 1.15,
    fontWeight: FontWeight.w700,
    fontFeatures: <FontFeature>[FontFeature.tabularFigures()],
    color: AppColors.inkStrong,
  );

  /// Button label.
  static const TextStyle button = TextStyle(
    fontSize: 14.5,
    height: 1.2,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.2,
  );
}
