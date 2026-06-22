package com.elderguard.safe

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object DnsBlacklist {
    private const val TAG = "DnsBlacklist"
    private val adDomains = mutableSetOf<String>()

    fun initialize(context: Context) {
        if (adDomains.isNotEmpty()) {
            Log.d(TAG, "DnsBlacklist already initialized.")
            return
        }
        Log.d(TAG, "Initializing DnsBlacklist...")
        try {
            val inputStream = context.resources.openRawResource(R.raw.ad_domains)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    val domain = line.trim()
                    if (domain.isNotEmpty() && !domain.startsWith("#")) {
                        adDomains.add(domain)
                    }
                }
            }
            Log.d(TAG, "Loaded ${adDomains.size} ad domains.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ad domains from raw resource: ${e.message}", e)
        }
    }

    fun isAdDomain(domain: String): Boolean {
        return adDomains.any { adDomain -> domain.contains(adDomain, ignoreCase = true) }
    }
}
