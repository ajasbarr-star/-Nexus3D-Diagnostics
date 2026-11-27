package com.nexus3d.diagnostics

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePresenceFormattingTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val now = Instant.parse("2026-11-26T12:00:00Z")

    @Test fun formatsJustNow() {
        assertEquals("Just now", formatLastActive("2026-11-26T11:59:40Z", now, zone))
    }

    @Test fun formatsFiveMinutesAgo() {
        assertEquals("5 minutes ago", formatLastActive("2026-11-26T11:55:00Z", now, zone))
    }

    @Test fun formatsTwoHoursAgo() {
        assertEquals("2 hours ago", formatLastActive("2026-11-26T10:00:00Z", now, zone))
    }

    @Test fun formatsYesterdayWithLocalTime() {
        assertEquals("Yesterday at 6:30 PM", formatLastActive("2026-11-25T10:30:00Z", now, zone))
    }

    @Test fun formatsOlderDateAsMonthDayYear() {
        assertEquals("11-18-2026", formatLastActive("2026-11-18T12:00:00Z", now, zone))
    }


    @Test fun formatsInstallationDateAndStableInstallationId() {
        val registeredAt = "2026-11-25T16:00:00Z"
        assertEquals("11/26/2026", formatInstallationDate(registeredAt, zone))
        assertEquals(
            "Nov26/2026-123e4567-e89b-12d3-a456-426614174000",
            formatInstallationId(registeredAt, "123e4567-e89b-12d3-a456-426614174000", zone)
        )
    }

    @Test fun formatsApkActiveTimeInLocalTimezone() {
        assertEquals("9:06 AM", formatActiveTime("2026-11-26T01:06:00Z", zone))
    }

    @Test fun formatsRealVersionHistoryWithoutInventingMissingEvents() {
        val update = InstallationVersion(
            appVersion = "v1.0.02",
            versionCode = 2L,
            buildNumber = 887L,
            eventType = "installed",
            occurredAt = "2026-11-25T16:00:00Z"
        )
        assertEquals("v1.0.02 - 11/26/2026", formatVersionEvent(update, zone))
        assertEquals("Not recorded", formatVersionEvent(null, zone))
    }

    @Test fun presenceRequiresRecentForegroundHeartbeat() {
        assertTrue(isDevicePresenceOnline("online", "2026-11-26T11:59:30Z", now))
        assertFalse(isDevicePresenceOnline("online", "2026-11-26T11:57:00Z", now))
        assertFalse(isDevicePresenceOnline("offline", "2026-11-26T11:59:30Z", now))
    }
}
