package com.joon.shoplist.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_shopping_lists")
data class SavedShoppingList(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
