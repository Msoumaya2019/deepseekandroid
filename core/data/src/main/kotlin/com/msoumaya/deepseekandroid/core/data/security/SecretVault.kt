package com.msoumaya.deepseekandroid.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement des secrets locaux par le magasin de clés Android.
 *
 * Le jeton de session Supabase donne accès à toutes les données de l'utilisateur. Il ne doit
 * donc pas être écrit en clair sur le disque : n'importe quelle sauvegarde `adb backup`, ou
 * un accès physique au fichier, suffirait à le lire.
 *
 * La clé ne quitte jamais le magasin de clés : elle y est générée, y reste, et n'est
 * utilisable que par cette application. Aucun mot de passe n'est dérivé, donc aucun mot de
 * passe n'est à choisir ni à stocker — et il n'existe pas de secret à commiter.
 *
 * Le mode est AES/GCM : chiffrement authentifié. Une altération du fichier est détectée au
 * déchiffrement au lieu de produire silencieusement des données fausses.
 */
class SecretVault(private val alias: String = DEFAULT_ALIAS) {

    /** Chiffre [plain] et renvoie l'enveloppe complète (en-tête + IV + cryptogramme). */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain)
        return Envelope.pack(cipher.iv, encrypted)
    }

    /**
     * Déchiffre une enveloppe produite par [encrypt].
     *
     * Renvoie `null` si l'enveloppe est illisible ou si l'authentification échoue. Le cas se
     * produit légitimement après une restauration de sauvegarde sur un autre appareil : la
     * clé n'y existe plus. L'appelant traite alors l'absence de session comme une
     * déconnexion, ce qui est le comportement attendu.
     */
    fun decrypt(blob: ByteArray): ByteArray? {
        val envelope = Envelope.unpack(blob) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, envelope.iv))
            cipher.doFinal(envelope.ciphertext)
        }.getOrNull()
    }

    /** Supprime la clé. Les secrets déjà chiffrés deviennent définitivement illisibles. */
    fun destroyKey() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Aucune authentification biométrique : l'application doit pouvoir
                // synchroniser en arrière-plan sans intervention de l'utilisateur.
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Format de l'enveloppe : `version (1 octet) | longueur IV (1 octet) | IV | cryptogramme`.
     *
     * Séparé du chiffrement pour être vérifiable sans magasin de clés : un en-tête mal formé
     * doit être rejeté avant tout appel au magasin, pas après.
     */
    internal object Envelope {

        private const val VERSION: Byte = 1

        fun pack(iv: ByteArray, ciphertext: ByteArray): ByteArray {
            require(iv.size in 1..255) { "IV de taille invalide : ${iv.size}" }
            val out = ByteArray(2 + iv.size + ciphertext.size)
            out[0] = VERSION
            out[1] = iv.size.toByte()
            iv.copyInto(out, 2)
            ciphertext.copyInto(out, 2 + iv.size)
            return out
        }

        data class Unpacked(val iv: ByteArray, val ciphertext: ByteArray) {
            override fun equals(other: Any?): Boolean =
                other is Unpacked && iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)

            override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
        }

        /** Renvoie `null` sur toute enveloppe qui n'a pas exactement la forme attendue. */
        fun unpack(blob: ByteArray): Unpacked? {
            if (blob.size < 3) return null
            if (blob[0] != VERSION) return null
            val ivLength = blob[1].toInt() and 0xFF
            if (ivLength == 0 || blob.size < 2 + ivLength + 1) return null
            val iv = blob.copyOfRange(2, 2 + ivLength)
            val ciphertext = blob.copyOfRange(2 + ivLength, blob.size)
            return Unpacked(iv, ciphertext)
        }
    }

    companion object {
        const val DEFAULT_ALIAS = "deepseekandroid.session.v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** Longueur du tag d'authentification GCM, en bits. */
        private const val TAG_BITS = 128
    }
}
