package com.joon.shoplist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.joon.shoplist.data.AppDatabase
import com.joon.shoplist.data.ShoppingRepository
import com.joon.shoplist.ui.HistoryDetailScreen
import com.joon.shoplist.ui.HistoryListScreen
import com.joon.shoplist.ui.Screen
import com.joon.shoplist.ui.SettingsScreen
import com.joon.shoplist.ui.ShoppingListScreen
import com.joon.shoplist.ui.ShoppingViewModel
import com.joon.shoplist.ui.theme.ShopListTheme
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ShoppingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = ShoppingRepository(db.shoppingDao(), db.historyDao())
        viewModel = ViewModelProvider(
            this,
            ShoppingViewModel.Factory(repository, applicationContext)
        )[ShoppingViewModel::class.java]

        handleIncomingIntent(intent)

        setContent {
            ShopListTheme {
                val screen by viewModel.currentScreen.collectAsState()

                if(screen !is Screen.ShoppingList) {
                    BackHandler { viewModel.goBack() }
                }

                when(val s = screen) {
                    is Screen.ShoppingList -> ShoppingListScreen(viewModel)
                    is Screen.HistoryList -> HistoryListScreen(viewModel)
                    is Screen.HistoryDetail -> HistoryDetailScreen(viewModel, s.date)
                    is Screen.Settings -> SettingsScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if(intent?.action != Intent.ACTION_SEND) return

        when(intent.type) {
            "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val names = parseSharedText(text)
                if(names.isNotEmpty())
                    viewModel.importItems(names)
            }
            "application/zip", "application/octet-stream", "application/x-zip-compressed" -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
                importFromZip(uri)
            }
        }
    }

    private fun importFromZip(uri: Uri) {
        try {
            val imagesDir = File(filesDir, "images")
            imagesDir.mkdirs()

            val imageMap = mutableMapOf<String, String>()
            var jsonContent = ""

            contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while(entry != null) {
                        if(entry.name == "list.json") {
                            jsonContent = zip.bufferedReader().readText()
                        } else if(entry.name.startsWith("img_")) {
                            val destFile = File(imagesDir, "${System.currentTimeMillis()}_${entry.name}")
                            destFile.outputStream().use { out -> zip.copyTo(out) }
                            imageMap[entry.name] = destFile.absolutePath
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if(jsonContent.isNotEmpty()) {
                val items = JSONObject(jsonContent).getJSONArray("items")
                val importList = mutableListOf<Triple<String, Boolean, String>>()
                for(i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val name = obj.getString("name")
                    val checked = obj.getBoolean("checked")
                    val imgFile = obj.optString("image", "")
                    val localPath = if(imgFile.isNotEmpty()) imageMap[imgFile] ?: "" else ""
                    importList.add(Triple(name, checked, localPath))
                }
                viewModel.importFullItems(importList)
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseSharedText(text: String): List<String> {
        return text.lines()
            .map { line ->
                line.trim()
                    .removePrefix("[v]").removePrefix("[ ]")
                    .removePrefix("[V]").removePrefix("[x]").removePrefix("[X]")
                    .trim()
            }
            .filter { it.isNotBlank() && it != "쇼핑 체크리스트" && !it.all { c -> c == '─' } }
    }
}
