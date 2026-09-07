import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/fields.dart';
import '../../../core/widgets/panels.dart';
import '../../../data/models/product.dart';
import '../../../state/draft_controller.dart';
import '../../product_history/product_history_sheet.dart';

/// Step 2 — identify the commodity being inspected.
class ProductStep extends StatefulWidget {
  const ProductStep({super.key});

  @override
  State<ProductStep> createState() => _ProductStepState();
}

class _ProductStepState extends State<ProductStep> {
  static const List<String> _modes = <String>['Search', 'Scan code', 'Enter manually'];

  int _mode = 0;
  String _query = '';
  bool _searching = false;
  List<Product> _results = <Product>[];

  final TextEditingController _barcodeController = TextEditingController();
  bool _scanning = false;
  String? _scanMessage;

  final TextEditingController _name = TextEditingController();
  final TextEditingController _brand = TextEditingController();
  final TextEditingController _manufacturer = TextEditingController();
  final TextEditingController _batch = TextEditingController();
  final TextEditingController _quantity = TextEditingController();
  final TextEditingController _price = TextEditingController();

  @override
  void initState() {
    super.initState();
    _runSearch('');
  }

  @override
  void dispose() {
    _barcodeController.dispose();
    _name.dispose();
    _brand.dispose();
    _manufacturer.dispose();
    _batch.dispose();
    _quantity.dispose();
    _price.dispose();
    super.dispose();
  }

  Future<void> _runSearch(String query) async {
    setState(() {
      _query = query;
      _searching = true;
    });
    final List<Product> found =
        await context.read<DraftController>().searchProducts(query);
    if (!mounted) return;
    setState(() {
      _results = found;
      _searching = false;
    });
  }

  Future<void> _simulateScan() async {
    setState(() {
      _scanning = true;
      _scanMessage = null;
    });

    final DraftController draft = context.read<DraftController>();
    final String code = _barcodeController.text.trim().isEmpty
        ? '8901234567890'
        : _barcodeController.text.trim();

    final Product? match = await draft.lookupBarcode(code);
    if (!mounted) return;

    setState(() {
      _scanning = false;
      _scanMessage = match == null
          ? 'No product matched code $code. Enter the details manually.'
          : null;
    });

    if (match != null) draft.setProduct(match);
  }

  void _saveManualProduct() {
    if (_name.text.trim().isEmpty) return;
    final DateTime now = DateTime.now();
    context.read<DraftController>().setProduct(
          Product(
            id: 'PRD-FIELD-${now.millisecondsSinceEpoch % 10000}',
            name: _name.text.trim(),
            brand: _brand.text.trim(),
            manufacturer: _manufacturer.text.trim(),
            category: 'Recorded in field',
            batchNumber: _batch.text.trim(),
            manufacturedOn: now,
            expiresOn: DateTime(now.year + 1, now.month, now.day),
            barcode: _barcodeController.text.trim(),
            netQuantity: _quantity.text.trim(),
            mrp: _price.text.trim(),
          ),
        );
    FocusScope.of(context).unfocus();
  }

