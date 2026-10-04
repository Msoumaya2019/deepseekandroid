package com.msoumaya.deepseekandroid.core.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Un fichier binaire lu et écrit de façon atomique.
 *
 * Utilisé pour le jeton de session chiffré. L'écriture passe par un fichier temporaire suivi
 * d'un renommage : une coupure en cours d'écriture laisse l'ancien contenu intact, jamais un
 * fichier à moitié écrit. Un jeton tronqué serait indéchiffrable et l'utilisateur serait
 * déconnecté sans raison apparente.
 */
class BlobFile(private val file: File) {

    suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        file.readBytes().takeIf { it.isNotEmpty() }
    }

    suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        move(temp, file)
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
        Unit
    }

    fun exists(): Boolean = file.isFile && file.length() > 0

    private fun move(from: File, to: File) {
        runCatching {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.recoverCatching {
            // Certains systèmes de fichiers Android refusent ATOMIC_MOVE ; le renommage
            // simple reste atomique sur un même volume.
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }
}
