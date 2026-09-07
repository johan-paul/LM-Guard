import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/fields.dart';
import '../../../core/widgets/panels.dart';
import '../../../data/mock/mock_data.dart';
import '../../../data/models/enums.dart';
import '../../../state/draft_controller.dart';

/// Step 1 — where and why the inspection is being carried out.
class InformationStep extends StatefulWidget {
  const InformationStep({super.key});

  @override
  State<InformationStep> createState() => _InformationStepState();
}

class _InformationStepState extends State<InformationStep> {
  late final TextEditingController _establishment;
  late final TextEditingController _location;

  @override
  void initState() {
    super.initState();
    final DraftController draft = context.read<DraftController>();
    _establishment = TextEditingController(text: draft.inspection.establishment);
    _location = TextEditingController(text: draft.inspection.location);

    // A new record carries no zone, and the dropdown would then *show* the
    // first zone while the record still held an empty string — the review
    // step would print "—" for a field the officer saw filled in. Commit the
    // default to the draft instead of only displaying it. Deferred to after
    // the first frame because it notifies listeners.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (_zones.contains(draft.inspection.zone)) return;
      draft.updateInformation(zone: _zones.first);
    });
  }

  /// Never empty: [AppDropdownField] asserts that its value appears exactly
  /// once among its items, so the list the value is resolved against and the
  /// list the field renders must be the same one.
  static List<String> get _zones =>
      MockData.zones.isEmpty ? const <String>['Unassigned'] : MockData.zones;

  @override
  void dispose() {
    _establishment.dispose();
    _location.dispose();
    super.dispose();
  }

  Future<void> _pickDate(DraftController draft) async {
    final DateTime now = DateTime.now();
    final DateTime? picked = await showDatePicker(
      context: context,
      initialDate: draft.inspection.dueOn,
      firstDate: now.subtract(const Duration(days: 30)),
      lastDate: now.add(const Duration(days: 90)),
    );
    if (picked == null) return;
    draft.updateInformation(
      dueOn: DateTime(
        picked.year,
        picked.month,
        picked.day,
        draft.inspection.dueOn.hour,
        draft.inspection.dueOn.minute,
      ),
    );
  }

  Future<void> _pickTime(DraftController draft) async {
    final TimeOfDay? picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(draft.inspection.dueOn),
    );
    if (picked == null) return;
    final DateTime current = draft.inspection.dueOn;
    draft.updateInformation(
      dueOn: DateTime(
        current.year,
        current.month,
        current.day,
        picked.hour,
        picked.minute,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: <Widget>[
        const InfoBanner(
          message:
              'Record where the inspection is taking place. These particulars '
              'appear on the submitted compliance report.',
        ),
        const SizedBox(height: 18),

        AppDropdownField<InspectionType>(
          label: 'Inspection type',
          required: true,
          value: draft.inspection.type,
          items: InspectionType.values,
          itemLabel: (InspectionType type) => type.label,
          onChanged: (InspectionType? value) {
            if (value != null) draft.updateInformation(type: value);
          },
        ),
        const SizedBox(height: 16),

        AppTextField(
          label: 'Establishment / premises',
          required: true,
          controller: _establishment,
          hint: 'e.g. ABC Food Products Pvt Ltd',
          onChanged: (String value) =>
              draft.updateInformation(establishment: value),
        ),
        const SizedBox(height: 16),

        AppTextField(
          label: 'Inspection location',
          required: true,
          controller: _location,
          hint: 'Street, area and city',
          prefixIcon: Icons.place_outlined,
          onChanged: (String value) => draft.updateInformation(location: value),
        ),
        const SizedBox(height: 16),

        AppDropdownField<String>(
          label: 'Zone',
          // Clamped to the list rather than trusted: once the API supplies
          // records, a zone outside this list would trip the dropdown's
          // "exactly one item with this value" assertion.
          value: _zones.contains(draft.inspection.zone)
              ? draft.inspection.zone
              : _zones.first,
          items: _zones,
          itemLabel: (String zone) => zone,
          onChanged: (String? value) {
            if (value != null) draft.updateInformation(zone: value);
          },
        ),
        const SizedBox(height: 16),

        AppDropdownField<Priority>(
          label: 'Priority',
          value: draft.inspection.priority,
          items: Priority.values,
          itemLabel: (Priority priority) => priority.label,
          onChanged: (Priority? value) {
            if (value != null) draft.updateInformation(priority: value);
          },
        ),
        const SizedBox(height: 16),

        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(
              child: AppPickerField(
                label: 'Date',
                value: Fmt.date(draft.inspection.dueOn),
                onTap: () => _pickDate(draft),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: AppPickerField(
                label: 'Time',
                value: Fmt.time(draft.inspection.dueOn),
                icon: Icons.schedule_outlined,
                onTap: () => _pickTime(draft),
              ),
            ),
          ],
        ),

        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          decoration: BoxDecoration(
            color: AppColors.surfaceMuted,
            borderRadius: BorderRadius.circular(6),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: <Widget>[
              const Icon(Icons.badge_outlined, size: 16, color: AppColors.inkMuted),
              const SizedBox(width: 9),
              Expanded(
                child: Text(
                  'Recorded against officer ${MockData.inspector.id} · ${MockData.inspector.name}',
                  style: const TextStyle(fontSize: 12.5, color: AppColors.inkMuted),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
