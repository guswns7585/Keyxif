package com.keyxif.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildInfoDisplayTest {
    @Test
    fun `housing is rendered with BOARD label`() {
        val rows = KeyboardBuildInfo(housing = "OTD 356CL").toDisplayRows()

        assertEquals(listOf(BuildInfoRow("BOARD", "OTD 356CL")), rows)
    }
}
