import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/buttons.dart';
import '../../core/widgets/fields.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../core/widgets/panels.dart';
import '../../data/mock/mock_data.dart';
import '../../state/auth_controller.dart';

/// Official sign-in for inspecting officers.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _idController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _idController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final AuthController auth = context.read<AuthController>();
    await auth.signIn(_idController.text, _passwordController.text);
  }

  void _fillDemoCredentials() {
    setState(() {
      _idController.text = MockData.demoInspectorId;
      _passwordController.text = MockData.demoPassword;
    });
    context.read<AuthController>().clearError();
  }

  void _showForgotPassword() {
    showDialog<void>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppSizes.radius),
        ),
        title: const Text('Password reset', style: AppText.sectionTitle),
        content: const Text(
          'Password resets are issued by the zonal office. Contact your '
          'controlling officer with your inspector ID to have credentials '
          'reissued.',
          style: AppText.body,
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final AuthController auth = context.watch<AuthController>();

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: AppTheme.navyStatusBar,
      child: Scaffold(
        backgroundColor: AppColors.canvas,
        body: SafeArea(
          bottom: false,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                _brandHeader(),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 22, 20, 24),
                  child: _form(auth),
                ),
                _footer(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _brandHeader() {
    return Container(
      color: AppColors.navy,
      padding: const EdgeInsets.fromLTRB(20, 34, 20, 30),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              const LmGuardMark(size: 46),
              const SizedBox(width: 13),
              // Expanded so the wordmark block takes the remaining width and
              // wraps, instead of claiming its intrinsic width and overflowing
              // the row on a narrow device or at a large text scale.
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    const Text(
                      'LM-GUARD',
                      style: TextStyle(
                        fontSize: 22,
                        height: 1.15,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.4,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Inspection & Compliance\nManagement System',
                      style: TextStyle(
                        fontSize: 12,
                        height: 1.35,
                        color: Colors.white.withOpacity(0.72),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 26),
          Container(height: 1, color: Colors.white.withOpacity(0.12)),
          const SizedBox(height: 16),
          Text(
            'GOVERNMENT OF INDIA · LEGAL METROLOGY',
            style: AppText.label.copyWith(
              color: AppColors.onNavyMuted,
              fontSize: 10,
              letterSpacing: 1.1,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Field Inspection Application',
            style: TextStyle(
              fontSize: 14.5,
              fontWeight: FontWeight.w500,
              color: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  Widget _form(AuthController auth) {
    final String? signInError = auth.error;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        Text('Inspector login'.toUpperCase(), style: AppText.label),
        const SizedBox(height: 6),
        const Text(
          'Sign in to collect your assigned inspections.',
          style: AppText.bodyMuted,
        ),
        const SizedBox(height: 20),

        if (signInError != null) ...<Widget>[
          InfoBanner(
            message: signInError,
            icon: Icons.error_outline,
            tone: BannerTone.danger,
          ),
          const SizedBox(height: 16),
        ],

        AppTextField(
          label: 'Email',
          controller: _idController,
          hint: 'you@lmguard.gov.in',
          required: true,
          keyboardType: TextInputType.emailAddress,
          textCapitalization: TextCapitalization.none,
          prefixIcon: Icons.badge_outlined,
          onChanged: (_) => auth.clearError(),
        ),
        const SizedBox(height: 16),

        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const FieldLabel('Password', required: true),
            const SizedBox(height: 6),
            TextField(
              controller: _passwordController,
              obscureText: _obscure,
              style: AppText.body,
              onChanged: (_) => auth.clearError(),
              onSubmitted: (_) => _submit(),
              decoration: InputDecoration(
                hintText: 'Enter your password',
                prefixIcon: const Icon(
                  Icons.lock_outline,
                  size: 18,
                  color: AppColors.inkFaint,
                ),
                suffixIcon: IconButton(
                  icon: Icon(
                    _obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                    size: 19,
                    color: AppColors.inkMuted,
                  ),
                  onPressed: () => setState(() => _obscure = !_obscure),
                  tooltip: _obscure ? 'Show password' : 'Hide password',
                ),
              ),
            ),
          ],
        ),

        const SizedBox(height: 10),
        Align(
          alignment: Alignment.centerRight,
          child: QuietButton(
            label: 'Forgot password?',
            onPressed: _showForgotPassword,
            color: AppColors.inkMuted,
          ),
        ),
        const SizedBox(height: 14),

        PrimaryButton(
          label: 'Sign in',
          icon: Icons.login,
          busy: auth.busy,
          onPressed: _submit,
        ),

        const SizedBox(height: 20),
        _demoCredentials(),
      ],
    );
  }

  Widget _demoCredentials() {
    return AppPanel(
      padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text('Demonstration account'.toUpperCase(), style: AppText.label),
                const SizedBox(height: 4),
                Text(
                  '${MockData.demoInspectorId}  ·  ${MockData.demoPassword}',
                  style: AppText.caption.copyWith(
                    color: AppColors.ink,
                    letterSpacing: 0.2,
                  ),
                ),
              ],
            ),
          ),
          QuietButton(label: 'Use', onPressed: _fillDemoCredentials),
        ],
      ),
    );
  }

  Widget _footer() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 22),
      decoration: const BoxDecoration(
        border: Border(top: BorderSide(color: AppColors.border)),
        color: AppColors.surface,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          const Icon(Icons.shield_outlined, size: 16, color: AppColors.inkFaint),
          const SizedBox(width: 9),
          Expanded(
            child: Text(
              'Secure access for authorised inspection officers only. All '
              'activity on this device is recorded against your officer ID.',
              style: AppText.caption.copyWith(height: 1.45),
            ),
          ),
        ],
      ),
    );
  }
}
