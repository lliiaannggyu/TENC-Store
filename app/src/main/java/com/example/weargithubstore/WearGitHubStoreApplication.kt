package com.example.weargithubstore

import android.app.Application
import com.example.weargithubstore.data.AppContainer

/**
 * Initializes the application-wide dependency container before any UI is shown.
 */
class WearGitHubStoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
