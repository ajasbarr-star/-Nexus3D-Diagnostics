export type ExistingCredential = {
  tokenHash: string;
  revokedAt: string | null;
};

/**
 * Existing installation may be reactivated only when it is not explicitly
 * revoked and the presented credential is present, unrevoked, and matches.
 */
export function validateInstallationReactivation(
  deviceStatus: string,
  credential: ExistingCredential | null,
  presentedTokenHash: string,
): "allowed" | "installation_revoked" | "identity_conflict" {
  if (deviceStatus === "revoked") return "installation_revoked";
  if (!credential || credential.revokedAt || credential.tokenHash !== presentedTokenHash) {
    return "identity_conflict";
  }
  return "allowed";
}

/**
 * Archive eligibility mirrors the SQL function: use the newest activity
 * timestamp, and archive only active devices strictly older than the cutoff.
 */
export function isEligibleForStaleArchive(
  status: string,
  lastSeenAt: string | null,
  lastActiveAt: string | null,
  now: Date,
  inactiveDays = 30,
): boolean {
  if (!Number.isInteger(inactiveDays) || inactiveDays < 7 || inactiveDays > 365) {
    throw new RangeError("inactiveDays must be an integer between 7 and 365");
  }
  if (status !== "active") return false;
  const timestamps = [lastSeenAt, lastActiveAt]
    .filter((value): value is string => value !== null)
    .map((value) => Date.parse(value))
    .filter((value) => Number.isFinite(value));
  const newestActivity = timestamps.length ? Math.max(...timestamps) : Number.NEGATIVE_INFINITY;
  const cutoff = now.getTime() - inactiveDays * 24 * 60 * 60 * 1000;
  return newestActivity < cutoff;
}
