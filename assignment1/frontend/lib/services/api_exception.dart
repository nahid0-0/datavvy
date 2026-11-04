/// Error raised by [VehicleApi]. Mirrors the backend's RFC 9457 problem response.
class ApiException implements Exception {
  const ApiException({required this.message, this.statusCode, this.code, this.fieldErrors = const {}});

  const ApiException.network([String? message])
    : this(message: message ?? 'Cannot reach the server. Check that the backend is running and try again.');

  /// Null for network failures (no HTTP response was received).
  final int? statusCode;

  /// Backend error code, e.g. DUPLICATE_VEHICLE_NUMBER, VERSION_CONFLICT.
  final String? code;

  final String message;

  /// Field name -> message, for validation failures.
  final Map<String, String> fieldErrors;

  bool get isNetworkError => statusCode == null;
  bool get isNotFound => statusCode == 404;
  bool get isDuplicateNumber => code == 'DUPLICATE_VEHICLE_NUMBER';
  bool get isVersionConflict => code == 'VERSION_CONFLICT';
  bool get isValidation => statusCode == 400 && fieldErrors.isNotEmpty;

  @override
  String toString() => 'ApiException($statusCode, $code): $message';
}
