import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/chips.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/panels.dart';
import '../../../data/models/checklist_item.dart';
import '../../../data/models/enums.dart';
import '../../../data/models/evidence.dart';
import '../../../data/models/finding.dart';
import '../../../state/draft_controller.dart';

/// Step 5 — record the non-compliances the officer is prepared to stand behind.
class FindingsStep extends StatefulWidget {
  const FindingsStep({super.key});

  @override
  State<FindingsStep> createState() => _FindingsStepState();
}

class _FindingsStepState extends State<FindingsStep> {
  List<RuleReference> _rules = <RuleReference>[];

  @override
  void initState() {
    super.initState();
    _loadRules();
  }

  Future<void> _loadRules() async {
    final List<RuleReference> rules =
        await context.read<DraftController>().fetchRules();
    if (!mounted) return;
    setState(() => _rules = rules);
  }

  Future<void> _addFinding(DraftController draft, {String? presetRuleRef}) async {
    if (_rules.isEmpty) return;

    final _FindingDraft? result = await showModalBottomSheet<_FindingDraft>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
      ),
      builder: (BuildContext context) => _FindingSheet(
        rules: _rules,
        evidence: draft.inspection.evidence,
        presetRuleRef: presetRuleRef,
      ),
    );

    if (result == null) return;
    draft.addFinding(
      ruleRef: result.rule.ref,
      ruleName: result.rule.name,
      severity: result.severity,
      description: result.description,
      note: result.note,
      evidenceIds: result.evidenceIds,
    );
  }

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();
    final List<Finding> findings = draft.inspection.findings;
    final List<ChecklistItem> unreconciled = draft.unreconciledFailures;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: <Widget>[
        if (unreconciled.isNotEmpty) ...<Widget>[
          InfoBanner(
            tone: BannerTone.warning,
            icon: Icons.warning_amber_outlined,
            message:
                '${unreconciled.length} declaration(s) were marked non-compliant '
                'with no finding raised against them.',
          ),
          const SizedBox(height: 10),
          ...unreconciled.map(
            (ChecklistItem item) => AppPanel(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.fromLTRB(12, 10, 8, 10),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(item.title, style: AppText.body),
                        const SizedBox(height: 2),
                        Text(item.ruleRef, style: AppText.identifier),
                      ],
                    ),
                  ),
                  QuietButton(
                    label: 'Raise',
                    icon: Icons.add,
                    onPressed: () =>
                        _addFinding(draft, presetRuleRef: item.ruleRef),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
        ],

        SectionHeading(
          'Recorded findings',
          trailing: Text('${findings.length}', style: AppText.caption),
        ),

        if (findings.isEmpty)
          AppPanel(
            child: EmptyState(
              compact: true,
              icon: Icons.gavel_outlined,
              title: 'No findings recorded',
              message:
                  'Raise a finding for each non-compliance observed on the package.',
              action: SecondaryButton(
                label: 'Add finding',
                expand: false,
                icon: Icons.add,
                onPressed: () => _addFinding(draft),
              ),
            ),
          )
        else ...<Widget>[
          ...findings.map(
            (Finding finding) => _FindingCard(finding: finding, draft: draft),
          ),
          const SizedBox(height: 4),
          SecondaryButton(
            label: 'Add another finding',
            icon: Icons.add,
            onPressed: () => _addFinding(draft),
          ),
        ],
      ],
    );
  }
}

class _FindingCard extends StatelessWidget {
  const _FindingCard({required this.finding, required this.draft});

