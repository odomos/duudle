package com.example.duudle

import com.google.firebase.crashlytics.FirebaseCrashlytics

// Phase 7a: Crashlytics reports crashes and ANRs by itself. Call this for a caught failure
// worth seeing in the console (e.g. a doodle pop-up that couldn't be shown). The stack trace
// and message are sent; never put chat text, strokes or tokens in an exception message.
fun reportNonFatal(e: Throwable) {
    FirebaseCrashlytics.getInstance().recordException(e)
}
