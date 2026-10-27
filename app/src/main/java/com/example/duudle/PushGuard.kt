package com.example.duudle

/**
 * Whether a push should be shown on this phone. The Cloud Functions put the intended
 * account in `receiverId`; a push for a different account (or arriving before sign-in)
 * is dropped. Pushes without `receiverId` come from older functions and are allowed.
 */
fun isPushForThisAccount(receiverId: String?, currentUid: String?): Boolean =
    receiverId == null || (currentUid != null && receiverId == currentUid)

/**
 * What's remembered locally once a push token has been saved privately: the account plus
 * a hash of the token, so a changed token or a different account syncs again, and the raw
 * token itself is never written to local storage.
 */
fun pushTokenSyncMarker(uid: String, token: String): String = "$uid:${token.hashCode()}"
