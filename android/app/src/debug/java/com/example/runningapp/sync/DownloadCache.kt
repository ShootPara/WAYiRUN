package com.example.runningapp.sync

import com.example.runningapp.storage.RunArchive
import java.io.File

/** Private no-backup staging, bounded to one archive per account. Every reused byte is rehashed. */
class DownloadCache(private val root: File) {
    fun clearOwner(owner: String) {
        File(root, RunArchive.sha(owner.toByteArray())).listFiles()?.forEach { check(it.delete()) }
    }
    fun prepare(owner: String, identity: String): File {
        val directory = File(root, RunArchive.sha(owner.toByteArray())).also { check(it.mkdirs() || it.isDirectory) }
        val prefix = RunArchive.sha(identity.toByteArray())
        directory.listFiles()?.filter { !it.name.startsWith("$prefix-") }?.forEach { check(it.delete()) }
        return File(directory, prefix)
    }
    fun read(key: File, index: Int, size: Int, hash: String): ByteArray? {
        val file = File("${key.path}-$index")
        if (!file.isFile || file.length() != size.toLong()) return null
        return file.readBytes().takeIf { RunArchive.sha(it) == hash }
    }
    fun write(key: File, index: Int, data: ByteArray) {
        val target = File("${key.path}-$index")
        val temp = File("${key.path}-$index.tmp")
        temp.outputStream().use { it.write(data); it.fd.sync() }
        check(temp.renameTo(target))
    }
    fun clear(key: File) { key.parentFile?.listFiles()?.filter { it.name.startsWith("${key.name}-") }?.forEach { check(it.delete()) } }
}