  @override
  Widget build(BuildContext context) {
    final DraftController draft = context.watch<DraftController>();
    final Product? selected = draft.inspection.product;

    if (selected != null) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        children: <Widget>[
          const InfoBanner(
            message: 'Product identified. Confirm the printed particulars against the package before continuing.',
            icon: Icons.check_circle_outline,
            tone: BannerTone.info,
          ),
          const SizedBox(height: 16),
          ProductSummaryCard(product: selected),
          const SizedBox(height: 14),
          SecondaryButton(
            label: 'View product history',
            icon: Icons.history,
            onPressed: () => showProductHistorySheet(context, draft),
          ),
          const SizedBox(height: 10),
          SecondaryButton(
            label: 'Choose a different product',
            icon: Icons.swap_horiz,
            onPressed: () {
              draft.clearProduct();
              setState(() => _mode = 0);
              _runSearch('');
            },
          ),
        ],
      );
    }

    // This step fills a pane of fixed height: the workflow screen renders it
    // inside `Expanded(child: _stepBody(...))`, so the Column below always
    // receives bounded height and its Expanded child is legal. Do not place
    // this widget inside a scrollable — an Expanded under an unbounded Column
    // is exactly the constraint violation that blanked the app.
    return Column(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
          child: FilterChipsRow(
            labels: _modes,
            selectedIndex: _mode,
            onSelected: (int index) => setState(() => _mode = index),
          ),
        ),
        Expanded(child: _body()),
      ],
    );
  }

  Widget _body() {
    switch (_mode) {
      case 1:
        return _scanMode();
      case 2:
        return _manualMode();
      default:
        return _searchMode();
    }
  }

  Widget _searchMode() {
    return Column(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: AppSearchField(
            hint: 'Search by product, brand or manufacturer',
            onChanged: _runSearch,
          ),
        ),
        const SizedBox(height: 12),
        Expanded(
          child: _searching
              ? const LoadingState(message: 'Searching the product register')
              : _results.isEmpty
                  ? EmptyState(
                      icon: Icons.search_off,
                      title: 'No product found',
                      message: _query.isEmpty
                          ? 'The product register is empty.'
                          : 'Nothing matched "$_query". Try the barcode or enter the details manually.',
                      action: SecondaryButton(
                        label: 'Enter manually',
                        expand: false,
                        icon: Icons.edit_outlined,
                        onPressed: () => setState(() => _mode = 2),
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                      itemCount: _results.length,
                      itemBuilder: (BuildContext context, int index) {
                        final Product product = _results[index];
                        return AppPanel(
                          margin: const EdgeInsets.only(bottom: 10),
                          onTap: () =>
                              context.read<DraftController>().setProduct(product),
                          child: Row(
                            children: <Widget>[
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: <Widget>[
                                    Text(product.name, style: AppText.recordTitle),
                                    const SizedBox(height: 3),
                                    Text(
                                      '${product.manufacturer} · ${product.netQuantity}',
                                      style: AppText.caption,
                                    ),
                                    const SizedBox(height: 4),
                                    Text(product.barcode, style: AppText.identifier),
                                  ],
                                ),
                              ),
                              const Icon(
                                Icons.chevron_right,
                                size: 20,
                                color: AppColors.inkFaint,
                              ),
                            ],
                          ),
                        );
                      },
                    ),
        ),
      ],
    );
  }

  Widget _scanMode() {
    final String? scanMessage = _scanMessage;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      children: <Widget>[
        AspectRatio(
          aspectRatio: 4 / 3,
          child: Container(
            decoration: BoxDecoration(
              color: AppColors.navy,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Stack(
              alignment: Alignment.center,
              children: <Widget>[
                Container(
                  margin: const EdgeInsets.symmetric(horizontal: 40, vertical: 46),
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.white.withOpacity(0.4)),
                    borderRadius: BorderRadius.circular(6),
                  ),
                ),
                if (_scanning)
                  const SizedBox(
                    width: 26,
                    height: 26,
                    child: CircularProgressIndicator(
                      strokeWidth: 2.4,
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  )
                else
                  Column(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Icon(
                        Icons.qr_code_scanner,
                        size: 34,
                        color: Colors.white.withOpacity(0.75),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Align the barcode within the frame',
                        style: TextStyle(
                          fontSize: 12.5,
                          color: Colors.white.withOpacity(0.75),
                        ),
                      ),
                    ],
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 14),
        PrimaryButton(
          label: _scanning ? 'Reading code' : 'Scan barcode',
          icon: Icons.qr_code_scanner,
          busy: _scanning,
          onPressed: _simulateScan,
        ),
        const SizedBox(height: 16),
        AppTextField(
          label: 'Or key the barcode',
          controller: _barcodeController,
          hint: '8901234567890',
          keyboardType: TextInputType.number,
        ),
        if (scanMessage != null) ...<Widget>[
          const SizedBox(height: 14),
          InfoBanner(
            message: scanMessage,
            icon: Icons.error_outline,
            tone: BannerTone.warning,
          ),
        ],
        const SizedBox(height: 14),
        const InfoBanner(
          message:
              'Scanning is simulated in this build. Wiring a scanner package '
              'replaces only the lookup call in DraftController.',
        ),
      ],
    );
  }

  Widget _manualMode() {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      children: <Widget>[
        const InfoBanner(
          message: 'Copy the particulars exactly as printed on the package.',
        ),
        const SizedBox(height: 16),
        AppTextField(label: 'Product name', required: true, controller: _name),
        const SizedBox(height: 14),
        AppTextField(label: 'Brand', controller: _brand),
        const SizedBox(height: 14),
        AppTextField(label: 'Manufacturer / packer', controller: _manufacturer),
        const SizedBox(height: 14),
        AppTextField(label: 'Batch number', controller: _batch),
        const SizedBox(height: 14),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(
              child: AppTextField(
                label: 'Net quantity',
                controller: _quantity,
                hint: '500 g',
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: AppTextField(
                label: 'Retail sale price',
                controller: _price,
                hint: 'Rs. 99.00',
              ),
            ),
          ],
        ),
        const SizedBox(height: 20),
        PrimaryButton(
          label: 'Use these details',
          icon: Icons.check,
          onPressed: _saveManualProduct,
        ),
      ],
    );
  }
}

/// Read-only summary of the identified commodity.
class ProductSummaryCard extends StatelessWidget {
  const ProductSummaryCard({super.key, required this.product});

  final Product product;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(product.name, style: AppText.recordTitle),
          const SizedBox(height: 3),
          Text(product.id, style: AppText.identifier),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(child: LabelledValue(label: 'Brand', value: product.brand)),
              Expanded(
                child: LabelledValue(
                  label: 'Net quantity',
                  value: product.netQuantity,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          LabelledValue(label: 'Manufacturer', value: product.manufacturer),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: LabelledValue(label: 'Batch', value: product.batchNumber),
              ),
              Expanded(
                child: LabelledValue(
                  label: 'Retail price',
                  value: product.mrp,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: LabelledValue(
                  label: 'Manufactured',
                  value: Fmt.date(product.manufacturedOn),
                ),
              ),
              Expanded(
                child: LabelledValue(
                  label: 'Expires',
                  value: Fmt.date(product.expiresOn),
                  valueStyle: AppText.body.copyWith(
                    fontWeight: FontWeight.w500,
                    color: product.isExpired ? AppColors.danger : AppColors.ink,
                  ),
                ),
              ),
            ],
          ),
          if (product.barcode.isNotEmpty) ...<Widget>[
            const SizedBox(height: 14),
            LabelledValue(label: 'Barcode', value: product.barcode),
          ],
        ],
      ),
    );
  }
}
