import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/buttons.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/fields.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../core/widgets/panels.dart';
import '../../data/models/inspector.dart';
import '../../state/auth_controller.dart';
import '../../state/inspection_controller.dart';

/// Official profile page — identity, posting and account actions only.
class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  Future<void> _confirmSignOut(BuildContext context) async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('Sign out?', style: AppText.sectionTitle),
        content: const Text(
          'Unsubmitted drafts stay on this device and will be available when '
          'you sign back in.',
          style: AppText.body,
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text(
              'Sign out',
              style: TextStyle(color: AppColors.danger),
            ),
          ),
        ],
      ),
    );

    if (confirmed != true || !context.mounted) return;
    await context.read<AuthController>().signOut();
  }

  void _notImplemented(BuildContext context, String feature) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$feature is handled by the zonal office.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final Inspector? officer =
        context.select<AuthController, Inspector?>((AuthController a) => a.inspector);
    final InspectionController controller = context.watch<InspectionController>();

    // Reachable only in the instant between sign-out and the auth gate
    // rebuilding. Render the chrome and a loading state rather than a blank
    // screen, so a slow frame never looks like a crash.
    if (officer == null) {
      return const Scaffold(
        backgroundColor: AppColors.canvas,
        appBar: GovHeader(
          title: 'Profile',
          subtitle: 'Officer account and posting',
          showMark: false,
        ),
        body: LoadingState(message: 'Loading your account'),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: const GovHeader(
        title: 'Profile',
        subtitle: 'Officer account and posting',
        showMark: false,
      ),
      body: ListView(
        padding: EdgeInsets.zero,
        children: <Widget>[
          _identityBand(officer),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 18, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                const SectionHeading('Officer particulars'),
                AppPanel(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: <Widget>[
                      _row('Inspector ID', officer.id, isFirst: true),
                      _row('Designation', officer.designation),
                      _row('Assigned zone', officer.zone),
                      _row('Reporting office', officer.office),
                      _row('Email', officer.email),
                      _row('Phone number', officer.phone),
                      _row('Account status', officer.accountStatus, isLast: true),
                    ],
                  ),
                ),

                const SizedBox(height: 22),
                const SectionHeading('Workload'),
                EqualHeightRow(
                  children: <Widget>[
                    SummaryTile(
                      label: 'Open assignments',
                      value: controller.assignedCount +
                          controller.inProgressCount,
                      accent: AppColors.info,
                    ),
                    SummaryTile(
                      label: 'Submitted',
                      value: controller.history.length,
                      accent: AppColors.success,
                    ),
                  ],
                ),

                const SizedBox(height: 22),
                const SectionHeading('Account'),
                AppPanel(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: <Widget>[
                      _action(
                        context,
                        icon: Icons.lock_outline,
                        label: 'Change password',
                        onTap: () => _showChangePasswordDialog(context),
                        isFirst: true,
                      ),
                      _action(
                        context,
                        icon: Icons.notifications_none,
                        label: 'Notifications',
                        onTap: () => _notImplemented(
                          context,
                          'Notification preferences',
                        ),
                      ),
                      _action(
                        context,
                        icon: Icons.tune,
                        label: 'App settings',
                        onTap: () => _notImplemented(context, 'App configuration'),
                      ),
                      _action(
                        context,
                        icon: Icons.info_outline,
                        label: 'About LM-GUARD',
                        onTap: () => showAboutDialog(
                          context: context,
                          applicationName: 'LM-GUARD Inspector',
                          applicationVersion: 'Version 1.0.0 · Demonstration build',
                          applicationLegalese:
                              'Field inspection application for Legal Metrology '
                              'inspecting officers. Records are held on the device '
                              'until submitted to the zonal office.',
                        ),
                        isLast: true,
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 18),
                SecondaryButton(
                  label: 'Sign out',
                  icon: Icons.logout,
                  foreground: AppColors.danger,
                  onPressed: () => _confirmSignOut(context),
                ),

                const SizedBox(height: 20),
                Center(
                  child: Text(
                    'LM-GUARD Inspector · v1.0.0',
                    style: AppText.caption.copyWith(color: AppColors.inkFaint),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _identityBand(Inspector officer) {
    return Container(
      width: double.infinity,
      color: AppColors.navy,
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 22),
      child: Row(
        children: <Widget>[
          OfficerAvatar(initials: Fmt.initials(officer.name), size: 54),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  officer.name,
                  style: const TextStyle(
                    fontSize: 18,
                    height: 1.25,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  officer.designation,
                  style: const TextStyle(
                    fontSize: 12.5,
                    color: AppColors.onNavyMuted,
                  ),
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.10),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: Colors.white.withOpacity(0.18)),
                  ),
                  child: Text(
                    officer.id,
                    style: const TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w600,
                      letterSpacing: 0.4,
                      color: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _row(
    String label,
    String value, {
    bool isFirst = false,
    bool isLast = false,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 12),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(
            color: isLast ? Colors.transparent : AppColors.border,
          ),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          SizedBox(
            width: 120,
            child: Text(label.toUpperCase(), style: AppText.label),
          ),
          Expanded(
            child: Text(
              value.isEmpty ? '—' : value,
              style: AppText.body.copyWith(fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }

  Widget _action(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    bool isFirst = false,
    bool isLast = false,
  }) {
    return InkWell(
      onTap: onTap,
      child: Container(
        height: AppSizes.touchTarget,
        padding: const EdgeInsets.symmetric(horizontal: 13),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: isLast ? Colors.transparent : AppColors.border,
            ),
          ),
        ),
        child: Row(
          children: <Widget>[
            Icon(icon, size: 18, color: AppColors.inkMuted),
            const SizedBox(width: 12),
            Expanded(child: Text(label, style: AppText.body)),
            const Icon(
              Icons.chevron_right,
              size: 19,
              color: AppColors.inkFaint,
            ),
          ],
        ),
      ),
    );
  }

  void _showChangePasswordDialog(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (BuildContext context) => const _ChangePasswordDialog(),
    );
  }
}

class _ChangePasswordDialog extends StatefulWidget {
  const _ChangePasswordDialog();

  @override
  State<_ChangePasswordDialog> createState() => _ChangePasswordDialogState();
}

class _ChangePasswordDialogState extends State<_ChangePasswordDialog> {
  final TextEditingController _currentPasswordController = TextEditingController();
  final TextEditingController _newPasswordController = TextEditingController();
  final TextEditingController _confirmPasswordController = TextEditingController();

  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;
  String? _error;
  bool _submitting = false;

  @override
  void dispose() {
    _currentPasswordController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final String currentPassword = _currentPasswordController.text.trim();
    final String newPassword = _newPasswordController.text.trim();
    final String confirmPassword = _confirmPasswordController.text.trim();

    if (currentPassword.isEmpty) {
      setState(() => _error = 'Please enter your current password');
      return;
    }
    if (newPassword.length < 6) {
      setState(() => _error = 'New password must be at least 6 characters');
      return;
    }
    if (newPassword != confirmPassword) {
      setState(() => _error = 'New passwords do not match');
      return;
    }

    setState(() {
      _error = null;
      _submitting = true;
    });

    final AuthController auth = context.read<AuthController>();
    final bool success = await auth.changePassword(currentPassword, newPassword);

    if (!mounted) return;

    if (success) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Password changed successfully'),
          backgroundColor: AppColors.success,
        ),
      );
    } else {
      setState(() {
        _submitting = false;
        _error = auth.error ?? 'Failed to change password. Check your current password.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.surface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      title: Row(
        children: const <Widget>[
          Icon(Icons.lock_outline, color: AppColors.navy),
          SizedBox(width: 10),
          Text('Change Password', style: AppText.sectionTitle),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            if (_error != null) ...<Widget>[
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppColors.danger.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: AppColors.danger),
                ),
                child: Text(
                  _error!,
                  style: const TextStyle(color: AppColors.danger, fontSize: 13),
                ),
              ),
              const SizedBox(height: 14),
            ],
            AppTextField(
              label: 'Current Password',
              required: true,
              controller: _currentPasswordController,
              obscureText: _obscureCurrent,
              suffixIcon: IconButton(
                icon: Icon(_obscureCurrent ? Icons.visibility_off : Icons.visibility, size: 20),
                onPressed: () => setState(() => _obscureCurrent = !_obscureCurrent),
              ),
            ),
            const SizedBox(height: 14),
            AppTextField(
              label: 'New Password',
              required: true,
              controller: _newPasswordController,
              obscureText: _obscureNew,
              suffixIcon: IconButton(
                icon: Icon(_obscureNew ? Icons.visibility_off : Icons.visibility, size: 20),
                onPressed: () => setState(() => _obscureNew = !_obscureNew),
              ),
            ),
            const SizedBox(height: 14),
            AppTextField(
              label: 'Confirm New Password',
              required: true,
              controller: _confirmPasswordController,
              obscureText: _obscureConfirm,
              suffixIcon: IconButton(
                icon: Icon(_obscureConfirm ? Icons.visibility_off : Icons.visibility, size: 20),
                onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
              ),
            ),
          ],
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: _submitting ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _submitting ? null : _submit,
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.navy,
            foregroundColor: Colors.white,
          ),
          child: _submitting
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Text('Change Password'),
        ),
      ],
    );
  }
}
