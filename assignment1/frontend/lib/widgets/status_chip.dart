import 'package:flutter/material.dart';

import '../models/vehicle.dart';

class StatusChip extends StatelessWidget {
  const StatusChip({super.key, required this.status});

  final VehicleStatus status;

  @override
  Widget build(BuildContext context) {
    final (Color bg, Color fg) = switch (status) {
      VehicleStatus.active => (const Color(0xFFE3F4E8), const Color(0xFF1B6B35)),
      VehicleStatus.maintenance => (const Color(0xFFFFF1D6), const Color(0xFF8A5A00)),
      VehicleStatus.inactive => (const Color(0xFFECECEC), const Color(0xFF555555)),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(12)),
      child: Text(
        status.label,
        style: TextStyle(color: fg, fontWeight: FontWeight.w600, fontSize: 12),
      ),
    );
  }
}
