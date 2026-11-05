package com.example.duudle

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Dev builds can't pass Play Integrity, so they use App Check's debug provider. On first run
 * it logs a debug secret (tag "com.google.firebase.appcheck.debug"); registering that in the
 * Firebase console marks this install's requests as verified. Release uses Play Integrity.
 */
fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
