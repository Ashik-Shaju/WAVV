package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogStateTest {
    @Test
    fun catalogRehydrationRestoresFavoriteAndRecentStateByUri() {
        val storedState = CatalogUserState(isFavorite = true, lastPlayedAt = 12_345L)

        assertEquals(storedState, resolveCatalogUserState(previous = null, persisted = storedState))
        assertEquals(storedState, resolveCatalogUserState(previous = CatalogUserState(false, null), persisted = storedState))
    }

    @Test
    fun explicitStoredNullRecentStateDoesNotFallBackToStaleCatalogRow() {
        val previousRow = CatalogUserState(isFavorite = true, lastPlayedAt = 99L)
        val storedState = CatalogUserState(isFavorite = false, lastPlayedAt = null)
        val refreshed = resolveCatalogUserState(previousRow, storedState)

        assertFalse(refreshed.isFavorite)
        assertNull(refreshed.lastPlayedAt)
    }

    @Test
    fun songWithoutPersistedUserStateStartsWithNeutralState() {
        val restored = resolveCatalogUserState(previous = null, persisted = null)

        assertFalse(restored.isFavorite)
        assertNull(restored.lastPlayedAt)
    }
}
