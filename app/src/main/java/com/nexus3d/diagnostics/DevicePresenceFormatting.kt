package com.nexus3d.diagnostics

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun isDevicePresenceOnline(
    state: String,
    lastActiveAt: String?,
    now: Instant = Instant.now()
): Boolean {
    if (!state.equals("online", ignoreCase = true) || lastActiveAt.isNullOrBlank()) return false
    val activeAt = runCatching { Instant.parse(lastActiveAt) }.getOrNull() ?: return false
    val ageSeconds = Duration.between(activeAt, now).seconds
    return ageSeconds in -60..120
}

internal fun formatLastActive(
    value: String?,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (value.isNullOrBlank()) return "Not yet active"
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return "Not available"
    val seconds = Duration.between(instant, now).seconds.coerceAtLeast(0)
    if (seconds < 60) return "Just now"
    if (seconds < 3_600) {
        val minutes = seconds / 60
        return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
    }
    if (seconds < 86_400) {
        val hours = seconds / 3_600
        return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    }

    val localTime = instant.atZone(zone)
    val today = LocalDate.now(now.atZone(zone))
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    if (localTime.toLocalDate() == today.minusDays(1)) {
        return "Yesterday at ${localTime.format(timeFormatter)}"
    }
    if (localTime.toLocalDate().isAfter(today.minusDays(7))) {
        return localTime.format(DateTimeFormatter.ofPattern("EEE 'at' h:mm a", Locale.US))
    }
    return localTime.format(DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.US))
}
