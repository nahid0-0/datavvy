import 'package:flutter/material.dart';

import 'screens/vehicle_list_screen.dart';
import 'services/vehicle_api.dart';

void main() {
  runApp(StarkVmsApp(api: VehicleApi()));
}

class StarkVmsApp extends StatelessWidget {
  const StarkVmsApp({super.key, required this.api});

  final VehicleApi api;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Stark Group VMS',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF8B1E2D)),
        useMaterial3: true,
      ),
      home: VehicleListScreen(api: api),
    );
  }
}
