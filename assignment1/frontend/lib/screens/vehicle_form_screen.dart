import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/vehicle.dart';
import '../services/api_exception.dart';
import '../services/vehicle_api.dart';
import '../utils/vehicle_validators.dart';

enum _AvailabilityState { idle, checking, available, taken }

/// Register (when [vehicleId] is null) or edit a vehicle.
/// Pops with the saved [Vehicle] on success.
class VehicleFormScreen extends StatefulWidget {
  const VehicleFormScreen({super.key, required this.api, this.vehicleId});

  final VehicleApi api;
  final int? vehicleId;

  bool get isEdit => vehicleId != null;

  @override
  State<VehicleFormScreen> createState() => _VehicleFormScreenState();
}

class _VehicleFormScreenState extends State<VehicleFormScreen> {
  static const _availabilityDebounce = Duration(milliseconds: 500);

  final _formKey = GlobalKey<FormState>();
  final _numberFieldKey = GlobalKey<FormFieldState<String>>();
  final _number = TextEditingController();
  final _make = TextEditingController();
  final _model = TextEditingController();
  final _year = TextEditingController();
  final _color = TextEditingController();
  final _owner = TextEditingController();
  VehicleType? _type;
  VehicleStatus _status = VehicleStatus.active;

  /// The vehicle as last loaded from the server (edit mode only).
  Vehicle? _original;
  bool _loading = false;
  ApiException? _loadError;
  bool _saving = false;
  String? _formError;

  /// Field errors returned by the server; cleared as soon as the user edits that field.
  final Map<String, String> _serverErrors = {};

  Timer? _availabilityTimer;
  _AvailabilityState _availability = _AvailabilityState.idle;
  String? _lastCheckedKey;

  /// Snapshot of field values at load time, used for unsaved-changes detection.
  String _initialSnapshot = '';

  @override
  void initState() {
    super.initState();
    _number.addListener(_onNumberChanged);
    if (widget.isEdit) {
      _loadVehicle();
    } else {
      _initialSnapshot = _snapshot();
    }
  }

  @override
  void dispose() {
    _availabilityTimer?.cancel();
    for (final c in [_number, _make, _model, _year, _color, _owner]) {
      c.dispose();
    }
    super.dispose();
  }

  // ------------------------------------------------------------------ loading

