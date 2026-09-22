package com.keyxif.app.domain.export

import java.io.IOException

internal enum class ExportStage { Render, Save }

internal class ExportStageException(
    val stage: ExportStage,
    cause: Throwable,
) : RuntimeException(cause)

internal enum class ExportFailureCode(val value: String) {
    SourceAccess("KX-SAVE-101"),
    OutOfMemory("KX-SAVE-201"),
    Render("KX-SAVE-202"),
    Encoding("KX-SAVE-301"),
    TemporaryStorage("KX-SAVE-401"),
    GalleryInsert("KX-SAVE-501"),
    GalleryWrite("KX-SAVE-502"),
    StorageIo("KX-SAVE-503"),
    Unknown("KX-SAVE-901"),
}

internal data class ExportFailureInfo(
    val code: ExportFailureCode,
    val message: String,
) {
    val userMessage: String
        get() = "[${code.value}] $message"
}

internal fun memoryFallbackLimits(requestedLongSide: Int): List<Int> =
    listOf(requestedLongSide, 3072, 2048, 1536)
        .map { minOf(requestedLongSide, it) }
        .distinct()

internal fun shouldRetryAtLowerResolution(error: ExportStageException): Boolean =
    error.cause is OutOfMemoryError || error.cause is ImageEncodingException

internal fun exportFailureInfo(error: Throwable): ExportFailureInfo {
    val causes = generateSequence(error) { it.cause }.toList()
    val stage = causes.filterIsInstance<ExportStageException>().firstOrNull()?.stage
    return when {
        causes.any { it is OutOfMemoryError } -> ExportFailureInfo(
            ExportFailureCode.OutOfMemory,
            "메모리가 부족해 저장하지 못했습니다. 출력 해상도를 낮춰 다시 시도해 주세요.",
        )
        causes.any { it is SecurityException } -> ExportFailureInfo(
            ExportFailureCode.SourceAccess,
            "사진 또는 저장소 접근 권한이 없습니다. 사진을 다시 추가해 주세요.",
        )
        causes.any { it is ImageEncodingException } -> ExportFailureInfo(
            ExportFailureCode.Encoding,
            "기기에서 선택한 이미지 형식 변환을 완료하지 못했습니다. 앱을 다시 시작한 뒤 시도해 주세요.",
        )
        causes.any { it is TemporaryStorageException } -> ExportFailureInfo(
            ExportFailureCode.TemporaryStorage,
            "이미지 저장을 준비할 공간이 부족합니다. 앱 캐시와 저장 공간을 확인해 주세요.",
        )
        causes.any { it is GalleryInsertException } -> ExportFailureInfo(
            ExportFailureCode.GalleryInsert,
            "갤러리에 저장 항목을 만들지 못했습니다. 저장 공간과 사진 앱 상태를 확인해 주세요.",
        )
        causes.any { it is GalleryWriteException } -> ExportFailureInfo(
            ExportFailureCode.GalleryWrite,
            "갤러리에 파일을 기록하지 못했습니다. 남은 저장 공간을 확인해 주세요.",
        )
        stage == ExportStage.Render -> ExportFailureInfo(
            ExportFailureCode.Render,
            "사진을 변환하지 못했습니다. 원본 파일과 템플릿을 확인해 주세요.",
        )
        causes.any { it is IOException } -> ExportFailureInfo(
            ExportFailureCode.StorageIo,
            "저장소에 기록하지 못했습니다. 남은 저장 공간을 확인해 주세요.",
        )
        else -> ExportFailureInfo(
            ExportFailureCode.Unknown,
            "알 수 없는 이유로 파일을 저장하지 못했습니다. 오류 코드를 알려 주세요.",
        )
    }
}

internal fun exportFailureMessage(error: Throwable): String = exportFailureInfo(error).userMessage
