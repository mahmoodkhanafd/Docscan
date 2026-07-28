package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val category: String, // "Scanned Docs", "ID Cards", "Converted PDFs", "Favorites"
    val pageCount: Int = 1,
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null,
    val tags: String = "", // Comma-separated tags
    val isProtected: Boolean = false,
    val extractedText: String = ""
)
