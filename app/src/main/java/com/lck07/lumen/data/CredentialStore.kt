package com.lck07.lumen.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lck07.lumen.core.model.AccountInfo
import com.lck07.lumen.core.model.XtreamAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets with an AES key that never leaves the Android Keystore. */
interface SecretBox {
    fun seal(plain: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray
}

class KeystoreSecretBox(private val alias: String = "lumen.credentials") : SecretBox {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        val iv = sealed.copyOfRange(0, 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return c.doFinal(sealed, 12, sealed.size - 12)
    }
}

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore("account")

/**
 * The Xtream login, encrypted with the Keystore. Only the encrypted blob is written to disk;
 * credentials are never logged. Account status (not secret) is kept beside it.
 */
class CredentialStore(private val context: Context, private val box: SecretBox = KeystoreSecretBox()) {
    @Serializable private data class Blob(val server: String, val username: String, val password: String)

    private object K {
        val blob = stringPreferencesKey("blob")
        val status = stringPreferencesKey("status")
        val expires = longPreferencesKey("expires")
        val maxCons = intPreferencesKey("max_connections")
        val activeCons = intPreferencesKey("active_connections")
        val checkedAt = longPreferencesKey("checked_at")
    }

    val account: Flow<XtreamAccount?> = context.accountStore.data.map { p -> p[K.blob]?.let(::decode) }

    val info: Flow<StoredAccountInfo?> = context.accountStore.data.map { p ->
        p[K.status]?.let { StoredAccountInfo(it, p[K.expires]?.let(Instant::ofEpochSecond), p[K.maxCons], p[K.activeCons], p[K.checkedAt] ?: 0) }
    }

    suspend fun current(): XtreamAccount? = account.first()

    suspend fun save(account: XtreamAccount, info: AccountInfo) {
        val json = Json.encodeToString(Blob.serializer(), Blob(account.server, account.username, account.password))
        val sealed = Base64.encodeToString(box.seal(json.toByteArray()), Base64.NO_WRAP)
        context.accountStore.edit { it[K.blob] = sealed }
        saveInfo(info)
    }

    suspend fun saveInfo(info: AccountInfo) = context.accountStore.edit { p ->
        p[K.status] = info.status
        info.expiresAt?.let { p[K.expires] = it.epochSecond } ?: p.remove(K.expires)
        info.maxConnections?.let { p[K.maxCons] = it } ?: p.remove(K.maxCons)
        info.activeConnections?.let { p[K.activeCons] = it } ?: p.remove(K.activeCons)
        p[K.checkedAt] = System.currentTimeMillis()
    }

    suspend fun saveStatus(status: String) = context.accountStore.edit { it[K.status] = status; it[K.checkedAt] = System.currentTimeMillis() }

    suspend fun clear() = context.accountStore.edit { it.clear() }

    private fun decode(sealed: String): XtreamAccount? = try {
        val json = String(box.open(Base64.decode(sealed, Base64.NO_WRAP)))
        val b = Json.decodeFromString(Blob.serializer(), json)
        XtreamAccount(b.server, b.username, b.password)
    } catch (_: Exception) {
        // The key is gone (e.g. restored to another device): the owner signs in again.
        null
    }
}

data class StoredAccountInfo(
    val status: String,
    val expiresAt: Instant?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val checkedAt: Long,
) {
    val active: Boolean get() = status.equals("Active", ignoreCase = true)
    fun expired(now: Instant = Instant.now()): Boolean = !active || (expiresAt != null && expiresAt.isBefore(now))
}
