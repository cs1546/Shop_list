package com.joon.shoplist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.joon.shoplist.data.AppDatabase
import com.joon.shoplist.data.ShoppingRepository
import com.joon.shoplist.ui.ShoppingListScreen
import com.joon.shoplist.ui.ShoppingViewModel
import com.joon.shoplist.ui.theme.ShopListTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = ShoppingRepository(db.shoppingDao())
        val viewModel = ViewModelProvider(
            this,
            ShoppingViewModel.Factory(repository)
        )[ShoppingViewModel::class.java]

        setContent {
            ShopListTheme {
                ShoppingListScreen(viewModel)
            }
        }
    }
}
