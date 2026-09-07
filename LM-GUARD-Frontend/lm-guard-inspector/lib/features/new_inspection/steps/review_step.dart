import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/chips.dart';
import '../../../core/widgets/checklist_panel.dart';
import '../../../core/widgets/panels.dart';
import '../../../core/widgets/product_panel.dart';
import '../../../data/models/ai_evaluation.dart';
import '../../../data/models/enums.dart';
import '../../../data/models/inspection.dart';
import '../../../state/draft_controller.dart';

/// Step 5 — the compliance report as it will be submitted, compiled entirely
/// from the AI/rule engine's own output: the identified product, the
/// checklist it filled in, every violation with its evidence, and the rule
/// engine's own suggested verdict, pre-selected below. The officer reviews
/// and can override any of it before submitting - nothing here is typed in
/// from a blank state.
class ReviewStep extends StatefulWidget {
  const ReviewStep({super.key});

  @override
  State<ReviewStep> createState() => _ReviewStepState();
}

class _ReviewStepState extends State<ReviewStep> {
  late final TextEditingController _notes;

  @override
  void initState() {
    super.initState();
    _notes = TextEditingController(
      text: context.read<DraftController>().inspection.officerNotes,
    );
  }

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();
    final Inspection record = draft.inspection;
    final List<AiViolationEvidence> violations = draft.aiEvaluation?.violations ?? const <AiViolationEvidence>[];

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: <Widget>[
        if (!draft.readyToSubmit)
          Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: InfoBanner(
              tone: BannerTone.warning,
              icon: Icons.warning_amber_outlined,
              message: _outstandingMessage(draft),
            ),
          ),

        // --- Inspection summary ---------------------------------------
        const SectionHeading('Inspection summary'),
        AppPanel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                record.establishment.isEmpty
                    ? 'Establishment not recorded'
                    : record.establishment,
                style: AppText.recordTitle,
              ),
              const SizedBox(height: 3),
              Text(record.id, style: AppText.identifier),
              const SizedBox(height: 12),
              const Divider(height: 1),
              const SizedBox(height: 12),
              LabelledValue(label: 'Location', value: record.location),
              const SizedBox(height: 12),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Expanded(
                    child: LabelledValue(label: 'Type', value: record.type.label),
                  ),
                  Expanded(
                    child: LabelledValue(label: 'Zone', value: record.zone),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Expanded(
                    child: LabelledValue(
                      label: 'Date',
                      value: Fmt.dayTime(record.dueOn),
                    ),
                  ),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text('Priority'.toUpperCase(), style: AppText.label),
                        const SizedBox(height: 5),
                        PriorityChip(record.priority),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),

        // --- Product ---------------------------------------------------
        const SizedBox(height: 20),
        const SectionHeading('Product details'),
        ProductPanel(product: record.product, showIdentifier: false),

        // --- Checklist -------------------------------------------------
        const SizedBox(height: 20),
        SectionHeading(
          'Checklist completion',
          trailing: Text(
            '${record.answeredChecks} of ${record.checklist.length}',
            style: AppText.caption,
          ),
        ),
        ChecklistPanel(items: record.checklist),

        // --- Findings & evidence -----------------------------------------
        // Sourced entirely from the rule engine's own violations - each one
        // already carries its evidence region, so there is nothing separate
        // to show here beyond what the findings/evidence step already did.
        const SizedBox(height: 20),
        SectionHeading(
          'Findings & evidence',
          trailing: Text('${violations.length}', style: AppText.caption),
        ),
        if (violations.isEmpty)
          const AppPanel(
            child: Text(
              'No violations recorded against this inspection.',
              style: AppText.bodyMuted,
            ),
          )
        else
          ...violations.map(
            (AiViolationEvidence violation) => AppPanel(
              margin: const EdgeInsets.only(bottom: 8),
              leadingStripe: violation.severity == 'CRITICAL' ? AppColors.critical : AppColors.danger,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      Expanded(child: Text(violation.ruleCode, style: AppText.identifier)),
                      if (violation.boundingBox != null)
                        const Icon(Icons.crop_free, size: 15, color: AppColors.inkFaint),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(violation.finding, style: AppText.body),
                  const SizedBox(height: 6),
                  Text(
                    '${violation.fieldName} · ${(violation.confidence * 100).round()}% confidence',
                    style: AppText.caption,
                  ),
                ],
              ),
            ),
          ),

        // --- Officer notes ---------------------------------------------
        const SizedBox(height: 20),
        const SectionHeading('Inspector notes'),
        TextField(
          controller: _notes,
          maxLines: 4,
          style: AppText.body,
          onChanged: draft.setOfficerNotes,
          decoration: const InputDecoration(
            hintText:
                'Action taken at the premises, samples drawn, documents collected',
          ),
        ),

        // --- Final decision ---------------------------------------------
        const SizedBox(height: 20),
        const SectionHeading('Final decision'),
        AppPanel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              const Text(
                'Your own decision - independent of any AI suggestion recorded '
                'against the checklist. Required before submitting.',
                style: AppText.bodyMuted,
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: FinalDecision.values.map((FinalDecision option) {
                  final bool selected = record.finalDecision == option;
                  return ChoiceChip(
                    label: Text(option.label),
                    selected: selected,
                    onSelected: (_) => draft.setFinalDecision(option),
                    selectedColor: _decisionColor(option).withOpacity(0.14),
                    labelStyle: AppText.body.copyWith(
                      color: selected ? _decisionColor(option) : AppColors.ink,
                      fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                    ),
                    side: BorderSide(
                      color: selected ? _decisionColor(option) : AppColors.border,
                    ),
                    backgroundColor: AppColors.surface,
                  );
                }).toList(),
              ),
            ],
          ),
        ),

        const SizedBox(height: 18),
        const InfoBanner(
          message:
              'On submission the record is transmitted to the zonal office and '
              'locked on this device. The enforcement decision rests with the '
              'reviewing officer.',
          icon: Icons.lock_outline,
        ),
      ],
    );
  }

  String _outstandingMessage(DraftController draft) {
    final List<String> missing = <String>[];
    if (!draft.informationComplete) missing.add('inspection particulars');
    if (!draft.productIdentified) missing.add('product identification');
    if (!draft.checklistComplete) missing.add('the compliance checklist');
    if (draft.inspection.finalDecision == null) missing.add('a final decision');
    return 'Complete ${missing.join(', ')} before submitting. The record can be '
        'saved as a draft in the meantime.';
  }

  Color _decisionColor(FinalDecision decision) {
    switch (decision) {
      case FinalDecision.compliant:
        return AppColors.success;
      case FinalDecision.nonCompliant:
        return AppColors.danger;
      case FinalDecision.inconclusive:
        return AppColors.warning;
    }
  }
}
