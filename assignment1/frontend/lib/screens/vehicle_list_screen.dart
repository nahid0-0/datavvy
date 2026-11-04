import 'dart:async';

import 'package:flutter/material.dart';

import '../models/vehicle.dart';
import '../services/api_exception.dart';
import '../services/vehicle_api.dart';
import '../widgets/status_chip.dart';
import 'vehicle_form_screen.dart';

enum _SortOption {
  newest('Newest first', 'createdAt,desc'),
  oldest('Oldest first', 'createdAt,asc'),
  numberAsc('Vehicle number A-Z', 'vehicleNumber,asc'),
  numberDesc('Vehicle number Z-A', 'vehicleNumber,desc'),
  yearDesc('Year (newest)', 'manufactureYear,desc'),
  yearAsc('Year (oldest)', 'manufactureYear,asc');

  const _SortOption(this.label, this.apiValue);

  final String label;
  final String apiValue;
}

class VehicleListScreen extends StatefulWidget {
  const VehicleListScreen({super.key, required this.api});

  final VehicleApi api;

  @override
  State<VehicleListScreen> createState() => _VehicleListScreenState();
}

class _VehicleListScreenState extends State<VehicleListScreen> {
  static const _pageSizes = [10, 20, 50];
  static const _searchDebounce = Duration(milliseconds: 400);

  final _searchController = TextEditingController();
  Timer? _debounce;

  String _search = '';
  VehicleType? _typeFilter;
  VehicleStatus? _statusFilter;
  _SortOption _sort = _SortOption.newest;
  int _page = 0;
  int _pageSize = _pageSizes.first;

  PageResult<Vehicle>? _result;
  ApiException? _error;
  bool _loading = false;

  /// Incremented per request so slow, out-of-order responses never overwrite newer ones.
  int _requestSeq = 0;

  /// Ids currently being deleted, to prevent double-deletes and show progress.
  final Set<int> _deleting = {};

  bool get _hasFilters => _search.isNotEmpty || _typeFilter != null || _statusFilter != null;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final seq = ++_requestSeq;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await widget.api.list(
        search: _search,
        type: _typeFilter,
        status: _statusFilter,
        page: _page,
        size: _pageSize,
        sort: _sort.apiValue,
      );
      if (!mounted || seq != _requestSeq) return;

      // The current page can disappear (e.g. its last item was deleted elsewhere).
      if (result.content.isEmpty && _page > 0 && result.totalPages > 0) {
        _page = result.totalPages - 1;
        unawaited(_load());
        return;
      }
      setState(() {
        _result = result;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted || seq != _requestSeq) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(_searchDebounce, () {
      final trimmed = value.trim();
      if (trimmed == _search) return;
      _search = trimmed;
      _page = 0;
      _load();
    });
  }

  void _clearFilters() {
    _debounce?.cancel();
    _searchController.clear();
    _search = '';
    _typeFilter = null;
    _statusFilter = null;
    _page = 0;
    _load();
  }

  Future<void> _openForm({Vehicle? vehicle}) async {
    final saved = await Navigator.of(context).push<Vehicle>(
      MaterialPageRoute(
        builder: (_) => VehicleFormScreen(api: widget.api, vehicleId: vehicle?.id),
      ),
    );
    if (!mounted) return;
    if (saved != null) {
      _showSnack(
        vehicle == null
            ? 'Vehicle ${saved.vehicleNumber} registered'
            : 'Vehicle ${saved.vehicleNumber} updated',
      );
      if (vehicle == null) _page = 0;
    }
    // Always refresh: data may have changed (or been deleted) while the form was open.
    _load();
  }

  Future<void> _confirmDelete(Vehicle vehicle) async {
    if (_deleting.contains(vehicle.id)) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.delete_outline),
        title: const Text('Delete vehicle?'),
        content: Text(
          'Vehicle ${vehicle.vehicleNumber} (${vehicle.make} ${vehicle.model}) will be '
          'permanently removed. This cannot be undone.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Theme.of(ctx).colorScheme.error),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _deleting.add(vehicle.id));
    try {
      await widget.api.delete(vehicle.id);
      _showSnack('Vehicle ${vehicle.vehicleNumber} deleted');
    } on ApiException catch (e) {
      if (e.isNotFound) {
        _showSnack('Vehicle ${vehicle.vehicleNumber} was already deleted');
      } else {
        _showSnack(e.message, isError: true);
      }
    } finally {
      if (mounted) {
        setState(() => _deleting.remove(vehicle.id));
        // If that was the last row on this page, step back a page.
        if ((_result?.content.length ?? 0) <= 1 && _page > 0) _page--;
        _load();
      }
    }
  }

