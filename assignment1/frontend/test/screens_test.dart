import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:stark_vms/main.dart';
import 'package:stark_vms/screens/vehicle_form_screen.dart';
import 'package:stark_vms/services/vehicle_api.dart';

import 'vehicle_api_test.dart' show problem, vehicleJson;

http.Response page(List<Map<String, dynamic>> items) => http.Response(
  jsonEncode({
    'content': items,
    'page': 0,
    'size': 10,
    'totalElements': items.length,
    'totalPages': items.isEmpty ? 0 : 1,
  }),
  200,
);

Future<void> pumpWide(WidgetTester tester, Widget widget) async {
  tester.view.physicalSize = const Size(1400, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(widget);
}

void main() {
  testWidgets('list shows vehicles from the API', (tester) async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((_) async => page([vehicleJson(), vehicleJson(id: 2, number: 'CTG-55')])),
    );
    await pumpWide(tester, StarkVmsApp(api: api));
    await tester.pumpAndSettle();

    expect(find.text('DHA-1234'), findsOneWidget);
    expect(find.text('CTG-55'), findsOneWidget);
    expect(find.text('1–2 of 2'), findsOneWidget);
  });

  testWidgets('list shows empty state', (tester) async {
    final api = VehicleApi(baseUrl: 'http://api.test', client: MockClient((_) async => page([])));
    await pumpWide(tester, StarkVmsApp(api: api));
    await tester.pumpAndSettle();

    expect(find.text('No vehicles registered yet'), findsOneWidget);
  });

  testWidgets('list shows retryable error when backend is down', (tester) async {
    var calls = 0;
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((_) async {
        calls++;
        if (calls == 1) throw http.ClientException('down');
        return page([vehicleJson()]);
      }),
    );
    await pumpWide(tester, StarkVmsApp(api: api));
    await tester.pumpAndSettle();

    expect(find.text('Could not load vehicles'), findsOneWidget);
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('DHA-1234'), findsOneWidget);
  });

  testWidgets('delete asks for confirmation then calls the API', (tester) async {
    var deleted = false;
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((req) async {
        if (req.method == 'DELETE') {
          deleted = true;
          return http.Response('', 204);
        }
        return page(deleted ? [] : [vehicleJson()]);
      }),
    );
    await pumpWide(tester, StarkVmsApp(api: api));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Delete'));
    await tester.pumpAndSettle();
    expect(find.text('Delete vehicle?'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();

    expect(deleted, isTrue);
    expect(find.text('Vehicle DHA-1234 deleted'), findsOneWidget);
    expect(find.text('No vehicles registered yet'), findsOneWidget);
  });

  testWidgets('form blocks submit on client validation errors', (tester) async {
    var posted = false;
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((req) async {
        if (req.method == 'POST') posted = true;
        return http.Response('{}', 200);
      }),
    );
    await pumpWide(tester, MaterialApp(home: VehicleFormScreen(api: api)));

    await tester.tap(find.text('Register'));
    await tester.pumpAndSettle();

    expect(find.text('Vehicle number is required'), findsOneWidget);
    expect(find.text('Make is required'), findsOneWidget);
    expect(find.text('Type is required'), findsOneWidget);
    expect(posted, isFalse);
  });

  testWidgets('form shows server duplicate error on the number field', (tester) async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((req) async {
        if (req.url.path.endsWith('/availability')) {
          // Simulate a race: number looked free when typed, taken by the time we save.
          return http.Response(jsonEncode({'vehicleNumber': 'DHA-1', 'available': true}), 200);
        }
        return problem(409, 'DUPLICATE_VEHICLE_NUMBER', errors: {'vehicleNumber': 'is already registered'});
      }),
    );
    await pumpWide(tester, MaterialApp(home: VehicleFormScreen(api: api)));

    await tester.enterText(find.widgetWithText(TextFormField, 'Vehicle number *'), 'dha-1');
    await tester.enterText(find.widgetWithText(TextFormField, 'Make *'), 'Toyota');
    await tester.enterText(find.widgetWithText(TextFormField, 'Model *'), 'Corolla');
    await tester.enterText(find.widgetWithText(TextFormField, 'Manufacture year *'), '2020');
    await tester.tap(find.text('Type *'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Car').last);
    await tester.pumpAndSettle();

    // Number is upper-cased as the user types.
    expect(find.text('DHA-1'), findsOneWidget);

    await tester.tap(find.text('Register'));
    await tester.pumpAndSettle();

    expect(find.text('This vehicle number is already registered'), findsOneWidget);
  });

  testWidgets('edit form handles a vehicle deleted by someone else', (tester) async {
    final api = VehicleApi(
      baseUrl: 'http://api.test',
      client: MockClient((_) async => problem(404, 'RESOURCE_NOT_FOUND')),
    );
    await pumpWide(tester, MaterialApp(home: VehicleFormScreen(api: api, vehicleId: 9)));
    await tester.pumpAndSettle();

    expect(find.text('This vehicle no longer exists.'), findsOneWidget);
  });
}
