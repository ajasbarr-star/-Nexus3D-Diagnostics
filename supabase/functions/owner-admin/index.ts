import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, jsonResponse, sha256Hex } from "../_shared/http.ts";

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let index = 0; index < a.length; index++) {
    difference |= a.charCodeAt(index) ^ b.charCodeAt(index);
  }
  return difference === 0;
}

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
  const user = userData.user;
  if (userError || !user || !user.email) return jsonResponse({ error: "unauthorized" }, 401);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { data: account, error: lookupError } = await admin
    .from("nexus3d_admin_accounts")
    .select("id,email,role,status,created_at")
    .eq("auth_user_id", user.id)
    .maybeSingle();

  if (lookupError) {
    console.error("owner_admin_status_lookup_failed", lookupError);
    return jsonResponse({ error: "admin_status_unavailable" }, 500);
  }

  let body: Record<string, unknown> = {};
  try {
    const parsed = await req.json();
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      body = parsed as Record<string, unknown>;
    }
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }

  const action = body.action === "bootstrap" ? "bootstrap" : "status";
  if (action === "status") {
    const ownerEmail = (Deno.env.get("NEXUS3D_OWNER_ADMIN_EMAIL") ?? "").trim().toLowerCase();
    const confirmed = Boolean(user.email_confirmed_at);
    const eligible = Boolean(ownerEmail) && confirmed &&
      user.email.trim().toLowerCase() === ownerEmail;
    return jsonResponse({
      registered: Boolean(account),
      adminId: account?.id ?? null,
      role: account?.role ?? null,
      status: account?.status ?? null,
      eligible,
      emailVerified: confirmed,
    });
  }

  if (account) {
    return jsonResponse({
      registered: true,
      adminId: account.id,
      role: account.role,
      status: account.status,
    });
  }

  const ownerEmail = (Deno.env.get("NEXUS3D_OWNER_ADMIN_EMAIL") ?? "").trim().toLowerCase();
  const setupCodeHash = (Deno.env.get("NEXUS3D_ADMIN_SETUP_CODE_SHA256") ?? "").trim().toLowerCase();
  if (!ownerEmail || !/^[a-f0-9]{64}$/.test(setupCodeHash)) {
    return jsonResponse({ error: "admin_registration_not_configured" }, 503);
  }

  if (!user.email_confirmed_at) return jsonResponse({ error: "email_not_verified" }, 403);
  if (user.email.trim().toLowerCase() !== ownerEmail) {
    return jsonResponse({ error: "owner_email_not_authorized" }, 403);
  }

  const setupCode = typeof body.setupCode === "string" ? body.setupCode.trim() : "";
  if (setupCode.length < 20 || setupCode.length > 256) {
    return jsonResponse({ error: "invalid_setup_code" }, 400);
  }

  const submittedHash = await sha256Hex(setupCode);
  if (!constantTimeEqual(submittedHash, setupCodeHash)) {
    return jsonResponse({ error: "invalid_setup_code" }, 403);
  }

  const { data: claimed, error: claimError } = await admin.rpc("claim_nexus3d_owner_admin", {
    p_auth_user_id: user.id,
    p_email: user.email,
  });

  if (claimError) {
    const code = claimError.message?.includes("owner_admin_already_claimed")
      ? "owner_admin_already_claimed"
      : claimError.message?.includes("owner_admin_email_not_verified")
        ? "email_not_verified"
        : "owner_admin_registration_failed";
    return jsonResponse({ error: code }, code === "owner_admin_already_claimed" ? 409 : 403);
  }

  const result = Array.isArray(claimed) ? claimed[0] : claimed;
  if (!result?.admin_id) return jsonResponse({ error: "owner_admin_registration_failed" }, 500);

  return jsonResponse({
    registered: true,
    adminId: result.admin_id,
    role: "owner_admin",
    status: result.admin_status,
  });
});
