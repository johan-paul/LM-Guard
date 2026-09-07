import 'package:flutter/material.dart';

import '../../data/models/checklist_item.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import 'chips.dart';
import 'empty_state.dart';
import 'panels.dart';

/// Read-only listing of the statutory declaration checks on a record.
///
/// Shared by the review step and the inspection record screen, which
/// previously carried two copies of this layout.
class ChecklistPanel extends StatelessWidget {
  const ChecklistPanel({super.key, required this.items});

  final List<ChecklistItem> items;

  @override
  Widget build(BuildContext context) {
    // An empty checklist would otherwise render a zero-height hairline box
    // under a "0 of 0" heading, which reads as a broken screen.
    if (items.isEmpty) {
      return const AppPanel(
        child: EmptyState(
          compact: true,
          icon: Icons.checklist_rtl,
          title: 'No checklist recorded',
          message: 'The statutory declaration checks for this record have not '
              'been loaded.',
        ),
      );
    }

    final int lastIndex = items.length - 1;

    return AppPanel(
      padding: EdgeInsets.zero,
      child: Column(
        children: <Widget>[
          for (int i = 0; i <= lastIndex; i++)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 10),
              decoration: BoxDecoration(
                border: Border(
                  bottom: BorderSide(
                    // Indexed rather than compared by identity: two checklist
                    // rows can be equal in value, which made the old
                    // `item == items.last` test hide the wrong divider.
                    color: i == lastIndex
                        ? Colors.transparent
                        : AppColors.border,
                  ),
                ),
              ),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(items[i].title, style: AppText.body),
                        const SizedBox(height: 2),
                        Text(items[i].ruleRef, style: AppText.identifier),
                        if (items[i].note.isNotEmpty) ...<Widget>[
                          const SizedBox(height: 4),
                          Text(items[i].note, style: AppText.caption),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: 10),
                  CheckResultChip(items[i].result),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