  final Finding finding;
  final DraftController draft;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      margin: const EdgeInsets.only(bottom: 10),
      leadingStripe: finding.severity == Severity.critical
          ? AppColors.critical
          : AppColors.danger,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: Text(finding.reference, style: AppText.identifier),
              ),
              SeverityChip(finding.severity, dense: true),
              const SizedBox(width: 4),
              InkWell(
                onTap: () => draft.removeFinding(finding.id),
                borderRadius: BorderRadius.circular(4),
                child: const Padding(
                  padding: EdgeInsets.all(4),
                  child: Icon(
                    Icons.close,
                    size: 16,
                    color: AppColors.inkMuted,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          LabelledValue(label: 'Rule', value: '${finding.ruleRef} · ${finding.ruleName}'),
          const SizedBox(height: 10),
          LabelledValue(label: 'Description', value: finding.description),
          if (finding.note.isNotEmpty) ...<Widget>[
            const SizedBox(height: 10),
            LabelledValue(label: 'Officer note', value: finding.note),
          ],
          const SizedBox(height: 10),
          Row(
            children: <Widget>[
              const Icon(
                Icons.attachment_outlined,
                size: 15,
                color: AppColors.inkFaint,
              ),
              const SizedBox(width: 6),
              Text(
                finding.evidenceIds.isEmpty
                    ? 'No evidence attached'
                    : '${finding.evidenceIds.length} attachment(s)',
                style: AppText.caption,
              ),
              const Spacer(),
              AppChip(
                label: finding.status.label,
                foreground: AppColors.neutral,
                background: AppColors.neutralSoft,
                dense: true,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Value returned by the add-finding sheet.
class _FindingDraft {
  const _FindingDraft({
    required this.rule,
    required this.severity,
    required this.description,
    required this.note,
    required this.evidenceIds,
  });

  final RuleReference rule;
  final Severity severity;
  final String description;
  final String note;
  final List<String> evidenceIds;
}

class _FindingSheet extends StatefulWidget {
  const _FindingSheet({
    required this.rules,
    required this.evidence,
    this.presetRuleRef,
  });

  final List<RuleReference> rules;
  final List<EvidenceItem> evidence;
  final String? presetRuleRef;

  @override
  State<_FindingSheet> createState() => _FindingSheetState();
}

class _FindingSheetState extends State<_FindingSheet> {
  late RuleReference _rule;
  Severity _severity = Severity.medium;
  final TextEditingController _description = TextEditingController();
  final TextEditingController _note = TextEditingController();
  final Set<String> _selectedEvidence = <String>{};

  @override
  void initState() {
    super.initState();
    _rule = widget.rules.firstWhere(
      (RuleReference rule) => rule.ref == widget.presetRuleRef,
      orElse: () => widget.rules.first,
    );
  }

  @override
  void dispose() {
    _description.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 18,
        bottom: MediaQuery.of(context).viewInsets.bottom + 18,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            const Text('Add finding', style: AppText.sectionTitle),
            const SizedBox(height: 3),
            const Text(
              'Cite the rule the package failed and describe what was observed.',
              style: AppText.caption,
            ),
            const SizedBox(height: 18),

            Text('Rule'.toUpperCase(), style: AppText.label),
            const SizedBox(height: 6),
            DropdownButtonFormField<RuleReference>(
              value: _rule,
              isExpanded: true,
              style: AppText.body,
              items: widget.rules
                  .map(
                    (RuleReference rule) => DropdownMenuItem<RuleReference>(
                      value: rule,
                      child: Text(
                        '${rule.ref} · ${rule.name}',
                        style: AppText.body,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  )
                  .toList(),
              onChanged: (RuleReference? value) {
                if (value != null) setState(() => _rule = value);
              },
            ),
            const SizedBox(height: 16),

            Text('Severity'.toUpperCase(), style: AppText.label),
            const SizedBox(height: 8),
            Row(
              children: Severity.values.map((Severity severity) {
                final bool active = severity == _severity;
                return Expanded(
                  child: Padding(
                    padding: EdgeInsets.only(
                      right: severity == Severity.values.last ? 0 : 8,
                    ),
                    child: InkWell(
                      onTap: () => setState(() => _severity = severity),
                      borderRadius: BorderRadius.circular(6),
                      child: Container(
                        height: 40,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: active ? AppColors.navy : AppColors.surface,
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(
                            color: active
                                ? AppColors.navy
                                : AppColors.borderStrong,
                          ),
                        ),
                        child: Text(
                          severity.label,
                          style: TextStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                            color: active ? Colors.white : AppColors.inkMuted,
                          ),
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
            const SizedBox(height: 16),

            Text('Description'.toUpperCase(), style: AppText.label),
            const SizedBox(height: 6),
            TextField(
              controller: _description,
              maxLines: 3,
              style: AppText.body,
              onChanged: (_) => setState(() {}),
              decoration: const InputDecoration(
                hintText: 'What was observed on the package?',
              ),
            ),
            const SizedBox(height: 16),

            Text('Officer note'.toUpperCase(), style: AppText.label),
            const SizedBox(height: 6),
            TextField(
              controller: _note,
              maxLines: 2,
              style: AppText.body,
              decoration: const InputDecoration(
                hintText: 'Optional — action taken, samples drawn',
              ),
            ),

            if (widget.evidence.isNotEmpty) ...<Widget>[
              const SizedBox(height: 16),
              Text('Attach evidence'.toUpperCase(), style: AppText.label),
              const SizedBox(height: 8),
              ...widget.evidence.map(
                (EvidenceItem item) => CheckboxListTile(
                  value: _selectedEvidence.contains(item.id),
                  onChanged: (bool? checked) {
                    setState(() {
                      if (checked ?? false) {
                        _selectedEvidence.add(item.id);
                      } else {
                        _selectedEvidence.remove(item.id);
                      }
                    });
                  },
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  controlAffinity: ListTileControlAffinity.leading,
                  activeColor: AppColors.accent,
                  title: Text(item.label, style: AppText.body),
                  subtitle: Text(item.id, style: AppText.identifier),
                ),
              ),
            ],

            const SizedBox(height: 20),
            Row(
              children: <Widget>[
                Expanded(
                  child: SecondaryButton(
                    label: 'Cancel',
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: PrimaryButton(
                    label: 'Add finding',
                    onPressed: _description.text.trim().isEmpty
                        ? null
                        : () => Navigator.of(context).pop(
                              _FindingDraft(
                                rule: _rule,
                                severity: _severity,
                                description: _description.text.trim(),
                                note: _note.text.trim(),
                                evidenceIds: _selectedEvidence.toList(),
                              ),
                            ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
