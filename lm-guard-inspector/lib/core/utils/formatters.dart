/// Formatting helpers.
///
/// Implemented locally rather than via `intl` so the project resolves with a
/// single runtime dependency and behaves identically on every SDK version.
class Fmt {
  const Fmt._();

  static const List<String> _months = <String>[
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];

  static String _two(int value) => value.toString().padLeft(2, '0');

  static DateTime _startOfDay(DateTime value) =>
      DateTime(value.year, value.month, value.day);

  /// Whole days between [value] and today. Negative values are in the future.
  static int daysFromToday(DateTime value) {
    return _startOfDay(DateTime.now()).difference(_startOfDay(value)).inDays;
  }

  /// "05 Sep 2026"
  static String date(DateTime value) =>
      '${_two(value.day)} ${_months[value.month - 1]} ${value.year}';

  /// "10:45 AM"
  static String time(DateTime value) {
    final int rawHour = value.hour % 12;
    final int hour = rawHour == 0 ? 12 : rawHour;
    final String period = value.hour < 12 ? 'AM' : 'PM';
    return '$hour:${_two(value.minute)} $period';
  }

  /// "Today" · "Yesterday" · "Tomorrow" · "05 Sep 2026"
  static String day(DateTime value) {
    final int diff = daysFromToday(value);
    if (diff == 0) return 'Today';
    if (diff == 1) return 'Yesterday';
    if (diff == -1) return 'Tomorrow';
    return date(value);
  }

  /// "Today, 10:45 AM" · "05 Sep 2026, 10:45 AM"
  static String dayTime(DateTime value) => '${day(value)}, ${time(value)}';

  /// Due-date phrasing for assignment lists.
  static String due(DateTime value) {
    final int diff = daysFromToday(value);
    if (diff == 0) return 'Due today';
    if (diff == 1) return 'Overdue by 1 day';
    if (diff > 1) return 'Overdue by $diff days';
    if (diff == -1) return 'Due tomorrow';
    return 'Due ${date(value)}';
  }

  static bool isOverdue(DateTime value) => daysFromToday(value) > 0;

  /// "Good morning" / "Good afternoon" / "Good evening"
  static String greeting([DateTime? now]) {
    final int hour = (now ?? DateTime.now()).hour;
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }

  /// Up to two uppercase initials for an officer avatar.
  static String initials(String name) {
    final List<String> parts = name
        .replaceAll(RegExp(r'[^A-Za-z\s.]'), '')
        .split(RegExp(r'[\s.]+'))
        .where((String part) => part.isNotEmpty)
        .toList();
    if (parts.isEmpty) return '--';
    if (parts.length == 1) {
      final String single = parts.first;
      return (single.length == 1 ? single : single.substring(0, 2)).toUpperCase();
    }
    return '${parts.first[0]}${parts.last[0]}'.toUpperCase();
  }

  /// "3 findings" / "1 finding"
  static String plural(int count, String singular, [String? pluralForm]) {
    if (count == 1) return '$count $singular';
    return '$count ${pluralForm ?? '${singular}s'}';
  }
}
