package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDeletionTest {
    @Test
    fun urlPointsAtTheAppsOwnProject() {
        // functions/index.js exports deleteAccount in the default region, us-central1.
        assertEquals("https://us-central1-duudle-296a9.cloudfunctions.net/deleteAccount", deleteAccountUrl("duudle-296a9"))
        assertEquals("https://us-central1-duudle-staging.cloudfunctions.net/deleteAccount", deleteAccountUrl("duudle-staging"))
    }

    @Test
    fun onlyA200MeansDeleted() {
        assertEquals(DeleteResponse.DELETED, deleteResponseFor(200))
        // 401: the token was refused, maybe because an earlier try already deleted the account.
        assertEquals(DeleteResponse.CHECK_ACCOUNT, deleteResponseFor(401))
        listOf(-1, 403, 404, 405, 500, 503).forEach { assertEquals(DeleteResponse.FAILED, deleteResponseFor(it)) }
    }
}