  void _showSnack(String message, {bool isError = false}) {
    final messenger = ScaffoldMessenger.of(context);
    messenger.hideCurrentSnackBar();
    messenger.showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? Theme.of(context).colorScheme.error : null,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Stark Group VMS'),
        actions: [
          IconButton(tooltip: 'Refresh', onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh)),
          const SizedBox(width: 8),
          // In the app bar rather than a FAB, so it never covers the table or the pager.
          FilledButton.icon(
            onPressed: () => _openForm(),
            icon: const Icon(Icons.add),
            label: const Text('Register vehicle'),
          ),
          const SizedBox(width: 16),
        ],
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1200),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildToolbar(),
              if (_loading) const LinearProgressIndicator() else const SizedBox(height: 4),
              Expanded(child: _buildBody()),
              if (_result != null && _result!.totalElements > 0) _buildPager(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildToolbar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Wrap(
        spacing: 12,
        runSpacing: 12,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          SizedBox(
            width: 320,
            child: TextField(
              controller: _searchController,
              onChanged: _onSearchChanged,
              textInputAction: TextInputAction.search,
              maxLength: 100,
              decoration: InputDecoration(
                counterText: '',
                prefixIcon: const Icon(Icons.search),
                hintText: 'Search number, make, model, owner',
                border: const OutlineInputBorder(),
                isDense: true,
                suffixIcon: ValueListenableBuilder(
                  valueListenable: _searchController,
                  builder: (_, value, _) => value.text.isEmpty
                      ? const SizedBox.shrink()
                      : IconButton(
                          tooltip: 'Clear search',
                          icon: const Icon(Icons.clear),
                          onPressed: () {
                            _searchController.clear();
                            _onSearchChanged('');
                          },
                        ),
                ),
              ),
            ),
          ),
          _FilterDropdown<VehicleType>(
            label: 'Type',
            allLabel: 'All types',
            value: _typeFilter,
            items: VehicleType.values,
            itemLabel: (t) => t.label,
            onChanged: (v) {
              _typeFilter = v;
              _page = 0;
              _load();
            },
          ),
          _FilterDropdown<VehicleStatus>(
            label: 'Status',
            allLabel: 'All statuses',
            value: _statusFilter,
            items: VehicleStatus.values,
            itemLabel: (s) => s.label,
            onChanged: (v) {
              _statusFilter = v;
              _page = 0;
              _load();
            },
          ),
          SizedBox(
            width: 200,
            child: DropdownButtonFormField<_SortOption>(
              initialValue: _sort,
              isDense: true,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Sort by',
                border: OutlineInputBorder(),
                isDense: true,
              ),
              items: [for (final s in _SortOption.values) DropdownMenuItem(value: s, child: Text(s.label))],
              onChanged: (v) {
                if (v == null) return;
                _sort = v;
                _page = 0;
                _load();
              },
            ),
          ),
          if (_hasFilters)
            TextButton.icon(
              onPressed: _clearFilters,
              icon: const Icon(Icons.filter_alt_off),
              label: const Text('Clear filters'),
            ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    final result = _result;

    if (_error != null && result == null) {
      return _MessageView(
        icon: _error!.isNetworkError ? Icons.cloud_off : Icons.error_outline,
        title: 'Could not load vehicles',
        message: _error!.message,
        action: FilledButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: const Text('Retry'),
        ),
      );
    }

    if (result == null) {
      return const Center(child: CircularProgressIndicator());
    }

    if (result.content.isEmpty) {
      return _hasFilters
          ? _MessageView(
              icon: Icons.search_off,
              title: 'No matching vehicles',
              message: 'Try a different search or clear the filters.',
              action: OutlinedButton(onPressed: _clearFilters, child: const Text('Clear filters')),
            )
          : _MessageView(
              icon: Icons.directions_car_outlined,
              title: 'No vehicles registered yet',
              message: 'Register your first vehicle to get started.',
              action: FilledButton.icon(
                onPressed: () => _openForm(),
                icon: const Icon(Icons.add),
                label: const Text('Register vehicle'),
              ),
            );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // A refresh failed but we still have the previous data: keep showing it with a banner.
        if (_error != null)
          MaterialBanner(
            content: Text('Showing previous results. ${_error!.message}'),
            leading: const Icon(Icons.warning_amber),
            actions: [TextButton(onPressed: _load, child: const Text('Retry'))],
          ),
        Expanded(
          child: LayoutBuilder(
            builder: (context, constraints) =>
                constraints.maxWidth >= 840 ? _buildTable(result.content) : _buildCards(result.content),
          ),
        ),
      ],
    );
  }

  Widget _buildTable(List<Vehicle> vehicles) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: LayoutBuilder(
          builder: (context, constraints) => SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: ConstrainedBox(
              constraints: BoxConstraints(minWidth: constraints.maxWidth),
              child: DataTable(
                headingTextStyle: Theme.of(context).textTheme.titleSmall,
                columns: const [
                  DataColumn(label: Text('Vehicle No.')),
                  DataColumn(label: Text('Make / Model')),
                  DataColumn(label: Text('Type')),
                  DataColumn(label: Text('Year'), numeric: true),
                  DataColumn(label: Text('Owner')),
                  DataColumn(label: Text('Status')),
                  DataColumn(label: Text('Actions')),
                ],
                rows: [
                  for (final v in vehicles)
                    DataRow(
                      cells: [
                        DataCell(Text(v.vehicleNumber, style: const TextStyle(fontWeight: FontWeight.w600))),
                        DataCell(
                          Text(
                            [v.make, v.model, if (v.color != null) '(${v.color})'].join(' '),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        DataCell(Text(v.type.label)),
                        DataCell(Text('${v.manufactureYear}')),
                        DataCell(Text(v.ownerName ?? '—', overflow: TextOverflow.ellipsis)),
                        DataCell(StatusChip(status: v.status)),
                        DataCell(
                          _RowActions(
                            deleting: _deleting.contains(v.id),
                            onEdit: () => _openForm(vehicle: v),
                            onDelete: () => _confirmDelete(v),
                          ),
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCards(List<Vehicle> vehicles) {
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      itemCount: vehicles.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, i) {
        final v = vehicles[i];
        return Card(
          child: ListTile(
            onTap: () => _openForm(vehicle: v),
            title: Text(v.vehicleNumber, style: const TextStyle(fontWeight: FontWeight.w600)),
            subtitle: Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Wrap(
                spacing: 8,
                runSpacing: 4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Text('${v.make} ${v.model} · ${v.type.label} · ${v.manufactureYear}'),
                  if (v.ownerName != null) Text('Owner: ${v.ownerName}'),
                  StatusChip(status: v.status),
                ],
              ),
            ),
            trailing: _RowActions(
              deleting: _deleting.contains(v.id),
              onEdit: () => _openForm(vehicle: v),
              onDelete: () => _confirmDelete(v),
            ),
          ),
        );
      },
    );
  }

  Widget _buildPager() {
    final r = _result!;
    final from = r.page * r.size + 1;
    final to = from + r.content.length - 1;
    return Material(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Wrap(
          alignment: WrapAlignment.end,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 16,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Rows per page:'),
                const SizedBox(width: 8),
                DropdownButton<int>(
                  value: _pageSize,
                  underline: const SizedBox.shrink(),
                  items: [for (final s in _pageSizes) DropdownMenuItem(value: s, child: Text('$s'))],
                  onChanged: (v) {
                    if (v == null || v == _pageSize) return;
                    _pageSize = v;
                    _page = 0;
                    _load();
                  },
                ),
              ],
            ),
            Text(r.content.isEmpty ? '0 of ${r.totalElements}' : '$from–$to of ${r.totalElements}'),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  tooltip: 'Previous page',
                  onPressed: r.isFirst || _loading
                      ? null
                      : () {
                          _page--;
                          _load();
                        },
                  icon: const Icon(Icons.chevron_left),
                ),
                Text('Page ${r.page + 1} of ${r.totalPages == 0 ? 1 : r.totalPages}'),
                IconButton(
                  tooltip: 'Next page',
                  onPressed: r.isLast || _loading
                      ? null
                      : () {
                          _page++;
                          _load();
                        },
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _RowActions extends StatelessWidget {
  const _RowActions({required this.deleting, required this.onEdit, required this.onDelete});

  final bool deleting;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: 'Edit',
          onPressed: deleting ? null : onEdit,
          icon: const Icon(Icons.edit_outlined),
        ),
        deleting
            ? const Padding(
                padding: EdgeInsets.all(12),
                child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
              )
            : IconButton(
                tooltip: 'Delete',
                onPressed: onDelete,
                icon: Icon(Icons.delete_outline, color: Theme.of(context).colorScheme.error),
              ),
      ],
    );
  }
}

class _FilterDropdown<T> extends StatelessWidget {
  const _FilterDropdown({
    required this.label,
    required this.allLabel,
    required this.value,
    required this.items,
    required this.itemLabel,
    required this.onChanged,
  });

  final String label;
  final String allLabel;
  final T? value;
  final List<T> items;
  final String Function(T) itemLabel;
  final ValueChanged<T?> onChanged;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 170,
      child: DropdownButtonFormField<T?>(
        // Key on value so "Clear filters" resets the field's internal state.
        key: ValueKey('$label-$value'),
        initialValue: value,
        isDense: true,
        isExpanded: true,
        decoration: InputDecoration(labelText: label, border: const OutlineInputBorder(), isDense: true),
        items: [
          DropdownMenuItem<T?>(value: null, child: Text(allLabel)),
          for (final item in items) DropdownMenuItem<T?>(value: item, child: Text(itemLabel(item))),
        ],
        onChanged: onChanged,
      ),
    );
  }
}

class _MessageView extends StatelessWidget {
  const _MessageView({required this.icon, required this.title, required this.message, this.action});

  final IconData icon;
  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 56, color: theme.colorScheme.outline),
            const SizedBox(height: 16),
            Text(title, style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(message, textAlign: TextAlign.center, style: theme.textTheme.bodyMedium),
            if (action != null) ...[const SizedBox(height: 16), action!],
          ],
        ),
      ),
    );
  }
}