  Future<void> _loadVehicle() async {
    setState(() {
      _loading = true;
      _loadError = null;
    });
    try {
      final vehicle = await widget.api.get(widget.vehicleId!);
      if (!mounted) return;
      _populate(vehicle);
      setState(() => _loading = false);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loadError = e;
        _loading = false;
      });
    }
  }

  void _populate(Vehicle v) {
    _original = v;
    _number.text = v.vehicleNumber;
    _make.text = v.make;
    _model.text = v.model;
    _year.text = '${v.manufactureYear}';
    _color.text = v.color ?? '';
    _owner.text = v.ownerName ?? '';
    _type = v.type;
    _status = v.status;
    _serverErrors.clear();
    _formError = null;
    _availability = _AvailabilityState.idle;
    _lastCheckedKey = VehicleValidators.normalizedKey(v.vehicleNumber);
    _initialSnapshot = _snapshot();
  }

  String _snapshot() => [
    _number.text.trim(),
    _make.text.trim(),
    _model.text.trim(),
    _year.text.trim(),
    _color.text.trim(),
    _owner.text.trim(),
    _type?.name,
    _status.name,
  ].join('\u0000');

  bool get _isDirty => _snapshot() != _initialSnapshot;

  // ------------------------------------------------------------------ duplicate check

  void _onNumberChanged() {
    _availabilityTimer?.cancel();
    final value = _number.text;
    final key = VehicleValidators.normalizedKey(value);

    // Unchanged from the saved value, or not valid yet: nothing to check.
    final originalKey = _original == null ? null : VehicleValidators.normalizedKey(_original!.vehicleNumber);
    if (VehicleValidators.vehicleNumber(value) != null || key == originalKey) {
      if (_availability != _AvailabilityState.idle) {
        setState(() => _availability = _AvailabilityState.idle);
      }
      _lastCheckedKey = key == originalKey ? key : null;
      return;
    }
    if (key == _lastCheckedKey) return;

    setState(() => _availability = _AvailabilityState.checking);
    _availabilityTimer = Timer(_availabilityDebounce, () => _checkAvailability(value, key));
  }

  Future<void> _checkAvailability(String value, String key) async {
    try {
      final available = await widget.api.isNumberAvailable(value, excludeId: widget.vehicleId);
      // Ignore the answer if the user has typed something else meanwhile.
      if (!mounted || VehicleValidators.normalizedKey(_number.text) != key) return;
      setState(() {
        _lastCheckedKey = key;
        _availability = available ? _AvailabilityState.available : _AvailabilityState.taken;
      });
      _numberFieldKey.currentState?.validate(); // only this field, not the whole form
    } on ApiException {
      // Best effort only: the server re-checks on save.
      if (!mounted) return;
      setState(() => _availability = _AvailabilityState.idle);
    }
  }

  // ------------------------------------------------------------------ saving

  Future<void> _submit() async {
    if (_saving) return; // double-submit guard
    setState(() {
      _serverErrors.clear();
      _formError = null;
    });
    // Always say why nothing happened: a silent no-op on "Register" is the worst UX.
    if (!(_formKey.currentState?.validate() ?? false)) {
      setState(() => _formError = 'Please fix the highlighted fields below.');
      return;
    }
    if (_availability == _AvailabilityState.taken) {
      setState(() => _formError = 'This vehicle number is already registered. Use a different number.');
      return;
    }

    final input = VehicleInput(
      vehicleNumber: _number.text,
      make: _make.text,
      model: _model.text,
      type: _type!,
      manufactureYear: int.parse(_year.text.trim()),
      color: _color.text,
      ownerName: _owner.text,
      status: _status,
      version: _original?.version,
    );

    setState(() => _saving = true);
    try {
      final saved = widget.isEdit
          ? await widget.api.update(widget.vehicleId!, input)
          : await widget.api.create(input);
      if (!mounted) return;
      _initialSnapshot = _snapshot(); // so the pop below is not treated as discarding changes
      Navigator.of(context).pop(saved);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      await _handleSaveError(e);
    }
  }

  Future<void> _handleSaveError(ApiException e) async {
    if (e.isVersionConflict) {
      final reload = await showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.sync_problem),
          title: const Text('Vehicle changed by someone else'),
          content: const Text(
            'This vehicle was updated after you opened it. Reload the latest version? '
            'Your unsaved changes will be lost.',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Keep editing')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Reload')),
          ],
        ),
      );
      if (reload == true && mounted) await _loadVehicle();
      return;
    }

    if (e.isNotFound && widget.isEdit) {
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Vehicle no longer exists'),
          content: const Text('It may have been deleted by another user.'),
          actions: [FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('OK'))],
        ),
      );
      if (mounted) {
        _initialSnapshot = _snapshot();
        Navigator.of(context).pop();
      }
      return;
    }

    setState(() {
      if (e.isDuplicateNumber) {
        _availability = _AvailabilityState.taken;
        _lastCheckedKey = VehicleValidators.normalizedKey(_number.text);
      }
      _serverErrors.addAll(e.fieldErrors);
      // Anything the server rejected that has no matching field goes in the form-level banner.
      final unmatched = e.fieldErrors.keys.where((k) => !_knownFields.contains(k));
      _formError = (e.fieldErrors.isEmpty || unmatched.isNotEmpty) && !e.isDuplicateNumber ? e.message : null;
    });
    _formKey.currentState?.validate();
  }

  static const _knownFields = {
    'vehicleNumber',
    'make',
    'model',
    'type',
    'manufactureYear',
    'color',
    'ownerName',
    'status',
  };

  // ------------------------------------------------------------------ navigation guard

  Future<bool> _confirmDiscard() async {
    if (!_isDirty) return true;
    final discard = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Discard changes?'),
        content: const Text('You have unsaved changes. Leave without saving?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Keep editing')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Discard')),
        ],
      ),
    );
    return discard == true;
  }

  // ------------------------------------------------------------------ UI

  String? Function(String?) _withServerError(String field, String? Function(String?) validator) {
    return (value) => validator(value) ?? _serverErrors[field];
  }

  void _clearServerError(String field) {
    if (_serverErrors.containsKey(field) || _formError != null) {
      setState(() {
        _serverErrors.remove(field);
        _formError = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope<Object?>(
      canPop: !_isDirty && !_saving,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop || _saving) return;
        final navigator = Navigator.of(context);
        if (await _confirmDiscard() && mounted) {
          _initialSnapshot = _snapshot();
          navigator.pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(title: Text(widget.isEdit ? 'Edit vehicle' : 'Register vehicle')),
        body: _buildBody(),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) return const Center(child: CircularProgressIndicator());

    if (_loadError != null) {
      final notFound = _loadError!.isNotFound;
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(notFound ? Icons.search_off : Icons.cloud_off, size: 56),
              const SizedBox(height: 16),
              Text(
                notFound ? 'This vehicle no longer exists.' : _loadError!.message,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              if (notFound)
                FilledButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Back to list'))
              else
                FilledButton.icon(
                  onPressed: _loadVehicle,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Retry'),
                ),
            ],
          ),
        ),
      );
    }

    return Align(
      alignment: Alignment.topCenter,
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Form(
                key: _formKey,
                onChanged: () => setState(() {}), // refresh dirty state for PopScope
                child: AbsorbPointer(
                  absorbing: _saving,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (_formError != null) ...[
                        _ErrorBanner(message: _formError!),
                        const SizedBox(height: 16),
                      ],
                      _buildNumberField(),
                      const SizedBox(height: 16),
                      _row([
                        TextFormField(
                          autovalidateMode: AutovalidateMode.onUserInteraction,
                          controller: _make,
                          decoration: _decoration('Make *', hint: 'e.g. Toyota'),
                          maxLength: 50,
                          textInputAction: TextInputAction.next,
                          onChanged: (_) => _clearServerError('make'),
                          validator: _withServerError(
                            'make',
                            VehicleValidators.requiredText('Make', max: 50),
                          ),
                        ),
                        TextFormField(
                          autovalidateMode: AutovalidateMode.onUserInteraction,
                          controller: _model,
                          decoration: _decoration('Model *', hint: 'e.g. Corolla'),
                          maxLength: 50,
                          textInputAction: TextInputAction.next,
                          onChanged: (_) => _clearServerError('model'),
                          validator: _withServerError(
                            'model',
                            VehicleValidators.requiredText('Model', max: 50),
                          ),
                        ),
                      ]),
                      const SizedBox(height: 8),
                      _row([
                        DropdownButtonFormField<VehicleType>(
                          autovalidateMode: AutovalidateMode.onUserInteraction,
                          key: ValueKey('type-${_original?.version}'),
                          initialValue: _type,
                          isExpanded: true,
                          decoration: _decoration('Type *'),
                          items: [
                            for (final t in VehicleType.values)
                              DropdownMenuItem(value: t, child: Text(t.label)),
                          ],
                          onChanged: (v) {
                            _clearServerError('type');
                            setState(() => _type = v);
                          },
                          validator: (v) => v == null ? 'Type is required' : _serverErrors['type'],
                        ),
                        TextFormField(
                          autovalidateMode: AutovalidateMode.onUserInteraction,
                          controller: _year,
                          decoration: _decoration('Manufacture year *', hint: 'e.g. ${DateTime.now().year}'),
                          keyboardType: TextInputType.number,
                          inputFormatters: [
                            FilteringTextInputFormatter.digitsOnly,
                            LengthLimitingTextInputFormatter(4),
                          ],
                          textInputAction: TextInputAction.next,
                          onChanged: (_) => _clearServerError('manufactureYear'),
                          validator: _withServerError('manufactureYear', VehicleValidators.year),
                        ),
                      ]),
                      const SizedBox(height: 16),
                      _row([
                        TextFormField(
                          autovalidateMode: AutovalidateMode.onUserInteraction,
                          controller: _color,
                          decoration: _decoration('Color', hint: 'Optional'),
                          maxLength: 30,
                          textInputAction: TextInputAction.next,
                          onChanged: (_) => _clearServerError('color'),
                          validator: _withServerError(
                            'color',
                            VehicleValidators.optionalText('Color', max: 30),
                          ),
                        ),
                        DropdownButtonFormField<VehicleStatus>(
                          autovalidateMode: AutovalidateMode.onUserInteraction,
                          key: ValueKey('status-${_original?.version}'),
                          initialValue: _status,
                          isExpanded: true,
                          decoration: _decoration('Status'),
                          items: [
                            for (final s in VehicleStatus.values)
                              DropdownMenuItem(value: s, child: Text(s.label)),
                          ],
                          onChanged: (v) => setState(() => _status = v ?? VehicleStatus.active),
                          validator: (_) => _serverErrors['status'],
                        ),
                      ]),
                      const SizedBox(height: 8),
                      TextFormField(
                        autovalidateMode: AutovalidateMode.onUserInteraction,
                        controller: _owner,
                        decoration: _decoration('Owner name', hint: 'Optional'),
                        maxLength: 100,
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _submit(),
                        onChanged: (_) => _clearServerError('ownerName'),
                        validator: _withServerError(
                          'ownerName',
                          VehicleValidators.optionalText('Owner name', max: 100),
                        ),
                      ),
                      const SizedBox(height: 24),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          TextButton(
                            onPressed: _saving ? null : () => Navigator.of(context).maybePop(),
                            child: const Text('Cancel'),
                          ),
                          const SizedBox(width: 12),
                          FilledButton.icon(
                            onPressed: _saving ? null : _submit,
                            icon: _saving
                                ? const SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                  )
                                : const Icon(Icons.save_outlined),
                            label: Text(widget.isEdit ? 'Save changes' : 'Register'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNumberField() {
    final Widget? suffix = switch (_availability) {
      _AvailabilityState.checking => const Padding(
        padding: EdgeInsets.all(12),
        child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
      ),
      _AvailabilityState.available => const Tooltip(
        message: 'Available',
        child: Icon(Icons.check_circle, color: Colors.green),
      ),
      _AvailabilityState.taken => Tooltip(
        message: 'Already registered',
        child: Icon(Icons.error, color: Theme.of(context).colorScheme.error),
      ),
      _AvailabilityState.idle => null,
    };

    return TextFormField(
      controller: _number,
      autofocus: !widget.isEdit,
      maxLength: 32,
      textCapitalization: TextCapitalization.characters,
      inputFormatters: [_UpperCaseFormatter()],
      textInputAction: TextInputAction.next,
      decoration: _decoration(
        'Vehicle number *',
        hint: 'e.g. DHA-METRO-GA-12-3456',
        helper: 'Case, spaces and hyphens are ignored when checking for duplicates',
      ).copyWith(suffixIcon: suffix),
      onChanged: (_) => _clearServerError('vehicleNumber'),
      validator: (value) {
        final clientError = VehicleValidators.vehicleNumber(value);
        if (clientError != null) return clientError;
        if (_availability == _AvailabilityState.taken) {
          return 'This vehicle number is already registered';
        }
        return _serverErrors['vehicleNumber'];
      },
    );
  }

  InputDecoration _decoration(String label, {String? hint, String? helper}) => InputDecoration(
    labelText: label,
    hintText: hint,
    // ' ' reserves the error line so fields (and the Register button) don't jump when errors appear.
    helperText: helper ?? ' ',
    helperMaxLines: 2,
    errorMaxLines: 3,
    border: const OutlineInputBorder(),
  );

  /// Two fields side by side on wide screens, stacked on narrow ones.
  Widget _row(List<Widget> children) => LayoutBuilder(
    builder: (context, constraints) {
      if (constraints.maxWidth < 520) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < children.length; i++) ...[if (i > 0) const SizedBox(height: 16), children[i]],
          ],
        );
      }
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) const SizedBox(width: 16),
            Expanded(child: children[i]),
          ],
        ],
      );
    },
  );
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: scheme.errorContainer, borderRadius: BorderRadius.circular(8)),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: scheme.onErrorContainer),
          const SizedBox(width: 12),
          Expanded(
            child: Text(message, style: TextStyle(color: scheme.onErrorContainer)),
          ),
        ],
      ),
    );
  }
}

class _UpperCaseFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) =>
      newValue.copyWith(text: newValue.text.toUpperCase());
}
