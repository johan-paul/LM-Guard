import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/widgets/buttons.dart';
import '../../core/widgets/gov_app_bar.dart';
import '../../data/models/inspection.dart';
import '../../data/repositories/inspection_repository.dart';
import '../../data/services/evidence_service.dart';
import '../../state/draft_controller.dart';
import 'steps/checklist_step.dart';
import 'steps/evidence_step.dart';
import 'steps/findings_step.dart';
import 'steps/information_step.dart';
import 'steps/product_step.dart';
import 'steps/review_step.dart';
import 'steps/scan_step.dart';

/// The guided field inspection workflow.
///
/// Information → Product → Scan → Checklist → Evidence → Findings → Review.
class NewInspectionScreen extends StatelessWidget {
  const NewInspectionScreen({
    super.key,
    required this.inspection,
    required this.repository,
    required this.evidenceService,
    required this.onCompleted,
    this.initialStep = InspectionStep.information,
  });

  final Inspection inspection;
  final InspectionRepository repository;
  final EvidenceService evidenceService;
  final void Function(Inspection inspection) onCompleted;
  final InspectionStep initialStep;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<DraftController>(
      create: (_) => DraftController(
        inspection: inspection,
        repository: repository,
        evidenceService: evidenceService,
        initialStep: initialStep,
      ),
      child: _WorkflowView(onCompleted: onCompleted),
    );
  }
}

class _WorkflowView extends StatelessWidget {
  const _WorkflowView({required this.onCompleted});

  final void Function(Inspection inspection) onCompleted;

  Future<bool> _confirmExit(BuildContext context, DraftController draft) async {
    if (!draft.dirty) return true;

    final bool? leave = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('Leave this inspection?', style: AppText.sectionTitle),
        content: const Text(
          'Unsaved changes will be lost. Save the record as a draft to continue '
          'it later.',
          style: AppText.body,
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Stay'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text(
              'Discard',
              style: TextStyle(color: AppColors.danger),
            ),
          ),
        ],
      ),
    );

    return leave ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();
    final Inspection record = draft.inspection;

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: RecordHeader(
        title:
            record.establishment.isEmpty ? 'New inspection' : record.establishment,
        subtitle:
            'Step ${draft.stepNumber} of ${draft.stepCount} · ${draft.step.title}',
        onBack: () async {
          final bool allowed = await _confirmExit(context, draft);
          if (allowed && context.mounted) Navigator.of(context).pop();
        },
        actions: <Widget>[
          TextButton(
            onPressed: draft.busy
                ? null
                : () async {
                    final Inspection saved = await draft.saveDraft();
                    onCompleted(saved);
                    if (!context.mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Saved as draft.')),
                    );
                  },
            child: const Text(
              'Save',
              style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
      body: Column(
        children: <Widget>[
          _StepRail(current: draft.step),
          Expanded(child: _stepBody(draft)),
          _ActionBar(onCompleted: onCompleted),
        ],
      ),
    );
  }

  Widget _stepBody(DraftController draft) {
    switch (draft.step) {
      case InspectionStep.information:
        return const InformationStep();
      case InspectionStep.product:
        return const ProductStep();
      case InspectionStep.scan:
        return const ScanStep();
      case InspectionStep.checklist:
        return const ChecklistStep();
      case InspectionStep.evidence:
        return const EvidenceStep();
      case InspectionStep.findings:
        return const FindingsStep();
      case InspectionStep.review:
        return const ReviewStep();
    }
  }
}

/// Segmented progress indicator across the six stages.
class _StepRail extends StatelessWidget {
  const _StepRail({required this.current});

  final InspectionStep current;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppColors.surface,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: List<Widget>.generate(InspectionStep.values.length, (int i) {
              final bool done = i < current.index;
              final bool active = i == current.index;
              return Expanded(
                child: Padding(
                  padding: EdgeInsets.only(
                    right: i == InspectionStep.values.length - 1 ? 0 : 5,
                  ),
                  child: Container(
                    height: 4,
                    decoration: BoxDecoration(
                      color: done
                          ? AppColors.accent
                          : active
                              ? AppColors.accent
                              : AppColors.border,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
              );
            }),
          ),
          const SizedBox(height: 9),
          Row(
            children: <Widget>[
              Text(
                'STEP ${current.number} OF ${InspectionStep.values.length}',
                style: AppText.label.copyWith(fontSize: 10),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  current.title,
                  style: AppText.caption.copyWith(
                    color: AppColors.ink,
                    fontWeight: FontWeight.w600,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Persistent bottom action bar for the workflow.
class _ActionBar extends StatelessWidget {
  const _ActionBar({required this.onCompleted});

  final void Function(Inspection inspection) onCompleted;

  Future<void> _submit(BuildContext context, DraftController draft) async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('Submit inspection?', style: AppText.sectionTitle),
        content: Text(
          'The record for ${draft.inspection.establishment} will be submitted to '
          'the zonal office and can no longer be edited on this device.',
          style: AppText.body,
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Submit'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    final Inspection submitted = await draft.submit();
    onCompleted(submitted);
    if (!context.mounted) return;

    Navigator.of(context).pop();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('${submitted.id} submitted for review.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();
    final bool onReview = draft.step == InspectionStep.review;

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: onReview
              ? Row(
                  children: <Widget>[
                    Expanded(
                      child: SecondaryButton(
                        label: 'Save as draft',
                        icon: Icons.save_outlined,
                        onPressed: draft.busy
                            ? null
                            : () async {
                                final Inspection saved = await draft.saveDraft();
                                onCompleted(saved);
                                if (!context.mounted) return;
                                Navigator.of(context).pop();
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text('Saved as draft.'),
                                  ),
                                );
                              },
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: PrimaryButton(
                        label: 'Submit',
                        icon: Icons.send_outlined,
                        busy: draft.busy,
                        onPressed: draft.readyToSubmit
                            ? () => _submit(context, draft)
                            : null,
                      ),
                    ),
                  ],
                )
              : Row(
                  children: <Widget>[
                    if (!draft.isFirstStep) ...<Widget>[
                      Expanded(
                        child: SecondaryButton(
                          label: 'Back',
                          icon: Icons.arrow_back,
                          onPressed: draft.back,
                        ),
                      ),
                      const SizedBox(width: 10),
                    ],
                    Expanded(
                      flex: draft.isFirstStep ? 1 : 1,
                      child: PrimaryButton(
                        label: 'Continue',
                        icon: Icons.arrow_forward,
                        onPressed: _canContinue(draft) ? draft.next : null,
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }

  bool _canContinue(DraftController draft) {
    switch (draft.step) {
      case InspectionStep.information:
        return draft.informationComplete;
      case InspectionStep.product:
        return draft.productIdentified;
      case InspectionStep.scan:
        return draft.packagePhotoCaptured;
      case InspectionStep.checklist:
      case InspectionStep.evidence:
      case InspectionStep.findings:
      case InspectionStep.review:
        return true;
    }
  }
}
