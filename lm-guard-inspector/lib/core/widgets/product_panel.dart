import 'package:flutter/material.dart';

import '../../data/models/product.dart';
import '../theme/app_text_styles.dart';
import 'panels.dart';

/// The identified commodity, or a plain statement that none was recorded.
///
/// Takes the nullable [Product] and resolves it to a non-null local exactly
/// once. The screens that render this used to branch on `record.product ==
/// null` and then read `record.product!` a dozen times — every one of those
/// reads a separate opportunity for a null-check failure if the record
/// changed between them. Here the check and the reads cannot disagree.
class ProductPanel extends StatelessWidget {
  const ProductPanel({super.key, required this.product, this.showIdentifier = true});

  final Product? product;
  final bool showIdentifier;

  @override
  Widget build(BuildContext context) {
    final Product? item = product;

    if (item == null) {
      return const AppPanel(
        child: Text(
          'No product identified.',
          style: AppText.bodyMuted,
        ),
      );
    }

    return AppPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(item.name, style: AppText.recordTitle),
          if (showIdentifier) ...<Widget>[
            const SizedBox(height: 3),
            Text(item.id, style: AppText.identifier),
          ],
          const SizedBox(height: 12),
          const Divider(height: 1),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: LabelledValue(label: 'Brand', value: item.brand),
              ),
              Expanded(
                child: LabelledValue(label: 'Batch', value: item.batchNumber),
              ),
            ],
          ),
          const SizedBox(height: 12),
          LabelledValue(label: 'Manufacturer', value: item.manufacturer),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: LabelledValue(
                  label: 'Net quantity',
                  value: item.netQuantity,
                ),
              ),
              Expanded(
                child: LabelledValue(label: 'Retail price', value: item.mrp),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
