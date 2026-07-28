package com.example

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.example.data.db.AppDatabase
import com.example.data.db.DocumentRepository

class DocScanApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: DocumentRepository by lazy { DocumentRepository(this, database.documentDao()) }

    override fun onCreate() {
        super.onCreate()
        // Initialize Google Mobile Ads SDK
        try {
            MobileAds.initialize(this) {}
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
