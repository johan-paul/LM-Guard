import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/buttons.dart';
import '../../../../core/widgets/panels.dart';
import '../../../../data/models/enums.dart';
import '../../../../state/draft_controller.dart';

/// Advisory AI evaluation, embedded at the top of the compliance checklist
/// step. States: idle → processing → completed | failed (retry loops back
/// to processing). The AI never writes a checklist result itself - each
/// line's "Accept" action is the only path from a suggestion to a recorded
/// decision, and the inspector can just as easily decide otherwise.
class AiEvaluationPanel extends StatelessWidget {
  const AiEvaluationPanel({super.key, required this.draft});

  final DraftController draft;

  @override
  Widget build(BuildContext context) {
    final bool productIdentified = draft.inspection.product != null;

    switch (draft.aiStatus) {
      case AiEvaluationStatus.idle:
        return AppPanel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text('AI evaluation'.toUpperCase(), style: AppText.label),
              const SizedBox(height: 6),
              const Text(
                'Send the captured package for evaluation. The model reads the '
                'printed declarations and reports what it finds; you record the '
                'decision on each line below.',
                style: AppText.bodyMuted,
              ),
              const SizedBox(height: 12),
              PrimaryButton(
                label: 'Run AI evaluation',
                icon: Icons.auto_awesome_outlined,
                onPressed: productIdentified ? draft.runAiEvaluation : null,
              ),
              if (!productIdentified) ...<Widget>[
                const SizedBox(height: 6),
                Text(
                  'Identify the product first.',
                  style: AppText.caption.copyWith(color: AppColors.inkMuted),
                ),
              ],
            ],
          ),
        );

      case AiEvaluationStatus.pending:
      case AiEvaluationStatus.processing:
        return const AppPanel(
          child: Row(
            children: <Widget>[
              SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2.4),
              ),
              SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text('AI evaluation in progress…', style: AppText.recordTitle),
                    SizedBox(height: 3),
                    Text(
                      'Reading the principal display panel against the active '
                      'ruleset. You can continue recording the checklist while '
                      'this runs.',
                      style: AppText.bodyMuted,
                    ),
                  ],
                ),
              ),
            ],
          ),
        );

      case AiEvaluationStatus.completed:
        final int flagged = draft.aiEvaluation?.flaggedCount ?? 0;
        final int total = draft.aiEvaluation?.checklistResults.length ?? 0;
        final int lowConfidence = draft.aiEvaluation?.lowConfidenceCount ?? 0;
        return AppPanel(
          leadingStripe: AppColors.success,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              const Text('AI evaluation complete', style: AppText.recordTitle),
              const SizedBox(height: 4),
              Text(
                flagged == 0
                    ? 'The model found no breach across $total declarations.'
                    : '$flagged of $total declarations were flagged. Each is marked on the line below.',
                style: AppText.body,
              ),
              if (lowConfidence > 0) ...<Widget>[
                const SizedBox(height: 4),
                Text(
                  '$lowConfidence assessment(s) came back at low confidence — treat those as advisory and verify against the package.',
                  style: AppText.caption.copyWith(color: AppColors.warning),
                ),
              ],
              const SizedBox(height: 8),
              Text(
                'The AI assessment is an observation, not a finding. Your decision on each line is what is recorded.',
                style: AppText.caption.copyWith(fontStyle: FontStyle.italic),
              ),
              const SizedBox(height: 10),
              QuietButton(label: 'Run again', onPressed: draft.runAiEvaluation),
            ],
          ),
        );

      case AiEvaluationStatus.failed:
        return AppPanel(
          leadingStripe: AppColors.danger,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              const Text('AI evaluation failed', style: AppText.recordTitle),
              const SizedBox(height: 4),
              Text(
                draft.aiError ?? 'The evaluation could not be completed.',
                style: AppText.body,
              ),
              const SizedBox(height: 4),
              const Text(
                'The checklist is unaffected — record it by hand, or try the evaluation again.',
                style: AppText.bodyMuted,
              ),
              const SizedBox(height: 10),
              PrimaryButton(label: 'Retry evaluation', icon: Icons.refresh, onPressed: draft.runAiEvaluation),
            ],
          ),
        );
    }
  }
}
