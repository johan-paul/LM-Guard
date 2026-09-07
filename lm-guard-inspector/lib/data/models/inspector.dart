/// The signed-in inspecting officer.
class Inspector {
  const Inspector({
    required this.id,
    required this.name,
    required this.designation,
    required this.zone,
    required this.email,
    required this.phone,
    required this.office,
    this.accountStatus = 'Active',
  });

  final String id;
  final String name;
  final String designation;
  final String zone;
  final String email;
  final String phone;
  final String office;
  final String accountStatus;

  factory Inspector.fromJson(Map<String, dynamic> json) {
    return Inspector(
      id: json['id'] as String,
      name: json['name'] as String,
      designation: json['designation'] as String? ?? 'Inspecting Officer',
      zone: json['zone'] as String? ?? '',
      email: json['email'] as String? ?? '',
      phone: json['phone'] as String? ?? '',
      office: json['office'] as String? ?? '',
      accountStatus: json['accountStatus'] as String? ?? 'Active',
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        'designation': designation,
        'zone': zone,
        'email': email,
        'phone': phone,
        'office': office,
        'accountStatus': accountStatus,
      };
}
