package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Size
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultItem(val id: String, val name: String, val isVideo: Boolean, val date: Long, val size: Long, val path: String)

/**
 * Carpeta privada. Cada archivo se guarda cifrado (AES-256) dentro del espacio de la app, fuera de
 * la galería del sistema. La clave de los archivos se guarda a su vez cifrada con otra clave que
 * vive en el almacén seguro del teléfono y no se puede extraer de él.
 */
class Vault(private val context: Context) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val temp = File(context.cacheDir, "vault_open")
    private val indexFile = File(dir, "index.tsv")
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    private val dataKey: SecretKey by lazy { loadOrCreateDataKey() }

    private fun masterKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(MASTER, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(MASTER, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun loadOrCreateDataKey(): SecretKey {
        val stored = prefs.getString("key", null)
        if (stored != null) {
            val (iv, wrapped) = stored.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv)) }
            return SecretKeySpec(cipher.doFinal(wrapped), "AES")
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, masterKey()) }
        val wrapped = cipher.doFinal(raw)
        prefs.edit().putString(
            "key",
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(wrapped, Base64.NO_WRAP),
        ).apply()
        return SecretKeySpec(raw, "AES")
    }

    /** Cada archivo empieza con su propio vector de 16 bytes y sigue con los datos cifrados. */
    private fun encrypting(file: File): OutputStream {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val out = file.outputStream()
        out.write(iv)
        return CipherOutputStream(out, Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, dataKey, IvParameterSpec(iv)) })
    }

    private fun decrypting(file: File): InputStream {
        val input = file.inputStream()
        val iv = ByteArray(16)
        check(input.read(iv) == 16)
        return CipherInputStream(input, Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, dataKey, IvParameterSpec(iv)) })
    }

    fun list(): List<VaultItem> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            indexFile.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size == 6) VaultItem(p[0], p[1], p[2] == "1", p[3].toLong(), p[4].toLong(), p[5]) else null
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.date }
    }

    private fun writeIndex(items: List<VaultItem>) {
        indexFile.writeText(items.joinToString("") { "${it.id}\t${it.name}\t${if (it.isVideo) 1 else 0}\t${it.date}\t${it.size}\t${it.path}\n" })
    }

    /** Guarda una copia cifrada de [item]. El original se borra aparte, cuando el sistema lo autoriza. */
    fun add(item: MediaItem): Boolean {
        val id = UUID.randomUUID().toString()
        val body = File(dir, "$id.bin")
        val thumb = File(dir, "$id.thumb")
        val ok = runCatching {
            context.contentResolver.openInputStream(item.uri)!!.use { input -> encrypting(body).use { input.copyTo(it) } }
            val preview = context.contentResolver.loadThumbnail(item.uri, Size(512, 512), null)
            encrypting(thumb).use { preview.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }.isSuccess
        if (!ok) {
            body.delete()
            thumb.delete()
            return false
        }
        writeIndex(list() + VaultItem(id, item.name.replace('\t', ' '), item.isVideo, item.date, item.size, item.path))
        return true
    }

    fun thumbnail(id: String): Bitmap? = runCatching {
        decrypting(File(dir, "$id.thumb")).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    /** Descifra a un archivo temporal para poder verlo. Se borra con [clearOpened]. */
    fun open(item: VaultItem): File? = runCatching {
        // Una carpeta por archivo para que conserve su nombre original a la vista.
        File(File(temp, item.id).apply { mkdirs() }, item.name.ifEmpty { "archivo" }).also { out ->
            if (!out.exists()) decrypting(File(dir, "${item.id}.bin")).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
    }.getOrNull()

    fun clearOpened() {
        temp.deleteRecursively()
    }

    /** Devuelve [item] a la galería del teléfono y lo quita de la carpeta privada. */
    fun restore(item: VaultItem): Boolean {
        val resolver = context.contentResolver
        val collection = if (item.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(item.path)) item.path else "Pictures/Lumi/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        val ok = runCatching {
            decrypting(File(dir, "${item.id}.bin")).use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }.isSuccess
        if (!ok) {
            runCatching { resolver.delete(target, null, null) }
            return false
        }
        delete(item)
        return true
    }

    fun delete(item: VaultItem) {
        File(dir, "${item.id}.bin").delete()
        File(dir, "${item.id}.thumb").delete()
        writeIndex(list().filter { it.id != item.id })
    }

    private companion object {
        const val MASTER = "lumi_vault"
    }
}
