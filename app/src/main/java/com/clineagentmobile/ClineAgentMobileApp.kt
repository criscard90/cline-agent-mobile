package com.clineagentmobile

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.clineagentmobile.storage.EncryptedPrefsManager

/**
 * Custom Application class that initializes the encrypted preferences manager
 * and sets global application configuration.
 */
class ClineAgentMobileApp : Application() {

    val encryptedPrefs: EncryptedPrefsManager by lazy {
        EncryptedPrefsManager.getInstance(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        // Configure dark mode based on system settings
        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        // Initialize the encrypted preferences manager
        encryptedPrefs.init()

        // Log application startup
        android.util.Log.d(
            "ClineAgentMobile",
            "Application started successfully"
        )
    }

    fun getEncryptedPrefsManager(): EncryptedPrefsManager = encryptedPrefs

    companion object {
        private const val TAG = "ClineAgentMobileApp"
    }
}
