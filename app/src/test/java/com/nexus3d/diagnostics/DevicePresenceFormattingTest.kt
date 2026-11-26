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

    @Test fun presenceRequiresRecentForegroundHeartbeat() {
        assertTrue(isDevicePresenceOnline("online", "2026-11-26T11:59:30Z", now))
        assertFalse(isDevicePresenceOnline("online", "2026-11-26T11:57:00Z", now))
        assertFalse(isDevicePresenceOnline("offline", "2026-11-26T11:59:30Z", now))
    }
}
