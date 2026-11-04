import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:stark_vms/models/vehicle.dart';
import 'package:stark_vms/services/api_exception.dart';
import 'package:stark_vms/services/vehicle_api.dart';

Map<String, dynamic> vehicleJson({int id = 1, String number = 'DHA-1234', int version = 0}) => {
  'id': id,
  'vehicleNumber': number,
  'make': 'Toyota',
  'model': 'Corolla',
  'type': 'CAR',
  'manufactureYear': 2022,
  'color': null,
  'ownerName': 'Tony Stark',
  'status': 'ACTIVE',
  'createdAt': '2026-11-04T10:00:00Z',
  'updatedAt': '2026-11-04T10:00:00Z',
  'version': version,
};

http.Response problem(int status, String code, {Map<String, String>? errors, String detail = 'x'}) =>
    http.Response(
      jsonEncode({'status': status, 'code': code, 'detail': detail, 'errors': ?errors}),
      status,
      headers: {'content-type': 'application/problem+json'},
    );

void main() {
  const input = VehicleInput(
    vehicleNumber: '  dha-1234 ',
    make: ' Toyota ',
    model: 'Corolla',
    type: VehicleType.car,
    manufactureYear: 2022,
    color: '   ',
    status: VehicleStatus.active,
    version: 3,
  );

  test('list builds the query string and parses the page', () async {
    late Uri captured;
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((req) async {
        captured = req.url;
        return http.Response(
          jsonEncode({
            'content': [vehicleJson()],
            'page': 0,
            'size': 10,
            'totalElements': 1,
            'totalPages': 1,
          }),
          200,
        );
      }),
    );

    final page = await api.list(
      search: '  dha ',
      type: VehicleType.truck,
      page: 2,
      size: 10,
      sort: 'make,asc',
    );

    expect(captured.path, '/api/v1/vehicles');
    expect(captured.queryParameters, {
      'page': '2',
      'size': '10',
      'search': 'dha',
      'type': 'TRUCK',
      'sort': 'make,asc',
    });
    expect(page.content.single.vehicleNumber, 'DHA-1234');
    expect(page.isLast, isTrue);
  });

  test('create sends trimmed JSON, nulls blank optionals and includes version', () async {
    late Map<String, dynamic> body;
    final api = VehicleApi(
      baseUrl: 'http://api.test/',
      client: MockClient((req) async {
        expect(req.method, 'POST');
        expect(req.headers['Content-Type'], startsWith('application/json'));
        body = jsonDecode(req.body) as Map<String, dynamic>;
        return http.Response(jsonEncode(vehicleJson()), 201);
      }),
    );

    await api.create(input);

    expect(body['vehicleNumber'], 'dha-1234');
    expect(body['make'], 'Toyota');
    expect(body['color'], isNull);
    expect(body['type'], 'CAR');
    expect(body['version'], 3);
  });

  test('duplicate number is surfaced with code and field error', () async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient(
        (_) async =>
            problem(409, 'DUPLICATE_VEHICLE_NUMBER', errors: {'vehicleNumber': 'is already registered'}),
      ),
    );

    await expectLater(
      api.create(input),
      throwsA(
        isA<ApiException>()
            .having((e) => e.isDuplicateNumber, 'isDuplicateNumber', isTrue)
            .having((e) => e.fieldErrors['vehicleNumber'], 'field error', 'is already registered'),
      ),
    );
  });

  test('non-JSON error body falls back to a friendly message', () async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((_) async => http.Response('<html>Bad Gateway</html>', 502)),
    );

    await expectLater(
      api.get(1),
      throwsA(
        isA<ApiException>()
            .having((e) => e.statusCode, 'status', 502)
            .having((e) => e.message, 'message', contains('server')),
      ),
    );
  });

  test('connection failures become network errors', () async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((_) async => throw http.ClientException('XMLHttpRequest error')),
    );

    await expectLater(
      api.delete(1),
      throwsA(isA<ApiException>().having((e) => e.isNetworkError, 'isNetworkError', isTrue)),
    );
  });

  test('timeouts become network errors', () async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      timeout: const Duration(milliseconds: 10),
      client: MockClient((_) => Future.delayed(const Duration(seconds: 1), () => http.Response('{}', 200))),
    );

    await expectLater(
      api.get(1),
      throwsA(isA<ApiException>().having((e) => e.isNetworkError, 'isNetworkError', isTrue)),
    );
  });

  test('availability passes excludeId and parses the flag', () async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((req) async {
        expect(req.url.path, '/api/v1/vehicles/availability');
        expect(req.url.queryParameters, {'vehicleNumber': 'DHA 1', 'excludeId': '7'});
        return http.Response(jsonEncode({'vehicleNumber': 'DHA 1', 'available': false}), 200);
      }),
    );

    expect(await api.isNumberAvailable(' DHA 1 ', excludeId: 7), isFalse);
  });

  test('delete handles 204 No Content', () async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((req) async {
        expect(req.method, 'DELETE');
        expect(req.url.path, '/api/v1/vehicles/5');
        return http.Response('', 204);
      }),
    );

    await api.delete(5);
  });
}
