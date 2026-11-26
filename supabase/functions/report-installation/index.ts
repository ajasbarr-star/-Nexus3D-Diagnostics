import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, sha256Hex, isUuid } from "../_shared/http.ts";
const clean = (v: unknown, max = 120) => typeof v === "string" ? v.trim().slice(0, max) : "";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return jsonResponse({ error: "server_not_configured" }, 503);
  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return jsonResponse({ error: "invalid_json" }, 400); }

  const installationId = body.installationId;
  const deviceCode = clean(body.deviceCode, 128);
  const eventType = clean(body.eventType, 20);
  if (!isUuid(installationId) || !/^[a-f0-9]{64}$/i.test(deviceCode) || !["launched","downloaded","installed","verified","failed"].includes(eventType)) return jsonResponse({ error: "invalid_installation_report" }, 400);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const tokenHash = await sha256Hex(deviceCode);
  const { data: device } = await admin.from("devices").select("id, workspace_id, status").eq("installation_id", installationId).maybeSingle();
  if (!device || device.status !== "active") return jsonResponse({ error: "device_not_active" }, 401);
  const { data: credential } = await admin.from("device_tokens").select("token_hash, revoked_at").eq("device_id", device.id).maybeSingle();
  if (!credential || credential.revoked_at || credential.token_hash !== tokenHash) return jsonResponse({ error: "invalid_device_credential" }, 401);

  const version = clean(body.appVersion, 40) || "unknown";
  const buildNumber = Number.isSafeInteger(body.buildNumber) ? Number(body.buildNumber) : null;
  const versionCode = Number.isSafeInteger(body.versionCode) ? Number(body.versionCode) : null;
  const releaseId = isUuid(body.releaseId) ? body.releaseId : null;
  const info = body.deviceInfo && typeof body.deviceInfo === "object" ? body.deviceInfo as Record<string, unknown> : {};
  const now = new Date().toISOString();
  const { error: updateError } = await admin.from("devices").update({
    app_version: version, app_build_number: buildNumber, app_version_code: versionCode,
    manufacturer: clean(info.manufacturer, 80), model: clean(info.model, 100),
    android_version: clean(info.androidVersion, 40), last_seen_at: now
  }).eq("id", device.id);
  if (updateError) return jsonResponse({ error: "device_update_failed" }, 500);

  if (releaseId) {
    const status = eventType === "failed" ? "failed" : eventType === "verified" ? "verified" : eventType === "installed" ? "installed" : eventType === "downloaded" ? "downloaded" : "assigned";
    await admin.from("release_assignments").upsert({
      workspace_id: device.workspace_id, release_id: releaseId, device_id: device.id,
      download_status: status, installed_version: version, installed_version_code: versionCode,
      installed_build_number: buildNumber, last_reported_at: now,
      failure_reason: eventType === "failed" ? clean(body.failureReason, 500) : null
    }, { onConflict: "release_id,device_id" });
  }

  const { error: eventError } = await admin.from("installation_events").insert({
    workspace_id: device.workspace_id, device_id: device.id, release_id: releaseId,
    event_type: eventType, app_version: version, version_code: versionCode,
    build_number: buildNumber, details: clean(body.details, 1000), occurred_at: now
  });
  if (eventError) return jsonResponse({ error: "installation_event_write_failed" }, 500);
  return jsonResponse({ accepted: true, eventType, verified: eventType === "verified", receivedAt: now });
});
