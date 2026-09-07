import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_text_styles.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/panels.dart';
import '../../data/models/product_history.dart';
import '../../state/draft_controller.dart';

/// Opens the Product History bottom sheet for the product currently
/// identified on [draft]. A modal sheet rather than a pushed screen: it sits
/// on top of the same `Navigator` route as the wizard, so the in-progress
/// inspection (checklist answers, AI result, notes, findings) is still the
/// exact same [DraftController] instance underneath when it closes - nothing
/// about the workflow state is touched by opening this.
Future<void> showProductHistorySheet(BuildContext context, DraftController draft) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    backgroundColor: AppColors.canvas,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (BuildContext context) => _ProductHistorySheet(draft: draft),
  );
}

class _ProductHistorySheet extends StatefulWidget {
  const _ProductHistorySheet({required this.draft});

  final DraftController draft;

  @override
  State<_ProductHistorySheet> createState() => _ProductHistorySheetState();
}

class _ProductHistorySheetState extends State<_ProductHistorySheet> {
  late Future<ProductHistorySummary> _future;

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _future = widget.draft.fetchProductHistory();
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.68,
      minChildSize: 0.4,
      maxChildSize: 0.92,
      expand: false,
      builder: (BuildContext context, ScrollController controller) {
        return FutureBuilder<ProductHistorySummary>(
          future: _future,
          builder: (BuildContext context, AsyncSnapshot<ProductHistorySummary> snapshot) {
            return ListView(
              controller: controller,
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
              children: <Widget>[
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 14),
                    decoration: BoxDecoration(
                      color: AppColors.borderStrong,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        widget.draft.inspection.productName,
                        style: AppText.pageTitle,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                  ],
                ),
                const Text('Product history', style: AppText.bodyMuted),
                const SizedBox(height: 16),
                if (snapshot.connectionState != ConnectionState.done)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 32),
                    child: Center(child: Text('Loading product history…', style: AppText.bodyMuted)),
                  )
                else if (snapshot.hasError)
                  _ErrorState(onRetry: () => setState(_load))
                else
                  _Content(summary: snapshot.data!),
              ],
            );
          },
        );
      },
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Column(
        children: <Widget>[
          const Icon(Icons.error_outline, color: AppColors.danger, size: 28),
          const SizedBox(height: 8),
          const Text('Product history could not be loaded.', style: AppText.body),
          const SizedBox(height: 10),
          OutlinedButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    );
  }
}

class _Content extends StatelessWidget {
  const _Content({required this.summary});

  final ProductHistorySummary summary;

  @override
  Widget build(BuildContext context) {
    if (summary.previousInspections == 0) {
      return const AppPanel(
        child: Text(
          'No previous inspection history found for this product.',
          style: AppText.bodyMuted,
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Row(
          children: <Widget>[
            _riskBadge(summary.riskLabel),
            const SizedBox(width: 8),
            Text('${summary.previousInspections} previous inspections', style: AppText.body),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: <Widget>[
            Expanded(child: _stat('Compliant', summary.compliantCount, AppColors.success)),
            Expanded(child: _stat('Non-compliant', summary.nonCompliantCount, AppColors.danger)),
            Expanded(child: _stat('Inconclusive', summary.inconclusiveCount, AppColors.warning)),
          ],
        ),
        const SizedBox(height: 14),
        AppPanel(
          leadingStripe: summary.hasRepeatIssue ? AppColors.danger : null,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                'Previous violations: ${summary.previousViolations}  ·  ${summary.repeatViolations} repeat',
                style: AppText.body,
              ),
              if (summary.hasRepeatIssue) ...<Widget>[
                const SizedBox(height: 6),
                Text(
                  'Repeat non-compliance recorded against this product. Check whether the same declarations are at fault again.',
                  style: AppText.caption.copyWith(color: AppColors.danger),
                ),
              ],
            ],
          ),
        ),
        if (summary.declarationChanges.isNotEmpty) ...<Widget>[
          const SizedBox(height: 16),
          const Text('DECLARATION CHANGES', style: AppText.label),
          const SizedBox(height: 4),
          const Text(
            'What was actually printed on the package changed between scans - not just whether '
            'past inspections passed or failed.',
            style: AppText.caption,
          ),
          const SizedBox(height: 8),
          ...summary.declarationChanges.take(8).map(
                (ProductDeclarationChange change) => AppPanel(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Text(change.field, style: AppText.identifier),
                            const SizedBox(height: 4),
                            Text.rich(
                              TextSpan(
                                children: <InlineSpan>[
                                  TextSpan(
                                    text: change.previousValue ?? 'not detected',
                                    style: AppText.caption.copyWith(
                                      decoration: TextDecoration.lineThrough,
                                      color: AppColors.inkFaint,
                                    ),
                                  ),
                                  const TextSpan(text: '  →  ', style: AppText.caption),
                                  TextSpan(
                                    text: change.newValue ?? 'not detected',
                                    style: AppText.body.copyWith(fontWeight: FontWeight.w600),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      Text(Fmt.dayTime(change.changedAt), style: AppText.caption),
                    ],
                  ),
                ),
              ),
        ],
        if (summary.recentInspections.isNotEmpty) ...<Widget>[
          const SizedBox(height: 16),
          const Text('RECENT INSPECTIONS', style: AppText.label),
          const SizedBox(height: 8),
          ...summary.recentInspections.take(5).map((PreviousInspectionEntry entry) => AppPanel(
                margin: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: <Widget>[
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(entry.establishment ?? entry.inspectionId, style: AppText.recordTitle),
                          const SizedBox(height: 2),
                          Text(Fmt.dayTime(entry.inspectedOn), style: AppText.caption),
                        ],
                      ),
                    ),
                    Text(entry.result, style: AppText.caption.copyWith(fontWeight: FontWeight.w600)),
                  ],
                ),
              )),
        ],
      ],
    );
  }

  Widget _riskBadge(String label) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.canvas,
        border: Border.all(color: AppColors.borderStrong),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text('RISK · $label'.toUpperCase(), style: AppText.label),
    );
  }

  Widget _stat(String label, int value, Color color) {
    return Column(
      children: <Widget>[
        Text('$value', style: AppText.pageTitle.copyWith(color: color)),
        Text(label, style: AppText.caption),
      ],
    );
  }
}
