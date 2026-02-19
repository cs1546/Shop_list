package com.joon.shoplist.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_shopping_items",
    foreignKeys = [ForeignKey(
        entity = SavedShoppingList::class,
        parentColumns = ["id"],
        childColumns = ["list_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SavedShoppingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "list_id", index = true)
    val listId: Long,

    val name: String
)
