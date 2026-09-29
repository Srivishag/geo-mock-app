package com.example.locationspoofer

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration

class LocationSpooferApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val osmConfig = Configuration.getInstance()
        val prefs = getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        osmConfig.load(this, prefs)

        // 1. Compliant User-Agent for OpenStreetMap Fastly Global Edge CDN
        val validUserAgent = "LocationSpoofer/1.0 (Android; dev@locationspoofer.org)"
        osmConfig.userAgentValue = validUserAgent

        // 2. High-performance Parallelism: 8-thread tile downloader pool
        osmConfig.tileDownloadThreads = 8
        osmConfig.tileDownloadMaxQueueSize = 50

        // 3. High-capacity RAM & Disk Caching: Instant tile recall on pan/zoom
        osmConfig.cacheMapTileCount = 80 // 80 tiles held in fast memory cache
        osmConfig.tileFileSystemCacheMaxBytes = 200L * 1024 * 1024 // 200 MB disk cache
        osmConfig.tileFileSystemCacheTrimBytes = 160L * 1024 * 1024
        osmConfig.expirationExtendedDuration = 1000L * 60 * 60 * 24 * 30 // 30-day cache validity

        osmConfig.save(this, prefs)
    }
}
