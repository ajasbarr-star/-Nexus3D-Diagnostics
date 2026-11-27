import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, sha256Hex, isUuid } from "../_shared/http.ts";
import { validateInstallationReactivation } from "../_shared/device-lifecycle.ts";
const clean = (v: unknown, max = 120) => typeof v === "string" ? v.trim().slice(0, max) : "";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const workspaceId = Deno.env.get("NEXUS3D_AUTO_ENROLL_WORKSPACE_ID");
  if (!url || !serviceKey || !workspaceId || !isUuid(workspaceId)) return jsonResponse({ error: "automatic_registration_not_configured" }, 503);

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return jsonResponse({ error: "invalid_json" }, 400); }
  const installationId = body.installationId;
  const deviceCode = clean(body.deviceCode, 128);
  if (!isUuid(installationId) || !/^[a-f0-9]{64}$/i.test(deviceCode)) return jsonResponse({ error: "invalid_device_identity" }, 400);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const tokenHash = await sha256Hex(deviceCode);
  const info = body.deviceInfo && typeof body.deviceInfo === "object" ? body.deviceInfo as Record<string, unknown> : {};
  const manufacturer = clean(info.manufacturer, 80);
  const model = clean(info.model, 100);
  const androidVersion = clean(info.androidVersion, 40);
  const appVersion = clean(body.appVersion, 40) || "unknown";
  const buildNumber = Number.isSafeInteger(body.buildNumber) ? Number(body.buildNumber) : null;
  const versionCode = Number.isSafeInteger(body.versionCode) ? Number(body.versionCode) : null;

  const { data: existing, error: lookupError } = await admin.from("devices").select("id, installation_id, device_name, status").eq("installation_id", installationId).maybeSingle();
  if (lookupError) return jsonResponse({ error: "device_lookup_failed" }, 500);
  if (existing) {
    const { data: credential } = await admin.from("device_tokens").select("token_hash, revoked_at").eq("device_id", existing.id).maybeSingle();
    const reactivation = validateInstallationReactivation(
      existing.status,
      credential ? { tokenHash: credential.token_hash, revokedAt: credential.revoked_at } : null,
      tokenHash,
    );
    if (reactivation === "installation_revoked") return jsonResponse({ error: "installation_revoked" }, 403);
    if (reactivation === "identity_conflict") return jsonResponse({ error: "installation_identity_conflict" }, 409);
    const { data: updated, error } = await admin.from("devices").update({
      manufacturer, model, android_version: androidVersion, app_version: appVersion,
      app_build_number: buildNumber, app_version_code: versionCode,
      status: "active", archived_at: null,
      // Registration is not proof that the Editor process is currently online.
      // Presence is set only by the authenticated device-presence heartbeat.
      presence_state: "offline", presence_updated_at: null
    }).eq("id", existing.id).select("id, installation_id, device_name, status").single();
    if (error || !updated) return jsonResponse({ error: "device_update_failed" }, 500);
    return jsonResponse({ deviceId: updated.installation_id, deviceRecordId: updated.id, deviceName: updated.device_name, status: updated.status, registered: true }, 200);
  }

  const { data: created, error } = await admin.from("devices").insert({
    installation_id: installationId, workspace_id: workspaceId,
    device_name: [manufacturer, model].filter(Boolean).join(" ").slice(0, 100) || "Nexus3D device",
    manufacturer, model, android_version: androidVersion, app_version: appVersion,
    app_build_number: buildNumber, app_version_code: versionCode, platform: "android",
    status: "active", archived_at: null, presence_state: "offline", presence_updated_at: null,
    registration_method: "automatic"
  }).select("id, installation_id, device_name, status").single();
  if (error || !created) return jsonResponse({ error: "device_registration_failed" }, 500);
  const { error: tokenError } = await admin.from("device_tokens").insert({ device_id: created.id, token_hash: tokenHash });
  if (tokenError) {
    await admin.from("devices").delete().eq("id", created.id);
    return jsonResponse({ error: "device_credential_create_failed" }, 500);
  }
  return jsonResponse({ deviceId: created.installation_id, deviceRecordId: created.id, deviceName: created.device_name, status: created.status, registered: true }, 201);
});
