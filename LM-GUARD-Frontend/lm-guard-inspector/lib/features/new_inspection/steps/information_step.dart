import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/theme/app_theme.dart';
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
  late final TextEditingController _productName;
  bool _isLocating = false;

  static const List<String> _defaultProductSuggestions = <String>[
    'Lifebuoy Bath Soap 100g',
    'Lux Toilet Soap 100g',
    'Classic Salted Chips 50g',
    'SunFresh Refined Sunflower Oil 1 L',
    'ABC Instant Noodles Masala 280 g',
    'FreshBite Cashew Biscuits 200 g',
    'PureDrop Packaged Drinking Water 1 L',
    'Tata Salt Vacuum Evaporated 1 kg',
    'Aashirvaad Whole Wheat Atta 5 kg',
    'Amul Butter Pasteurized 500 g',
    'Fortune Mustard Oil 1 L',
    'Brittania Good Day Cookies 150 g',
  ];

  @override
  void initState() {
    super.initState();
    final DraftController draft = context.read<DraftController>();
    _establishment = TextEditingController(text: draft.inspection.establishment);
    _location = TextEditingController(text: draft.inspection.location);
    _productName = TextEditingController(text: draft.inspection.product?.name ?? '');

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
    _productName.dispose();
    super.dispose();
  }

  Future<void> _detectLocation(DraftController draft) async {
    setState(() => _isLocating = true);
    await Future<void>.delayed(const Duration(milliseconds: 500));
    if (!mounted) return;

    final String zone = draft.inspection.zone.isNotEmpty ? draft.inspection.zone : _zones.first;
    final String detected = '$zone, Main Market Premises (11.0168° N, 76.9558° E)';

    _location.text = detected;
    draft.updateInformation(location: detected);
    setState(() => _isLocating = false);

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Auto-detected location: $detected'),
        backgroundColor: AppColors.info,
        duration: const Duration(seconds: 2),
      ),
    );
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

        // Product Name Field with History Autocomplete Suggestions
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const FieldLabel('Product Name (Optional)', required: false),
            const SizedBox(height: 6),
            Autocomplete<String>(
              initialValue: TextEditingValue(text: draft.inspection.product?.name ?? ''),
              optionsBuilder: (TextEditingValue textEditingValue) {
                if (textEditingValue.text.isEmpty) {
                  return const Iterable<String>.empty();
                }
                return _defaultProductSuggestions.where((String option) {
                  return option.toLowerCase().contains(textEditingValue.text.toLowerCase());
                });
              },
              onSelected: (String selection) {
                _productName.text = selection;
                draft.updateProductName(selection);
              },
              fieldViewBuilder: (BuildContext context, TextEditingController controller, FocusNode focusNode, VoidCallback onFieldSubmitted) {
                return TextField(
                  controller: controller,
                  focusNode: focusNode,
                  onChanged: (String value) {
                    draft.updateProductName(value);
                  },
                  style: AppText.body,
                  decoration: const InputDecoration(
                    hintText: 'e.g. Lifebuoy Soap, Classic Salted Chips...',
                    prefixIcon: Icon(Icons.shopping_bag_outlined, size: 18, color: AppColors.inkFaint),
                  ),
                );
              },
            ),
            const SizedBox(height: 5),
            const Text(
              'Type to get suggestions from registered products history',
              style: AppText.caption,
            ),
          ],
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

        // Location field with auto-detect GPS button
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: <Widget>[
            Expanded(
              child: AppTextField(
                label: 'Inspection location',
                required: true,
                controller: _location,
                hint: 'Street, area and city',
                prefixIcon: Icons.place_outlined,
                onChanged: (String value) => draft.updateInformation(location: value),
              ),
            ),
            const SizedBox(width: 8),
            Padding(
              padding: const EdgeInsets.only(bottom: 2),
              child: Container(
                height: 44,
                width: 44,
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(AppSizes.radiusSmall),
                  border: Border.all(color: AppColors.borderStrong),
                ),
                child: IconButton(
                  icon: _isLocating
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.my_location, size: 20, color: AppColors.navy),
                  tooltip: 'Auto-detect GPS Location',
                  onPressed: _isLocating ? null : () => _detectLocation(draft),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),

        AppDropdownField<String>(
          label: 'Zone',
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
