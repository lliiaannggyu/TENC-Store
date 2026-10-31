package com.tengwear.tengstore

import android.app.Application
import com.tengwear.tengstore.data.AppContainer

/**
 * Initializes the application-wide dependency container before any UI is shown.
 */
class WearGitHubStoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
