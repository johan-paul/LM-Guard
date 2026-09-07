import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app_colors.dart';
import 'app_text_styles.dart';

/// Layout constants used across the application.
class AppSizes {
  const AppSizes._();

  static const double screenPadding = 16;
  static const double gap = 12;
  static const double gapLarge = 20;
  static const double radius = 8;
  static const double radiusSmall = 6;

  /// Minimum height for anything an officer taps while holding a device
  /// one-handed in the field.
  static const double touchTarget = 48;
}

/// Application theme.
///
/// Only long-stable [ThemeData] fields are set here; component surfaces are
/// styled by the widgets in `core/widgets` so the look does not drift between
/// Flutter releases.
class AppTheme {
  const AppTheme._();

  static const SystemUiOverlayStyle navyStatusBar = SystemUiOverlayStyle(
    statusBarColor: AppColors.navy,
    statusBarIconBrightness: Brightness.light,
    statusBarBrightness: Brightness.dark,
  );

  static ThemeData build() {
    const ColorScheme scheme = ColorScheme.light(
      primary: AppColors.accent,
      onPrimary: Colors.white,
      secondary: AppColors.navy,
      onSecondary: Colors.white,
      surface: AppColors.surface,
      onSurface: AppColors.ink,
      error: AppColors.danger,
      onError: Colors.white,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.canvas,
      dividerColor: AppColors.border,
      splashFactory: InkRipple.splashFactory,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.navy,
        foregroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        systemOverlayStyle: navyStatusBar,
        titleTextStyle: TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.border,
        thickness: 1,
        space: 1,
      ),
      textTheme: const TextTheme(
        titleLarge: AppText.pageTitle,
        titleMedium: AppText.sectionTitle,
        bodyMedium: AppText.body,
        bodySmall: AppText.caption,
        labelSmall: AppText.label,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surface,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
        hintStyle: const TextStyle(color: AppColors.inkFaint, fontSize: 14),
        border: _fieldBorder(AppColors.borderStrong),
        enabledBorder: _fieldBorder(AppColors.borderStrong),
        focusedBorder: _fieldBorder(AppColors.accent, width: 1.6),
        errorBorder: _fieldBorder(AppColors.danger),
        focusedErrorBorder: _fieldBorder(AppColors.danger, width: 1.6),
      ),
      textSelectionTheme: const TextSelectionThemeData(
        cursorColor: AppColors.accent,
        selectionHandleColor: AppColors.accent,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.accent,
        linearTrackColor: AppColors.border,
      ),
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: AppColors.navy,
        contentTextStyle: TextStyle(color: Colors.white, fontSize: 14),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  static OutlineInputBorder _fieldBorder(Color color, {double width = 1}) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
      borderSide: BorderSide(color: color, width: width),
    );
  }
}
