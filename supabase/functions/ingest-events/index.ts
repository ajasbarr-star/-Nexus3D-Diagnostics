import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, isUuid, jsonResponse, sha256Hex } from "../_shared/http.ts";

const allowedSources = new Set(["RDS_LOGCAT", "RIT_TRACE", "RUNTIME"]);
const allowedSeverities = new Set(["VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"]);

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  const authorization = req.headers.get("Authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (token.length !== 64) return jsonResponse({ error: "unauthorized" }, 401);

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return jsonResponse({ error: "server_not_configured" }, 503);
  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });

  const tokenHash = await sha256Hex(token);
  const { data: credential, error: credentialError } = await admin.from("device_tokens")
    .select("device_id, devices!inner(installation_id,status)")
    .eq("token_hash", tokenHash)
    .is("revoked_at", null)
    .maybeSingle();
  if (credentialError || !credential || credential.devices.status !== "active") {
    return jsonResponse({ error: "invalid_or_revoked_device_token" }, 401);
  }

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return jsonResponse({ error: "invalid_json" }, 400); }
  if (!Array.isArray(body.events) || body.events.length < 1 || body.events.length > 50) {
    return jsonResponse({ error: "batch_must_contain_1_to_50_events" }, 400);
  }
  if (JSON.stringify(body).length > 256_000) return jsonResponse({ error: "payload_too_large" }, 413);

  const deviceId = credential.devices.installation_id;
  const rows = [];
  for (const raw of body.events) {
    if (!raw || typeof raw !== "object") return jsonResponse({ error: "invalid_event" }, 400);
    const event = raw as Record<string, unknown>;
    if (!isUuid(event.eventId) || !isUuid(event.sessionId)) return jsonResponse({ error: "invalid_event_or_session_id" }, 400);
    if (!Number.isSafeInteger(event.sequence) || Number(event.sequence) < 0) return jsonResponse({ error: "invalid_sequence" }, 400);
    if (event.schemaVersion !== 1) return jsonResponse({ error: "unsupported_schema_version" }, 400);
    if (typeof event.source !== "string" || !allowedSources.has(event.source)) return jsonResponse({ error: "invalid_source" }, 400);
    if (typeof event.severity !== "string" || !allowedSeverities.has(event.severity.toUpperCase())) return jsonResponse({ error: "invalid_severity" }, 400);
    if (typeof event.title !== "string" || event.title.trim().length < 1 || event.title.length > 240) return jsonResponse({ error: "invalid_title" }, 400);
    if (typeof event.details !== "string" || event.details.length > 24_000) return jsonResponse({ error: "invalid_details" }, 400);
    const occurredAt = typeof event.timestamp === "string" ? new Date(event.timestamp) : new Date(Number(event.timestamp));
    if (!Number.isFinite(occurredAt.getTime())) return jsonResponse({ error: "invalid_timestamp" }, 400);

    rows.push({
      device_id: credential.device_id,
      event_id: event.eventId,
      session_id: event.sessionId,
      sequence: Number(event.sequence),
      schema_version: 1,
      source: event.source,
      severity: String(event.severity).toUpperCase(),
      title: event.title.trim(),
      details: event.details,
      occurred_at: occurredAt.toISOString(),
    });
  }

  const { data: inserted, error: insertError } = await admin.from("diagnostic_reports")
    .upsert(rows, { onConflict: "device_id,event_id", ignoreDuplicates: true })
    .select("event_id");
  if (insertError) return jsonResponse({ error: "report_storage_failed" }, 500);

  await admin.from("devices").update({ last_seen_at: new Date().toISOString() })
    .eq("id", credential.device_id);

  return jsonResponse({
    ok: true,
    deviceId,
    received: rows.length,
    newlyStored: inserted?.length ?? 0,
    acknowledgedEventIds: rows.map((row) => row.event_id),
    duplicateSafe: true,
  });
});
