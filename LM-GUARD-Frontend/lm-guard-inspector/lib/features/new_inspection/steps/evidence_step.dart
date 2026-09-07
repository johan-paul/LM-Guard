import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/evidence_thumb.dart';
import '../../../core/widgets/panels.dart';
import '../../../data/models/evidence.dart';
import '../../../state/draft_controller.dart';

/// Step 4 — photographs and notes that support every finding.
class EvidenceStep extends StatelessWidget {
  const EvidenceStep({super.key});

  static const List<String> _suggestedLabels = <String>[
    'Principal display panel',
    'Retail sale price',
    'Net quantity declaration',
    'Manufacturer address',
    'Consumer care block',
    'Shelf display',
  ];

  Future<void> _capture(
    BuildContext context,
    DraftController draft, {
    required bool fromGallery,
  }) async {
    final String? label = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
      ),
      builder: (BuildContext context) => _LabelPicker(
        title: fromGallery ? 'Upload evidence' : 'Capture evidence',
        options: _suggestedLabels,
      ),
    );

    if (label == null || label.isEmpty) return;
    await draft.captureEvidence(label: label, fromGallery: fromGallery);
  }

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();
    final List<EvidenceItem> items = draft.inspection.evidence;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: <Widget>[
        const InfoBanner(
          message:
              'Photograph the declaration region for every non-compliance. '
              'Evidence is attached to the record and cannot be edited after '
              'submission.',
          icon: Icons.photo_camera_outlined,
        ),
        const SizedBox(height: 16),

        Row(
          children: <Widget>[
            Expanded(
              child: PrimaryButton(
                label: 'Take photo',
                icon: Icons.photo_camera_outlined,
                busy: draft.busy,
                onPressed: () => _capture(context, draft, fromGallery: false),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: SecondaryButton(
                label: 'Upload',
                icon: Icons.upload_outlined,
                onPressed: () => _capture(context, draft, fromGallery: true),
              ),
            ),
          ],
        ),

        const SizedBox(height: 22),
        SectionHeading(
          'Captured evidence',
          trailing: Text(
            Fmt.plural(items.length, 'item'),
            style: AppText.caption,
          ),
        ),

        if (items.isEmpty)
          AppPanel(
            child: const EmptyState(
              compact: true,
              icon: Icons.image_outlined,
              title: 'No evidence captured',
              message:
                  'Findings carry more weight when the panel region is photographed.',
            ),
          )
        else
          ...items.map(
            (EvidenceItem item) => _EvidenceRow(item: item, draft: draft),
          ),
      ],
    );
  }
}

class _EvidenceRow extends StatelessWidget {
  const _EvidenceRow({required this.item, required this.draft});

  final EvidenceItem item;
  final DraftController draft;

  Future<void> _edit(BuildContext context) async {
    final String? saved = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
      ),
      builder: (BuildContext context) => _DescriptionSheet(item: item),
    );

    if (saved != null) {
      draft.updateEvidence(item.id, description: saved);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(11),
      onTap: () => _edit(context),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          EvidenceThumb(item: item),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(item.label, style: AppText.recordTitle),
                const SizedBox(height: 2),
                Text(
                  '${item.kind.label} · ${Fmt.dayTime(item.capturedAt)}',
                  style: AppText.caption,
                ),
                const SizedBox(height: 5),
                Text(
                  item.description.isEmpty
                      ? 'Tap to add a description'
                      : item.description,
                  style: AppText.caption.copyWith(
                    color: item.description.isEmpty
                        ? AppColors.inkFaint
                        : AppColors.ink,
                    fontStyle: item.description.isEmpty
                        ? FontStyle.italic
                        : FontStyle.normal,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 3),
                Text(item.id, style: AppText.identifier),
              ],
            ),
          ),
          IconButton(
            onPressed: () => draft.removeEvidence(item.id),
            icon: const Icon(Icons.delete_outline, size: 19),
            color: AppColors.inkMuted,
            tooltip: 'Remove evidence',
          ),
        ],
      ),
    );
  }
}

/// Description editor for one captured evidence item.
///
/// The controller is owned by this State rather than created by the caller.
/// `showModalBottomSheet` completes its future the moment the route is popped
/// — while the sheet is still mounted for its exit transition — so disposing a
/// caller-owned controller at the await point would tear it out from under a
/// live TextField, and the next focus change would throw
/// "A TextEditingController was used after being disposed."
class _DescriptionSheet extends StatefulWidget {
  const _DescriptionSheet({required this.item});

  final EvidenceItem item;

  @override
  State<_DescriptionSheet> createState() => _DescriptionSheetState();
}

class _DescriptionSheetState extends State<_DescriptionSheet> {
  late final TextEditingController _description;

  @override
  void initState() {
    super.initState();
    _description = TextEditingController(text: widget.item.description);
  }

  @override
  void dispose() {
    _description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 18,
        // Lift the sheet clear of the on-screen keyboard.
        bottom: MediaQuery.of(context).viewInsets.bottom + 18,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text(widget.item.label, style: AppText.sectionTitle),
          const SizedBox(height: 3),
          Text(Fmt.dayTime(widget.item.capturedAt), style: AppText.caption),
          const SizedBox(height: 16),
          TextField(
            controller: _description,
            maxLines: 3,
            autofocus: true,
            style: AppText.body,
            decoration: const InputDecoration(
              hintText: 'Describe what this evidence shows',
            ),
          ),
          const SizedBox(height: 16),
          PrimaryButton(
            label: 'Save description',
            onPressed: () => Navigator.of(context).pop(_description.text),
          ),
        ],
      ),
    );
  }
}

/// Bottom sheet that names the evidence before it is captured.
class _LabelPicker extends StatelessWidget {
  const _LabelPicker({required this.title, required this.options});

  final String title;
  final List<String> options;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 18),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(title, style: AppText.sectionTitle),
            const SizedBox(height: 3),
            const Text(
              'What does this evidence show?',
              style: AppText.caption,
            ),
            const SizedBox(height: 14),
            ...options.map(
              (String option) => InkWell(
                onTap: () => Navigator.of(context).pop(option),
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 13),
                  decoration: const BoxDecoration(
                    border: Border(
                      bottom: BorderSide(color: AppColors.border),
                    ),
                  ),
                  child: Row(
                    children: <Widget>[
                      const Icon(
                        Icons.crop_free,
                        size: 17,
                        color: AppColors.inkFaint,
                      ),
                      const SizedBox(width: 11),
                      Expanded(child: Text(option, style: AppText.body)),
                      const Icon(
                        Icons.chevron_right,
                        size: 18,
                        color: AppColors.inkFaint,
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 14),
            SecondaryButton(
              label: 'Cancel',
              onPressed: () => Navigator.of(context).pop(),
            ),
          ],
        ),
      ),
    );
  }
}
