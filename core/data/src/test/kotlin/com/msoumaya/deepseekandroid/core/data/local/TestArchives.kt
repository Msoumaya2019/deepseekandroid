package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.QuranArchive
import com.msoumaya.deepseekandroid.core.domain.ZIP_LINES_PER_PAGE
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fabriques d'archives pour les tests.
 *
 * Le paquet complet est construit **une fois pour tout le processus de test** : les classes d'un
 * même module partagent la JVM, et refabriquer 9 060 entrées à chaque classe coûterait plus cher
 * que la suite entière.
 */
internal object TestArchives {

    /** Une image de ligne valide : signature PNG sur deux octets et dimensions 1440×232. */
    fun png(
        width: Int = QuranArchive.IMAGE_WIDTH,
        height: Int = QuranArchive.IMAGE_HEIGHT,
        extra: Int = 0,
    ): ByteArray {
        val bytes = ByteArray(24 + extra)
        bytes[0] = 137.toByte()
        bytes[1] = 80.toByte()
        writeUint32(bytes, 16, width)
        writeUint32(bytes, 20, height)
        return bytes
    }

    fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * Le paquet complet : 9 060 pages **plus** des entrées sans rapport.
     *
     * Les entrées parasites sont là exprès : une archive réelle en contient — fichiers de
     * description, dossiers — et une installation qui les refuserait échouerait sur une archive
     * parfaitement valide.
     */
    val full: ByteArray by lazy {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        entries += "readme.txt" to "coran 1441".toByteArray()
        entries += "width_1440/" to ByteArray(0)
        entries += "width_1440/7/12.jpg" to ByteArray(0)
        for (page in 1..604) {
            for (line in 1..ZIP_LINES_PER_PAGE) {
                entries += "width_1440/$page/$line.png" to png()
            }
        }
        archive(*entries.toTypedArray())
    }

    private fun writeUint32(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value ushr 24).toByte()
        bytes[at + 1] = (value ushr 16).toByte()
        bytes[at + 2] = (value ushr 8).toByte()
        bytes[at + 3] = value.toByte()
    }
}
