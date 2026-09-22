package com.keyxif.app.domain.export

import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFailureTest {
    @Test
    fun memoryFallbackOnlyTriesLowerResolutions() {
        assertEquals(listOf(4096, 3072, 2048, 1536), memoryFallbackLimits(4096))
        assertEquals(listOf(1920, 1536), memoryFallbackLimits(1920))
    }

    @Test
    fun memoryFailureSuggestsLowerResolution() {
        val message = exportFailureMessage(ExportStageException(ExportStage.Render, OutOfMemoryError()))
        assertTrue(message.contains(ExportFailureCode.OutOfMemory.value))
        assertTrue(message.contains("해상도"))
    }

    @Test
    fun saveFailureMentionsStorage() {
        val message = exportFailureMessage(ExportStageException(ExportStage.Save, IOException()))
        assertTrue(message.contains(ExportFailureCode.StorageIo.value))
        assertTrue(message.contains("저장 공간"))
    }

    @Test
    fun renderFailureMentionsSourceAndTemplate() {
        val message = exportFailureMessage(ExportStageException(ExportStage.Render, IllegalArgumentException()))
        assertTrue(message.contains(ExportFailureCode.Render.value))
        assertTrue(message.contains("원본 파일과 템플릿"))
    }

    @Test
    fun encodingFailureIsNotReportedAsGenericFailure() {
        val error = ImageEncodingException(IllegalStateException())
        val message = exportFailureMessage(ExportStageException(ExportStage.Save, error))
        assertTrue(message.contains(ExportFailureCode.Encoding.value))
        assertTrue(message.contains("선택한 이미지 형식"))
    }

    @Test
    fun encodingFailureRetriesAtLowerResolutionWithoutChangingFormat() {
        val error = ExportStageException(
            ExportStage.Save,
            ImageEncodingException(IllegalStateException()),
        )
        assertTrue(shouldRetryAtLowerResolution(error))
    }

    @Test
    fun mediaStoreInsertFailureHasActionableMessage() {
        val error = GalleryInsertException(IllegalStateException())
        val message = exportFailureMessage(ExportStageException(ExportStage.Save, error))
        assertTrue(message.contains(ExportFailureCode.GalleryInsert.value))
        assertTrue(message.contains("사진 앱 상태"))
    }

    @Test
    fun nestedSecurityFailureKeepsSourceAccessCode() {
        val error = GalleryWriteException(SecurityException("denied"))
        val info = exportFailureInfo(ExportStageException(ExportStage.Save, error))
        assertEquals(ExportFailureCode.SourceAccess, info.code)
    }
}
