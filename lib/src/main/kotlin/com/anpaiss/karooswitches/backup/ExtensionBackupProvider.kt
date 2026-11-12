package com.anpaiss.karooswitches.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The "door" through which RideStats backs up an extension to the rider's
 * Google Drive and puts it back after a reset of the Karoo. An extension
 * subclasses it, says which preferences and files hold what it has learned,
 * and declares it in its manifest:
 *
 *     <provider
 *         android:name=".BackupProvider"
 *         android:authorities="${applicationId}.backup"
 *         android:exported="true"
 *         android:permission="com.anpaiss.permission.EXTENSION_BACKUP" />
 *
 * RideStats finds every provider guarded by that permission, so a new
 * extension shows up there without changing RideStats. The permission is
 * signature-level: only our own apps get in.
 *
 *  call("export") → "data": gzip of a JSON with the preferences and the text files
 *  call("import", "data") → "ok", "result" (a line for the screen)
 */
abstract class ExtensionBackupProvider : ContentProvider() {

    /** SharedPreferences files to save whole. */
    protected abstract val prefsNames: List<String>

    /** Text files or folders under filesDir. */
    protected abstract val files: List<String>

    /** Why a restore cannot run now (a ride being recorded…), or null. */
    protected open fun restoreRefusal(): String? = null

    /** After the restore wrote the disk: bring what is in memory back in line. */
    protected open fun afterRestore() {}

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context!!
        return when (method) {
            METHOD_EXPORT -> Bundle().apply { putByteArray(KEY_DATA, export(ctx)) }
            METHOD_IMPORT -> {
                val data = extras?.getByteArray(KEY_DATA)
                val busy = restoreRefusal()
                var ok = false
                val result = when {
                    data == null -> "No backup data"
                    busy != null -> busy
                    else -> runCatching { restore(ctx, data); afterRestore(); ok = true; "Restored" }
                        .getOrElse { "Restore failed: ${it.message}" }
                }
                Bundle().apply { putString(KEY_RESULT, result); putBoolean(KEY_OK, ok) }
            }
            else -> Bundle()
        }
    }

    fun export(ctx: Context): ByteArray {
        val prefs = JSONObject()
        for (name in prefsNames) {
            val o = JSONObject()
            // sorted: the same data gives the same bytes, and RideStats has nothing to send
            for ((k, v) in ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all.toSortedMap()) {
                val t = when (v) {
                    is Int -> "i"; is Long -> "l"; is Float -> "f"; is Boolean -> "b"; is String -> "s"
                    is Set<*> -> "S"
                    else -> continue
                }
                o.put(k, JSONArray().put(t).put(if (v is Set<*>) JSONArray(v.map { it.toString() }.sorted()) else v))
            }
            prefs.put(name, o)
        }
        val out = JSONObject()
        for (rel in files) {
            val f = File(ctx.filesDir, rel)
            val all = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
                      else listOf(f).filter { it.isFile }
            for (x in all) out.put(x.relativeTo(ctx.filesDir).invariantSeparatorsPath, x.readText())
        }
        val json = JSONObject().put("format", FORMAT).put("app", ctx.packageName).put("prefs", prefs).put("files", out)
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(json.toString().toByteArray()) }
        return bytes.toByteArray()
    }

    /** Everything back as in the backup; files the backup does not have are removed. All checks come first. */
    fun restore(ctx: Context, data: ByteArray) {
        val json = JSONObject(GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes().decodeToString() })
        require(json.optString("app") == ctx.packageName) { "this backup is of ${json.optString("app")}" }
        require(json.optInt("format") <= FORMAT) { "backup from a newer version, update the extension first" }
        val prefs = json.getJSONObject("prefs")
        val saved = json.getJSONObject("files")
        for (rel in saved.keys()) {
            require(files.any { rel == it || rel.startsWith("$it/") } && ".." !in rel) { "unexpected file $rel" }
        }
        for (name in prefsNames) {
            val o = prefs.optJSONObject(name) ?: JSONObject()
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().apply {
                clear()
                for (k in o.keys()) {
                    val a = o.getJSONArray(k)
                    when (a.getString(0)) {
                        "i" -> putInt(k, a.getInt(1))
                        "l" -> putLong(k, a.getLong(1))
                        "f" -> putFloat(k, a.getDouble(1).toFloat())
                        "b" -> putBoolean(k, a.getBoolean(1))
                        "s" -> putString(k, a.getString(1))
                        "S" -> a.getJSONArray(1).let { s -> putStringSet(k, (0 until s.length()).map(s::getString).toSet()) }
                    }
                }
            }.commit()
        }
        for (rel in files) File(ctx.filesDir, rel).deleteRecursively()
        for (rel in saved.keys()) File(ctx.filesDir, rel).apply { parentFile?.mkdirs() }.writeText(saved.getString(rel))
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        const val PERMISSION = "com.anpaiss.permission.EXTENSION_BACKUP"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val KEY_DATA = "data"
        const val KEY_RESULT = "result"
        const val KEY_OK = "ok"
        private const val FORMAT = 1
    }
}
