package com.nexus3d.diagnostics

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Device-local Owner Admin settings. PIN/password material is never stored in
 * plaintext; only a salted PBKDF2 hash is stored in EncryptedSharedPreferences.
 * Images are stored in this app's private files directory and survive APK updates.
 */
class OwnerAdminLocalStore(private val context: Context) {
    companion object {
        private const val PREFS = "nexus3d_owner_admin_local"
        private const val SALT = "credential_salt"
        private const val HASH = "credential_hash"
        private const val KIND = "credential_kind"
        private const val PROFILE = "profile_image_path"
        private const val COVER = "cover_image_path"
        private const val FAILED_ATTEMPTS = "credential_failed_attempts"
        private const val LOCKED_UNTIL = "credential_locked_until"
        private const val ITERATIONS = 210_000
        private const val KEY_BITS = 256
    }

    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasCredential(): Boolean =
        !preferences.getString(SALT, null).isNullOrBlank() &&
            !preferences.getString(HASH, null).isNullOrBlank()

    fun credentialKind(): String = preferences.getString(KIND, "PIN") ?: "PIN"

    fun saveCredential(value: String, kind: String) {
        require(value.isNotBlank()) { "Enter a PIN or password." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(value, salt)
        preferences.edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KIND, kind)
            .putInt(FAILED_ATTEMPTS, 0)
            .remove(LOCKED_UNTIL)
            .apply()
    }

    fun verifyCredential(value: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < preferences.getLong(LOCKED_UNTIL, 0L)) return false
        val saltText = preferences.getString(SALT, null) ?: return false
        val hashText = preferences.getString(HASH, null) ?: return false
        val valid = runCatching {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, derive(value, salt))
        }.getOrDefault(false)
        if (valid) {
            preferences.edit().putInt(FAILED_ATTEMPTS, 0).remove(LOCKED_UNTIL).apply()
        } else {
            val failures = preferences.getInt(FAILED_ATTEMPTS, 0) + 1
            val edit = preferences.edit().putInt(FAILED_ATTEMPTS, failures)
            if (failures >= 5) edit.putLong(LOCKED_UNTIL, now + 5 * 60 * 1000L)
            edit.apply()
        }
        return valid
    }

    private fun derive(value: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun saveImage(uri: Uri, slot: String): String {
        require(slot == "profile" || slot == "cover") { "Unknown image slot." }
        val mime = context.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("image/")) { "Choose an image file." }
        val folder = File(context.filesDir, "owner_admin_media").apply { mkdirs() }
        val extension = when (mime.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val target = File(folder, "$slot-${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read the selected image." }
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 12L * 1024L * 1024L) { "Image must be 12 MB or smaller." }
                    output.write(buffer, 0, count)
                }
            }
        }
        val key = if (slot == "profile") PROFILE else COVER
        preferences.getString(key, null)?.takeIf { it != target.absolutePath }?.let { File(it).delete() }
        preferences.edit().putString(key, target.absolutePath).apply()
        return target.absolutePath
    }

    fun imagePath(slot: String): String? {
        val key = if (slot == "profile") PROFILE else COVER
        return preferences.getString(key, null)?.takeIf { File(it).isFile }
    }

    fun imageScale(slot: String): Float = preferences.getFloat("${slot}_scale", 1f)
    fun imageOffsetX(slot: String): Float = preferences.getFloat("${slot}_offset_x", 0f)
    fun imageOffsetY(slot: String): Float = preferences.getFloat("${slot}_offset_y", 0f)

    fun saveImageCrop(slot: String, scale: Float, offsetX: Float = 0f, offsetY: Float = 0f) {
        require(slot == "profile" || slot == "cover") { "Unknown image slot." }
        preferences.edit()
            .putFloat("${slot}_scale", scale.coerceIn(1f, 2.5f))
            .putFloat("${slot}_offset_x", offsetX.coerceIn(-1f, 1f))
            .putFloat("${slot}_offset_y", offsetY.coerceIn(-1f, 1f))
            .apply()
    }

    fun removeImage(slot: String) {
        val key = if (slot == "profile") PROFILE else COVER
        preferences.getString(key, null)?.let { File(it).delete() }
        preferences.edit()
            .remove(key)
            .remove("${slot}_scale")
            .remove("${slot}_offset_x")
            .remove("${slot}_offset_y")
            .apply()
    }
}
