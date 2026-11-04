enum VehicleType {
  car('Car'),
  motorcycle('Motorcycle'),
  bus('Bus'),
  truck('Truck'),
  van('Van'),
  pickup('Pickup'),
  suv('SUV'),
  other('Other');

  const VehicleType(this.label);

  final String label;

  String get apiValue => name.toUpperCase();

  static VehicleType fromApi(String? value) => VehicleType.values.firstWhere(
    (t) => t.apiValue == value?.toUpperCase(),
    orElse: () => VehicleType.other,
  );
}

enum VehicleStatus {
  active('Active'),
  inactive('Inactive'),
  maintenance('Maintenance');

  const VehicleStatus(this.label);

  final String label;

  String get apiValue => name.toUpperCase();

  static VehicleStatus fromApi(String? value) => VehicleStatus.values.firstWhere(
    (s) => s.apiValue == value?.toUpperCase(),
    orElse: () => VehicleStatus.inactive,
  );
}

class Vehicle {
  const Vehicle({
    required this.id,
    required this.vehicleNumber,
    required this.make,
    required this.model,
    required this.type,
    required this.manufactureYear,
    required this.status,
    required this.version,
    this.color,
    this.ownerName,
    this.createdAt,
    this.updatedAt,
  });

  final int id;
  final String vehicleNumber;
  final String make;
  final String model;
  final VehicleType type;
  final int manufactureYear;
  final String? color;
  final String? ownerName;
  final VehicleStatus status;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final int version;

  factory Vehicle.fromJson(Map<String, dynamic> json) => Vehicle(
    id: (json['id'] as num).toInt(),
    vehicleNumber: json['vehicleNumber'] as String? ?? '',
    make: json['make'] as String? ?? '',
    model: json['model'] as String? ?? '',
    type: VehicleType.fromApi(json['type'] as String?),
    manufactureYear: (json['manufactureYear'] as num?)?.toInt() ?? 0,
    color: json['color'] as String?,
    ownerName: json['ownerName'] as String?,
    status: VehicleStatus.fromApi(json['status'] as String?),
    createdAt: DateTime.tryParse(json['createdAt'] as String? ?? '')?.toLocal(),
    updatedAt: DateTime.tryParse(json['updatedAt'] as String? ?? '')?.toLocal(),
    version: (json['version'] as num?)?.toInt() ?? 0,
  );
}

/// Payload sent when registering or editing a vehicle.
class VehicleInput {
  const VehicleInput({
    required this.vehicleNumber,
    required this.make,
    required this.model,
    required this.type,
    required this.manufactureYear,
    required this.status,
    this.color,
    this.ownerName,
    this.version,
  });

  final String vehicleNumber;
  final String make;
  final String model;
  final VehicleType type;
  final int manufactureYear;
  final String? color;
  final String? ownerName;
  final VehicleStatus status;

  /// Version read before editing; lets the server reject lost updates.
  final int? version;

  Map<String, dynamic> toJson() => {
    'vehicleNumber': vehicleNumber.trim(),
    'make': make.trim(),
    'model': model.trim(),
    'type': type.apiValue,
    'manufactureYear': manufactureYear,
    'color': _blankToNull(color),
    'ownerName': _blankToNull(ownerName),
    'status': status.apiValue,
    if (version != null) 'version': version,
  };

  static String? _blankToNull(String? value) {
    final trimmed = value?.trim();
    return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
  }
}

class PageResult<T> {
  const PageResult({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  bool get isFirst => page <= 0;
  bool get isLast => totalPages == 0 || page >= totalPages - 1;

  factory PageResult.fromJson(Map<String, dynamic> json, T Function(Map<String, dynamic>) itemFromJson) =>
      PageResult(
        content: (json['content'] as List<dynamic>? ?? const [])
            .map((e) => itemFromJson(e as Map<String, dynamic>))
            .toList(),
        page: (json['page'] as num?)?.toInt() ?? 0,
        size: (json['size'] as num?)?.toInt() ?? 0,
        totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
        totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
      );
}
