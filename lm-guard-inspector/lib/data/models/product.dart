/// A packaged commodity under inspection.
class Product {
  const Product({
    required this.id,
    required this.name,
    required this.brand,
    required this.manufacturer,
    required this.category,
    required this.batchNumber,
    required this.manufacturedOn,
    required this.expiresOn,
    required this.barcode,
    required this.netQuantity,
    required this.mrp,
  });

  final String id;
  final String name;
  final String brand;
  final String manufacturer;
  final String category;
  final String batchNumber;
  final DateTime manufacturedOn;
  final DateTime expiresOn;
  final String barcode;
  final String netQuantity;
  final String mrp;

  bool get isExpired => expiresOn.isBefore(DateTime.now());

  Product copyWith({
    String? name,
    String? brand,
    String? manufacturer,
    String? category,
    String? batchNumber,
    DateTime? manufacturedOn,
    DateTime? expiresOn,
    String? barcode,
    String? netQuantity,
    String? mrp,
  }) {
    return Product(
      id: id,
      name: name ?? this.name,
      brand: brand ?? this.brand,
      manufacturer: manufacturer ?? this.manufacturer,
      category: category ?? this.category,
      batchNumber: batchNumber ?? this.batchNumber,
      manufacturedOn: manufacturedOn ?? this.manufacturedOn,
      expiresOn: expiresOn ?? this.expiresOn,
      barcode: barcode ?? this.barcode,
      netQuantity: netQuantity ?? this.netQuantity,
      mrp: mrp ?? this.mrp,
    );
  }

  factory Product.fromJson(Map<String, dynamic> json) {
    return Product(
      id: json['id'] as String,
      name: json['name'] as String,
      brand: json['brand'] as String? ?? '',
      manufacturer: json['manufacturer'] as String? ?? '',
      category: json['category'] as String? ?? '',
      batchNumber: json['batchNumber'] as String? ?? '',
      manufacturedOn: DateTime.parse(json['manufacturedOn'] as String),
      expiresOn: DateTime.parse(json['expiresOn'] as String),
      barcode: json['barcode'] as String? ?? '',
      netQuantity: json['netQuantity'] as String? ?? '',
      mrp: json['mrp'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        'brand': brand,
        'manufacturer': manufacturer,
        'category': category,
        'batchNumber': batchNumber,
        'manufacturedOn': manufacturedOn.toIso8601String(),
        'expiresOn': expiresOn.toIso8601String(),
        'barcode': barcode,
        'netQuantity': netQuantity,
        'mrp': mrp,
      };
}
