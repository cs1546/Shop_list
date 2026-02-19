package com.joon.shoplist

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
import com.joon.shoplist.ui.ShoppingListScreen
import com.joon.shoplist.ui.ShoppingViewModel
import com.joon.shoplist.ui.theme.ShopListTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = ShoppingRepository(db.shoppingDao(), db.historyDao())
        val viewModel = ViewModelProvider(
            this,
            ShoppingViewModel.Factory(repository)
        )[ShoppingViewModel::class.java]

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
                }
            }
        }
    }
}
