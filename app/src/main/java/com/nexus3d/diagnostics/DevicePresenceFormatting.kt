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
    val today = now.atZone(zone).toLocalDate()
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    if (localTime.toLocalDate() == today.minusDays(1)) {
        return "Yesterday at ${localTime.format(timeFormatter)}"
    }
    if (localTime.toLocalDate().isAfter(today.minusDays(7))) {
        return localTime.format(DateTimeFormatter.ofPattern("EEE 'at' h:mm a", Locale.US))
    }
    return localTime.format(DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.US))
}

internal fun formatInstallationDate(
    value: String?,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val instant = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "Not reported"
    return instant.atZone(zone).format(DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US))
}

internal fun formatInstallationId(
    registeredAt: String?,
    installationId: String,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val instant = registeredAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val datePrefix = instant?.atZone(zone)
        ?.format(DateTimeFormatter.ofPattern("MMMdd/yyyy", Locale.US))
        ?: "Registration date unavailable"
    return "$datePrefix-$installationId"
}

internal fun formatActiveTime(
    value: String?,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val instant = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "Not yet active"
    return instant.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
}

internal fun formatVersionEvent(event: InstallationVersion?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (event == null) return "Not recorded"
    val date = event.occurredAt?.let { raw ->
        runCatching { Instant.parse(raw).atZone(zone).format(DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)) }.getOrNull()
    } ?: "date not reported"
    return "${event.appVersion} - $date"
}

internal fun formatHardwareBytes(value: Long?): String {
    if (value == null || value <= 0L) return "Not reported"
    val gib = value.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gib)
}
