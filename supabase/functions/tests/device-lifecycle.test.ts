import { assertEquals, assertThrows } from "https://deno.land/std@0.224.0/assert/mod.ts";
import {
  isEligibleForStaleArchive,
  validateInstallationReactivation,
} from "../_shared/device-lifecycle.ts";

Deno.test("archives active device only when both activity timestamps exceed 30 days", () => {
  const now = new Date("2026-11-26T12:00:00.000Z");
  assertEquals(isEligibleForStaleArchive("active", "2026-10-26T11:59:59.000Z", null, now), true);
  assertEquals(isEligibleForStaleArchive("active", "2026-10-01T00:00:00.000Z", "2026-11-01T00:00:00.000Z", now), false);
  assertEquals(isEligibleForStaleArchive("active", null, null, now), true);
});

Deno.test("does not archive devices that are not active", () => {
  const now = new Date("2026-11-26T12:00:00.000Z");
  assertEquals(isEligibleForStaleArchive("archived", null, null, now), false);
  assertEquals(isEligibleForStaleArchive("revoked", null, null, now), false);
});

Deno.test("rejects invalid inactivity thresholds", () => {
  const now = new Date("2026-11-26T12:00:00.000Z");
  assertThrows(() => isEligibleForStaleArchive("active", null, null, now, 6), RangeError);
  assertThrows(() => isEligibleForStaleArchive("active", null, null, now, 366), RangeError);
});

Deno.test("allows reactivation with the original valid credential", () => {
  assertEquals(validateInstallationReactivation("archived", {
    tokenHash: "abc123",
    revokedAt: null,
  }, "abc123"), "allowed");
});

Deno.test("rejects revoked installation even if credential matches", () => {
  assertEquals(validateInstallationReactivation("revoked", {
    tokenHash: "abc123",
    revokedAt: null,
  }, "abc123"), "installation_revoked");
});

Deno.test("rejects missing, revoked, or mismatched credentials", () => {
  assertEquals(validateInstallationReactivation("archived", null, "abc123"), "identity_conflict");
  assertEquals(validateInstallationReactivation("archived", {
    tokenHash: "abc123",
    revokedAt: "2026-11-01T00:00:00.000Z",
  }, "abc123"), "identity_conflict");
  assertEquals(validateInstallationReactivation("archived", {
    tokenHash: "different",
    revokedAt: null,
  }, "abc123"), "identity_conflict");
});
