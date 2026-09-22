package com.keyxif.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftPhotoMergeTest {
    private data class Photo(val id: String, val origin: String)

    @Test
    fun sharedPhotosRemainAvailableAfterRestoringDraft() {
        val draft = listOf(Photo("old", "draft"))
        val incoming = listOf(Photo("new", "share"))

        assertEquals(
            listOf(Photo("old", "draft"), Photo("new", "share")),
            mergeDraftAndIncomingPhotos(draft, incoming, Photo::id),
        )
    }

    @Test
    fun incomingPhotoWinsWhenAnIdAlreadyExistsInDraft() {
        val draft = listOf(Photo("same", "draft"), Photo("old", "draft"))
        val incoming = listOf(Photo("same", "share"))

        assertEquals(
            listOf(Photo("old", "draft"), Photo("same", "share")),
            mergeDraftAndIncomingPhotos(draft, incoming, Photo::id),
        )
    }
}
