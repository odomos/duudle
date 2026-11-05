# Duudle release rules. The libraries (Firebase, Compose, Credential Manager) bring their own
# consumer rules; the app reads Firestore and the push payload by field name (Map / org.json),
# never by reflection, so it needs no keep rules of its own.

# Readable stack traces in Crashlytics: keep line numbers, hide the source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip debug and verbose logs from release builds (they carry uids and doodle ids).
# Info, warning and error logs stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
