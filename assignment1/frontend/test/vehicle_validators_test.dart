import 'package:flutter_test/flutter_test.dart';
import 'package:stark_vms/utils/vehicle_validators.dart';

void main() {
  group('vehicleNumber', () {
    test('accepts realistic plates', () {
      for (final ok in ['DHA-1234', 'DHA METRO GA 12-3456', 'KA 01 AB 1234', 'ঢাকা-১২৩৪']) {
        expect(VehicleValidators.vehicleNumber(ok), isNull, reason: ok);
      }
    });

    test('rejects malformed plates', () {
      for (final bad in ['', '   ', 'A', '-DHA1', 'DHA1-', 'DHA#1', 'ABCDEF', 'DHA_12', '1' * 33]) {
        expect(VehicleValidators.vehicleNumber(bad), isNotNull, reason: bad);
      }
    });
  });

  test('year range is 1886..next year', () {
    final next = DateTime.now().year + 1;
    expect(VehicleValidators.year('1886'), isNull);
    expect(VehicleValidators.year('$next'), isNull);
    expect(VehicleValidators.year('1885'), isNotNull);
    expect(VehicleValidators.year('${next + 1}'), isNotNull);
    expect(VehicleValidators.year(''), isNotNull);
    expect(VehicleValidators.year('abcd'), isNotNull);
  });

  test('normalizedKey matches backend duplicate rules', () {
    expect(VehicleValidators.normalizedKey('dha - 12 34'), 'DHA1234');
    expect(VehicleValidators.normalizedKey('DHA1234'), 'DHA1234');
  });
}
