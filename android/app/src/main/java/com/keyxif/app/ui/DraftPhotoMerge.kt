package com.keyxif.app.ui

internal fun <T> mergeDraftAndIncomingPhotos(
    draft: List<T>,
    incoming: List<T>,
    idOf: (T) -> String,
): List<T> {
    val incomingIds = incoming.mapTo(mutableSetOf(), idOf)
    return draft.filterNot { idOf(it) in incomingIds } + incoming
}
