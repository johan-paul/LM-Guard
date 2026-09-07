import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/panels.dart';
import '../../../data/models/ai_evaluation.dart';
import '../../../data/models/checklist_item.dart';
import '../../../data/models/enums.dart';
import '../../../state/draft_controller.dart';
import 'widgets/ai_evaluation_panel.dart';

/// Step 3 — the statutory declaration checklist, recorded line by line.
class ChecklistStep extends StatelessWidget {
  const ChecklistStep({super.key});

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();
    final List<ChecklistItem> items = draft.inspection.checklist;
    final int answered = draft.inspection.answeredChecks;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: <Widget>[
        AppPanel(
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      'Declarations recorded'.toUpperCase(),
                      style: AppText.label,
                    ),
                  ),
                  Text(
                    '$answered of ${items.length}',
                    style: AppText.caption.copyWith(
                      color: AppColors.ink,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 9),
              ProgressRail(
                percent: items.isEmpty
                    ? 0
                    : ((answered / items.length) * 100).round(),
              ),
              if (draft.inspection.failedChecks > 0) ...<Widget>[
                const SizedBox(height: 11),
                Text(
                  '${draft.inspection.failedChecks} declaration(s) marked non-compliant. '
                  'Raise a finding for each at the next step.',
                  style: AppText.caption.copyWith(color: AppColors.danger),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 16),
        AiEvaluationPanel(draft: draft),
        const SizedBox(height: 16),
        ...items.map(
          (ChecklistItem item) => _ChecklistTile(
            key: ValueKey<String>(item.id),
            item: item,
            draft: draft,
          ),
        ),
      ],
    );
  }
}

class _ChecklistTile extends StatefulWidget {
  const _ChecklistTile({super.key, required this.item, required this.draft});

  final ChecklistItem item;
  final DraftController draft;

  @override
  State<_ChecklistTile> createState() => _ChecklistTileState();
}

class _ChecklistTileState extends State<_ChecklistTile> {
  late final TextEditingController _note;

  @override
  void initState() {
    super.initState();
    _note = TextEditingController(text: widget.item.note);
  }

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ChecklistItem item = widget.item;
    final DraftController draft = widget.draft;

    return AppPanel(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.fromLTRB(13, 12, 13, 12),
      leadingStripe: item.isFailed ? AppColors.danger : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(child: Text(item.title, style: AppText.recordTitle)),
              const SizedBox(width: 8),
              Text(item.ruleRef, style: AppText.identifier),
            ],
          ),
          const SizedBox(height: 4),
          Text(item.guidance, style: AppText.caption),
          _AiSuggestionRow(draft: draft, item: item),
          const SizedBox(height: 12),
          _ResultSelector(
            value: item.result,
            onChanged: (CheckResult result) =>
                draft.setCheckResult(item.id, result),
          ),
          if (item.isFailed) ...<Widget>[
            const SizedBox(height: 11),
            TextField(
              controller: _note,
              onChanged: (String value) => draft.setCheckNote(item.id, value),
              maxLines: 2,
              style: AppText.body,
              decoration: const InputDecoration(
                hintText: 'What was observed on the package?',
                isDense: true,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// The AI's suggestion for one checklist line, if an evaluation has run.
/// Shows "You decided otherwise" instead of Accept once the inspector's own
/// result diverges from the suggestion - it never gets overwritten silently.
class _AiSuggestionRow extends StatelessWidget {
  const _AiSuggestionRow({required this.draft, required this.item});

  final DraftController draft;
  final ChecklistItem item;

  @override
  Widget build(BuildContext context) {
    final AiChecklistResult? suggestion = draft.aiResultFor(item.ruleRef);
    if (suggestion == null) return const SizedBox(height: 8);

    final bool matches = item.result == suggestion.aiSuggestedStatus;
    final bool lowConfidence = suggestion.confidenceScore < 0.7;

    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Container(
        padding: const EdgeInsets.all(9),
        decoration: BoxDecoration(
          color: AppColors.canvas,
          borderRadius: BorderRadius.circular(6),
          border: Border.all(color: AppColors.borderStrong),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      Text('AI ASSESSMENT', style: AppText.label.copyWith(color: AppColors.inkMuted)),
                      const SizedBox(width: 6),
                      Text(
                        '${(suggestion.confidenceScore * 100).round()}%',
                        style: AppText.caption.copyWith(
                          color: lowConfidence ? AppColors.warning : AppColors.inkMuted,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 3),
                  Text(suggestion.explanation, style: AppText.caption),
                  if (item.isAnswered && !matches) ...<Widget>[
                    const SizedBox(height: 3),
                    Text(
                      'You decided otherwise',
                      style: AppText.caption.copyWith(color: AppColors.inkMuted, fontStyle: FontStyle.italic),
                    ),
                  ],
                ],
              ),
            ),
            if (!item.isAnswered || matches) ...<Widget>[
              const SizedBox(width: 8),
              QuietButton(label: 'Accept', onPressed: () => draft.acceptAiSuggestion(item.id)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Three-way outcome selector sized for gloved, one-handed use.
class _ResultSelector extends StatelessWidget {
  const _ResultSelector({required this.value, required this.onChanged});

  final CheckResult value;
  final ValueChanged<CheckResult> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: <Widget>[
        Expanded(
          child: _option(
            label: 'Compliant',
            icon: Icons.check,
            active: value == CheckResult.compliant,
            activeColor: AppColors.success,
            onTap: () => onChanged(CheckResult.compliant),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _option(
            label: 'Not compliant',
            icon: Icons.close,
            active: value == CheckResult.nonCompliant,
            activeColor: AppColors.danger,
            onTap: () => onChanged(CheckResult.nonCompliant),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _option(
            label: 'N/A',
            icon: Icons.remove,
            active: value == CheckResult.notApplicable,
            activeColor: AppColors.neutral,
            onTap: () => onChanged(CheckResult.notApplicable),
          ),
        ),
      ],
    );
  }

  Widget _option({
    required String label,
    required IconData icon,
    required bool active,
    required Color activeColor,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(6),
      child: Container(
        height: 42,
        alignment: Alignment.center,
        padding: const EdgeInsets.symmetric(horizontal: 6),
        decoration: BoxDecoration(
          color: active ? activeColor : AppColors.surface,
          borderRadius: BorderRadius.circular(6),
          border: Border.all(
            color: active ? activeColor : AppColors.borderStrong,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Icon(
              icon,
              size: 15,
              color: active ? Colors.white : AppColors.inkMuted,
            ),
            const SizedBox(width: 4),
            Flexible(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: active ? Colors.white : AppColors.inkMuted,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
