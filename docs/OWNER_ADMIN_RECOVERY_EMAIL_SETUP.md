# Owner Admin recovery email setup

The Owner Admin PIN/password recovery flow requests a Supabase Auth email OTP for the existing verified Owner Admin account. The Android app expects an 6–8 digit code in the email and verifies it with Supabase Auth before allowing a new local PIN/password.

## Required for this project

This Supabase organization is on the Free plan and the project was created after 3 June 2026. Supabase's default SMTP provider does not allow new Free projects to customize Auth email templates. The default Magic Link template sends a link, not the numeric code expected by the app.

To use the recovery-code flow, configure a custom SMTP provider first, then customize the **Magic Link** template to include `{{ .Token }}`.

## Example Gmail SMTP configuration

If using the Owner Admin Gmail account, configure SMTP in Supabase Dashboard → Authentication → SMTP Settings:

- Sender email: `ajasbarr@gmail.com`
- Sender name: `Nexus3D Diagnostics`
- Host: `smtp.gmail.com`
- Port: `465` (SSL) or `587` (STARTTLS)
- Username: `ajasbarr@gmail.com`
- Password: a Google App Password created for SMTP (never use the normal Gmail password)

Google App Passwords require 2-Step Verification. Enter the App Password directly in Supabase settings; never paste it into ChatGPT, source code, GitHub, or a ZIP report.

A dedicated transactional email provider such as Resend, Postmark, or SendGrid is also supported. The sender address must be verified with that provider.

## Magic Link template

In Supabase Dashboard → Authentication → Email Templates → Magic Link, set the subject to:

`Nexus3D Owner Admin recovery code`

Use the template in `supabase/templates/owner_admin_recovery_code.html`. The important variable is `{{ .Token }}`, which inserts the one-time email OTP. Keep the email's recipient bound to the verified Owner Admin account.

## Validation

1. Confirm custom SMTP is enabled and test delivery to `ajasbarr@gmail.com`.
2. Confirm the Magic Link template contains `{{ .Token }}` rather than only `{{ .ConfirmationURL }}`.
3. From the Diagnostics APK, open Owner Admin Statusbar → Unlock → Forgot PIN / Password.
4. Confirm the email contains a numeric one-time code.
5. Enter the code in the APK and confirm the server verifies it before the local PIN/password reset is allowed.

The app's password-reset flow is separate: it uses Supabase's **Reset Password** email link and the Android auth callback to set a new account password.
