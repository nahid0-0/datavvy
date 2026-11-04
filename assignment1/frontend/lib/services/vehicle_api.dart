import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/vehicle.dart';
import 'api_exception.dart';

class VehicleApi {
  VehicleApi({
    http.Client? client,
    String baseUrl = AppConfig.apiBaseUrl,
    this.timeout = AppConfig.requestTimeout,
  }) : _client = client ?? http.Client(),
       _base = Uri.parse(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/').resolve('api/v1/vehicles');

  final http.Client _client;
  final Uri _base;
  final Duration timeout;

  static const _jsonHeaders = {'Content-Type': 'application/json', 'Accept': 'application/json'};

  Future<PageResult<Vehicle>> list({
    String? search,
    VehicleType? type,
    VehicleStatus? status,
    int page = 0,
    int size = 10,
    String? sort,
  }) async {
    final query = <String, String>{
      'page': '$page',
      'size': '$size',
      if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
      if (type != null) 'type': type.apiValue,
      if (status != null) 'status': status.apiValue,
      'sort': ?sort,
    };
    final json = await _send(() => _client.get(_base.replace(queryParameters: query), headers: _jsonHeaders));
    return PageResult.fromJson(json as Map<String, dynamic>, Vehicle.fromJson);
  }

  Future<Vehicle> get(int id) async {
    final json = await _send(() => _client.get(_item(id), headers: _jsonHeaders));
    return Vehicle.fromJson(json as Map<String, dynamic>);
  }

  Future<Vehicle> create(VehicleInput input) async {
    final json = await _send(
      () => _client.post(_base, headers: _jsonHeaders, body: jsonEncode(input.toJson())),
    );
    return Vehicle.fromJson(json as Map<String, dynamic>);
  }

  Future<Vehicle> update(int id, VehicleInput input) async {
    final json = await _send(
      () => _client.put(_item(id), headers: _jsonHeaders, body: jsonEncode(input.toJson())),
    );
    return Vehicle.fromJson(json as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _send(() => _client.delete(_item(id), headers: _jsonHeaders));
  }

  /// Returns true when [vehicleNumber] is free. Pass [excludeId] while editing.
  Future<bool> isNumberAvailable(String vehicleNumber, {int? excludeId}) async {
    final uri = _base.replace(
      path: '${_base.path}/availability',
      queryParameters: {
        'vehicleNumber': vehicleNumber.trim(),
        if (excludeId != null) 'excludeId': '$excludeId',
      },
    );
    final json = await _send(() => _client.get(uri, headers: _jsonHeaders));
    return (json as Map<String, dynamic>)['available'] == true;
  }

  void close() => _client.close();

  Uri _item(int id) => _base.replace(path: '${_base.path}/$id');

  Future<dynamic> _send(Future<http.Response> Function() request) async {
    final http.Response response;
    try {
      response = await request().timeout(timeout);
    } on TimeoutException {
      throw const ApiException.network('The server took too long to respond. Please try again.');
    } on http.ClientException {
      throw const ApiException.network();
    }

    final body = response.body;
    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (body.isEmpty) return null;
      try {
        return jsonDecode(body);
      } on FormatException {
        throw ApiException(
          statusCode: response.statusCode,
          message: 'The server returned an unexpected response.',
        );
      }
    }
    throw _toException(response);
  }

  static ApiException _toException(http.Response response) {
    Map<String, dynamic>? problem;
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map<String, dynamic>) problem = decoded;
    } on FormatException {
      // Not JSON (e.g. a proxy error page) - fall through to a generic message.
    }

    final errors = <String, String>{};
    final rawErrors = problem?['errors'];
    if (rawErrors is Map) {
      rawErrors.forEach((k, v) => errors['$k'] = '$v');
    }

    return ApiException(
      statusCode: response.statusCode,
      code: problem?['code'] as String?,
      message: (problem?['detail'] as String?) ?? _fallbackMessage(response.statusCode),
      fieldErrors: errors,
    );
  }

  static String _fallbackMessage(int status) => switch (status) {
    400 => 'The request was invalid.',
    404 => 'The vehicle could not be found.',
    409 => 'The request conflicts with existing data.',
    >= 500 => 'The server encountered an error. Please try again later.',
    _ => 'Request failed (HTTP $status).',
  };
}
