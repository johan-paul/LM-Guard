/// A lightweight match from the backend's registered-product search
/// (`GET /api/products?search=`) - backs the Information step's autocomplete.
/// The full [Product] record is only fetched/created once a suggestion is
/// actually selected and attached to an inspection.
class ProductSuggestion {
  const ProductSuggestion({
    required this.id,
    required this.name,
    required this.brand,
  });

  final String id;
  final String name;
  final String brand;

  factory ProductSuggestion.fromJson(Map<String, dynamic> json) {
    return ProductSuggestion(
      id: json['id'] as String,
      name: json['productName'] as String? ?? '',
      brand: json['brand'] as String? ?? '',
    );
  }

  /// What the autocomplete option and the field show once selected.
  String get displayLabel => brand.isEmpty ? name : '$name ($brand)';
}
