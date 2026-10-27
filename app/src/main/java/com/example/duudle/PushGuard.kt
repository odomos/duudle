package com.example.duudle

/**
 * Whether a push should be shown on this phone. The Cloud Functions put the intended
 * account in `receiverId`; a push for a different account (or arriving before sign-in)
 * is dropped. Pushes without `receiverId` come from older functions and are allowed.
 */
fun isPushForThisAccount(receiverId: String?, currentUid: String?): Boolean =
    receiverId == null || (currentUid != null && receiverId == currentUid)
