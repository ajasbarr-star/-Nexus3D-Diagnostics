import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, sha256Hex } from "../_shared/http.ts";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return jsonResponse({ error: "unauthorized" }, 401);

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return jsonResponse({ error: "server_not_configured" }, 503);

  const userClient = createClient(url, anonKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });
  const { data: userData, error: userError } = await userClient.auth.getUser();
  if (userError || !userData.user) return jsonResponse({ error: "unauthorized" }, 401);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { data: workspace, error: workspaceError } = await admin
    .from("workspaces")
    .select("id")
    .eq("owner_id", userData.user.id)
    .maybeSingle();
  if (workspaceError || !workspace) return jsonResponse({ error: "workspace_not_found" }, 404);

  const codeBytes = crypto.getRandomValues(new Uint8Array(24));
  const code = Array.from(codeBytes).map((b) => b.toString(16).padStart(2, "0")).join("");
  const codeHash = await sha256Hex(code);
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

  const { error: insertError } = await admin.from("enrollment_codes").insert({
    workspace_id: workspace.id,
    code_hash: codeHash,
    expires_at: expiresAt,
  });
  if (insertError) return jsonResponse({ error: "enrollment_code_create_failed" }, 500);

  return jsonResponse({ code, expiresAt, expiresInSeconds: 600 });
});
