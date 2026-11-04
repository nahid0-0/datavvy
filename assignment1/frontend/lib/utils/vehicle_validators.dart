/// Client-side validation mirroring the backend rules, so users get instant feedback.
/// The server remains the source of truth and re-validates everything.
class VehicleValidators {
  const VehicleValidators._();

  static final RegExp _numberPattern = RegExp(
    r'^(?=.*\p{N})[\p{L}\p{M}\p{N}]+(?:[\s-]+[\p{L}\p{M}\p{N}]+)*$',
    unicode: true,
  );

  static const int minYear = 1886;
  static int get maxYear => DateTime.now().year + 1;

  static String? vehicleNumber(String? value) {
    final v = value?.trim() ?? '';
    if (v.isEmpty) return 'Vehicle number is required';
    if (v.length < 2) return 'Must be at least 2 characters';
    if (v.length > 32) return 'Must be at most 32 characters';
    if (!_numberPattern.hasMatch(v)) {
      return 'Use letters, digits, spaces or hyphens; start/end with a letter '
          'or digit and include at least one digit';
    }
    return null;
  }

  static String? Function(String?) requiredText(String label, {required int max}) {
    return (value) {
      final v = value?.trim() ?? '';
      if (v.isEmpty) return '$label is required';
      if (v.length > max) return '$label must be at most $max characters';
      return null;
    };
  }

  static String? Function(String?) optionalText(String label, {required int max}) {
    return (value) {
      final v = value?.trim() ?? '';
      if (v.length > max) return '$label must be at most $max characters';
      return null;
    };
  }

  static String? year(String? value) {
    final v = value?.trim() ?? '';
    if (v.isEmpty) return 'Manufacture year is required';
    final year = int.tryParse(v);
    if (year == null) return 'Enter a valid year';
    if (year < minYear || year > maxYear) {
      return 'Must be between $minYear and $maxYear';
    }
    return null;
  }

  /// Same canonical key the backend uses for duplicate detection.
  static String normalizedKey(String value) =>
      value.replaceAll(RegExp(r'[^\p{L}\p{M}\p{N}]', unicode: true), '').toUpperCase();
}
