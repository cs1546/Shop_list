package com.joon.shoplist.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val notes: String
)

object UpdateChecker
{
    private const val GITHUB_API = "https://api.github.com/repos/cs1546/Shop_list/releases/latest"

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try
        {
            val conn = (URL(GITHUB_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if(conn.responseCode != 200)
                return@withContext null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val latest = tag.trimStart('v', 'V')
            val notes = json.optString("body", "")

            val assets = json.getJSONArray("assets")
            var apkUrl = ""
            for(i in 0 until assets.length())
            {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if(name.endsWith(".apk"))
                {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if(apkUrl.isEmpty()) return@withContext null

            if(compareVersions(latest, currentVersion) <= 0) return@withContext null

            UpdateInfo(latest, apkUrl, notes)
        }
        catch(e: Exception) { null }
    }

    suspend fun download(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try
        {
            val dir = File(context.cacheDir, "updates")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "update.apk")

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                file.outputStream().use { out -> input.copyTo(out) }
            }
            file
        }
        catch(e: Exception) { null }
    }

    fun install(context: Context, apkFile: File)
    {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun compareVersions(a: String, b: String): Int
    {
        val va = parseVersion(a)
        val vb = parseVersion(b)
        val len = maxOf(va.size, vb.size)
        for(i in 0 until len)
        {
            val ai = va.getOrElse(i) { 0 }
            val bi = vb.getOrElse(i) { 0 }
            if(ai != bi) return ai.compareTo(bi)
        }
        return 0
    }

    private fun parseVersion(v: String): IntArray =
        v.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }.toIntArray()
}
