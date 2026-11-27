import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, sha256Hex, isUuid } from "../_shared/http.ts";

const clean = (value: unknown, max = 128) =>
  typeof value === "string" ? value.trim().slice(0, max) : "";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return jsonResponse({ error: "presence_service_not_configured" }, 503);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }

  const installationId = body.installationId;
  const deviceCode = clean(body.deviceCode);
  const state = clean(body.state, 16).toLowerCase();
  if (!isUuid(installationId) || !/^[a-f0-9]{64}$/i.test(deviceCode)) {
    return jsonResponse({ error: "invalid_device_identity" }, 400);
  }
  if (state !== "online" && state !== "offline") {
    return jsonResponse({ error: "invalid_presence_state" }, 400);
  }

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { data: device, error: deviceError } = await admin
    .from("devices")
    .select("id, status")
    .eq("installation_id", installationId)
    .maybeSingle();
  if (deviceError) return jsonResponse({ error: "device_lookup_failed" }, 500);
  if (!device || device.status !== "active") return jsonResponse({ error: "device_not_registered" }, 404);

  const { data: credential, error: credentialError } = await admin
    .from("device_tokens")
    .select("token_hash, revoked_at")
    .eq("device_id", device.id)
    .maybeSingle();
  if (credentialError) return jsonResponse({ error: "credential_lookup_failed" }, 500);
  if (!credential || credential.revoked_at || credential.token_hash !== await sha256Hex(deviceCode)) {
    return jsonResponse({ error: "device_credential_invalid" }, 401);
  }

  const now = new Date().toISOString();
  const update: Record<string, unknown> = {
    presence_state: state,
    presence_updated_at: now,
    // Keep the installation's inactivity/archive clock current on authenticated contact.
    last_seen_at: now,
  };
  if (state === "online") update.last_active_at = now;

  // Optional metadata is validated independently. Missing/invalid values never erase
  // the last known-good Device Info values.
  const info = body.deviceInfo && typeof body.deviceInfo === "object"
    ? body.deviceInfo as Record<string, unknown> : {};
  const manufacturer = clean(info.manufacturer, 80);
  const model = clean(info.model, 100);
  const androidVersion = clean(info.androidVersion, 40);
  const appVersion = clean(body.appVersion, 40);
  const buildNumber = Number.isSafeInteger(body.buildNumber) && Number(body.buildNumber) >= 0
    ? Number(body.buildNumber) : null;
  const versionCode = Number.isSafeInteger(body.versionCode) && Number(body.versionCode) >= 0
    ? Number(body.versionCode) : null;

  if (manufacturer) update.manufacturer = manufacturer;
  if (model) update.model = model;
  if (androidVersion) update.android_version = androidVersion;
  if (appVersion) update.app_version = appVersion;
  if (buildNumber !== null) update.app_build_number = buildNumber;
  if (versionCode !== null) update.app_version_code = versionCode;
  if (manufacturer || model) {
    const deviceName = [manufacturer, model].filter(Boolean).join(" ").slice(0, 100);
    if (deviceName) update.device_name = deviceName;
  }

  const { error: updateError } = await admin.from("devices").update(update).eq("id", device.id);
  if (updateError) return jsonResponse({ error: "presence_update_failed" }, 500);

  return jsonResponse({ presenceState: state, lastActiveAt: state === "online" ? now : undefined });
});
