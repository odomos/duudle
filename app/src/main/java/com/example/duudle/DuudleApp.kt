package com.example.duudle

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Installs App Check (Phase 6b) before anything talks to Firebase, including a push that
 * starts the app in the background. Monitor-only for now: Firebase records whether requests
 * carry a valid App Check token, but nothing is refused until enforcement is switched on in
 * the console after the Play release.
 */
class DuudleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
