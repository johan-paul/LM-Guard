import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'data/repositories/auth_repository.dart';
import 'data/repositories/inspection_repository.dart';
import 'data/services/api_client.dart';
import 'data/services/evidence_service.dart';
import 'features/auth/login_screen.dart';
import 'features/shell/main_shell.dart';
import 'state/auth_controller.dart';
import 'state/inspection_controller.dart';

/// Application root.
class LmGuardInspectorApp extends StatelessWidget {
  const LmGuardInspectorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider<ApiClient>(create: (_) => ApiClient()),
        Provider<InspectionRepository>(
          create: (BuildContext context) => ApiInspectionRepository(context.read<ApiClient>()),
        ),
        Provider<EvidenceService>(create: (_) => CameraEvidenceService()),
        ChangeNotifierProvider<AuthController>(
          create: (BuildContext context) =>
              AuthController(ApiAuthRepository(context.read<ApiClient>())),
        ),
        ChangeNotifierProvider<InspectionController>(
          create: (BuildContext context) => InspectionController(
            context.read<InspectionRepository>(),
          ),
        ),
      ],
      child: MaterialApp(
        title: 'LM-GUARD Inspector',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.build(),
        builder: _frameForWideViewports,
        home: const _AuthGate(),
      ),
    );
  }
}

/// Keeps the field application at handset proportions on a wide viewport.
///
/// This is a mobile application; stretching a one-handed layout across a
/// 1900 px desktop browser window makes every row unreadable. Below the
/// breakpoint the constraint has no effect at all, so a handset renders
/// exactly as before.
Widget _frameForWideViewports(BuildContext context, Widget? child) {
  final Widget content = child ?? const SizedBox.shrink();
  if (MediaQuery.sizeOf(context).width <= _kHandsetWidth) return content;

  return ColoredBox(
    color: AppColors.shell,
    child: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: _kHandsetWidth),
        child: Container(
          decoration: const BoxDecoration(
            color: AppColors.canvas,
            border: Border.symmetric(
              vertical: BorderSide(color: AppColors.borderStrong),
            ),
          ),
          child: content,
        ),
      ),
    ),
  );
}

const double _kHandsetWidth = 520;

class _AuthGate extends StatelessWidget {
  const _AuthGate();

  @override
  Widget build(BuildContext context) {
    final bool signedIn =
        context.select<AuthController, bool>((AuthController a) => a.isSignedIn);
    return signedIn ? const MainShell() : const LoginScreen();
  }
}
