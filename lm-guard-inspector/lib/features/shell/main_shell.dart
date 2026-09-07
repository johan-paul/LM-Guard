import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../data/models/enums.dart';
import '../../data/models/inspection.dart';
import '../../data/repositories/inspection_repository.dart';
import '../../data/services/evidence_service.dart';
import '../../state/auth_controller.dart';
import '../../state/draft_controller.dart';
import '../../state/inspection_controller.dart';
import '../history/history_screen.dart';
import '../home/home_screen.dart';
import '../inspections/my_inspections_screen.dart';
import '../new_inspection/new_inspection_screen.dart';
import '../profile/profile_screen.dart';

/// Bottom-navigation shell. The centre slot is an action rather than a tab —
/// starting an inspection opens the workflow over the current screen.
class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // Only this officer's own inspections - the backend also enforces this
      // server-side, this is just which records to ask for.
      final String? inspectorId = context.read<AuthController>().inspector?.id;
      context.read<InspectionController>().load(inspectorId: inspectorId);
    });
  }

  void _onNavTap(int index) {
    if (index == 2) {
      openInspectionWorkflow(context);
      return;
    }
    setState(() => _tab = index > 2 ? index - 1 : index);
  }

  int get _navIndex => _tab >= 2 ? _tab + 1 : _tab;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: IndexedStack(
        index: _tab,
        children: <Widget>[
          HomeScreen(onOpenTab: (int index) => setState(() => _tab = index)),
          const MyInspectionsScreen(),
          const HistoryScreen(),
          const ProfileScreen(),
        ],
      ),
      bottomNavigationBar: FieldNavBar(
        index: _navIndex,
        onTap: _onNavTap,
      ),
    );
  }
}

/// Opens the inspection workflow for a new or existing record.
///
/// [initialStep] lets a caller jump straight to a stage — the home screen's
/// "Scan product" action opens at product identification.
///
/// A brand-new inspection (no [existing]) is created on the backend up
/// front, via [InspectionRepository.createInspection] - the checklist,
/// findings and officer-evidence endpoints all need a real, server-assigned
/// inspection id to attach to, which a purely local placeholder id could
/// never provide. This is exactly the "Inspector directly starts an
/// inspection" entry point: no admin, no assignment, the inspector becomes
/// the record's inspector on the backend automatically.
Future<void> openInspectionWorkflow(
  BuildContext context, {
  Inspection? existing,
  InspectionStep initialStep = InspectionStep.information,
}) async {
  final InspectionController controller = context.read<InspectionController>();
  final InspectionRepository repository = context.read<InspectionRepository>();
  final EvidenceService evidenceService = context.read<EvidenceService>();

  Inspection working;
  if (existing != null) {
    working = await controller.beginWork(existing);
  } else {
    final DateTime now = DateTime.now();
    working = await repository.createInspection(
      establishment: '',
      location: '',
      zone: '',
      type: InspectionType.routine,
      priority: Priority.medium,
      scheduledFor: now,
    );
    controller.adopt(working);
  }

  if (!context.mounted) return;

  await Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => NewInspectionScreen(
        inspection: working,
        repository: repository,
        evidenceService: evidenceService,
        initialStep: initialStep,
        onCompleted: controller.adopt,
      ),
    ),
  );
}

/// Professional five-slot navigation bar.
class FieldNavBar extends StatelessWidget {
  const FieldNavBar({super.key, required this.index, required this.onTap});

  final int index;
  final ValueChanged<int> onTap;

  static const List<_NavSpec> _items = <_NavSpec>[
    _NavSpec('Home', Icons.home_outlined, Icons.home),
    _NavSpec('Inspections', Icons.assignment_outlined, Icons.assignment),
    _NavSpec('New', Icons.add, Icons.add),
    _NavSpec('History', Icons.history_outlined, Icons.history),
    _NavSpec('Profile', Icons.person_outline, Icons.person),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 62,
          child: Row(
            children: List<Widget>.generate(_items.length, (int i) {
              final _NavSpec spec = _items[i];
              if (i == 2) {
                return Expanded(child: _actionSlot(spec));
              }
              return Expanded(child: _tabSlot(spec, i));
            }),
          ),
        ),
      ),
    );
  }

  Widget _tabSlot(_NavSpec spec, int slot) {
    final bool active = slot == index;
    final Color tint = active ? AppColors.accent : AppColors.inkMuted;

    return InkWell(
      onTap: () => onTap(slot),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Icon(active ? spec.activeIcon : spec.icon, size: 21, color: tint),
          const SizedBox(height: 3),
          Text(
            spec.label,
            style: TextStyle(
              fontSize: 10.5,
              height: 1.2,
              fontWeight: active ? FontWeight.w600 : FontWeight.w500,
              color: tint,
            ),
          ),
        ],
      ),
    );
  }

  Widget _actionSlot(_NavSpec spec) {
    return InkWell(
      onTap: () => onTap(2),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Container(
            width: 40,
            height: 26,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: AppColors.accent,
              borderRadius: BorderRadius.circular(5),
            ),
            child: const Icon(Icons.add, size: 18, color: Colors.white),
          ),
          const SizedBox(height: 3),
          const Text(
            'New',
            style: TextStyle(
              fontSize: 10.5,
              height: 1.2,
              fontWeight: FontWeight.w600,
              color: AppColors.accent,
            ),
          ),
        ],
      ),
    );
  }
}

class _NavSpec {
  const _NavSpec(this.label, this.icon, this.activeIcon);

  final String label;
  final IconData icon;
  final IconData activeIcon;
}
