import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, isUuid, jsonResponse, sha256Hex } from "../_shared/http.ts";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return jsonResponse({ error: "server_not_configured" }, 503);

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return jsonResponse({ error: "invalid_json" }, 400); }
  const code = typeof body.code === "string" ? body.code.trim() : "";
  const installationId = body.installationId;
  const deviceName = typeof body.deviceName === "string" ? body.deviceName.trim().slice(0, 100) : "Nexus3D device";
  const appVersion = typeof body.appVersion === "string" ? body.appVersion.trim().slice(0, 40) : "unknown";
  if (code.length !== 48 || !isUuid(installationId)) {
    return jsonResponse({ error: "invalid_enrollment_request" }, 400);
  }

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const codeHash = await sha256Hex(code);
  const { data: workspaceId, error: consumeError } = await admin.rpc(
    "consume_nexus3d_enrollment_code",
    { p_code_hash: codeHash },
  );
  if (consumeError || !workspaceId) return jsonResponse({ error: "invalid_or_expired_enrollment_code" }, 401);

  const { data: existing } = await admin.from("devices")
    .select("id")
    .eq("installation_id", installationId)
    .maybeSingle();
  if (existing) return jsonResponse({ error: "installation_already_registered" }, 409);

  const { data: device, error: deviceError } = await admin.from("devices").insert({
    installation_id: installationId,
    workspace_id: workspaceId,
    device_name: deviceName || "Nexus3D device",
    app_version: appVersion || "unknown",
    platform: "android",
    status: "active",
    last_seen_at: new Date().toISOString(),
  }).select("id, installation_id, device_name, status").single();
  if (deviceError || !device) return jsonResponse({ error: "device_registration_failed" }, 500);

  const tokenBytes = crypto.getRandomValues(new Uint8Array(32));
  const deviceToken = Array.from(tokenBytes).map((b) => b.toString(16).padStart(2, "0")).join("");
  const tokenHash = await sha256Hex(deviceToken);
  const { error: tokenError } = await admin.from("device_tokens").insert({
    device_id: device.id,
    token_hash: tokenHash,
  });
  if (tokenError) {
    await admin.from("devices").delete().eq("id", device.id);
    return jsonResponse({ error: "device_credential_create_failed" }, 500);
  }

  return jsonResponse({
    deviceId: device.installation_id,
    deviceName: device.device_name,
    status: device.status,
    deviceToken,
    registeredAt: new Date().toISOString(),
  }, 201);
});
