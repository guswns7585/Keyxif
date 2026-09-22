package com.keyxif.app.domain.export

import java.io.IOException

internal enum class ExportStage { Render, Save }

internal class ExportStageException(
    val stage: ExportStage,
    cause: Throwable,
) : RuntimeException(cause)

internal fun memoryFallbackLimits(requestedLongSide: Int): List<Int> =
    listOf(requestedLongSide, 3072, 2048, 1536)
        .map { minOf(requestedLongSide, it) }
        .distinct()

internal fun exportFailureMessage(error: Throwable): String {
    val cause = if (error is ExportStageException) error.cause ?: error else error
    return when {
        cause is OutOfMemoryError -> "메모리가 부족해 저장하지 못했습니다. 출력 해상도를 낮춰 다시 시도해 주세요."
        cause is SecurityException -> "사진 또는 저장소 접근 권한이 없습니다. 사진을 다시 추가해 주세요."
        error is ExportStageException && error.stage == ExportStage.Render ->
            "사진을 변환하지 못했습니다. 원본 파일과 템플릿을 확인해 주세요."
        cause is IOException -> "저장소에 기록하지 못했습니다. 남은 저장 공간을 확인해 주세요."
        else -> "파일을 저장하지 못했습니다. 다른 형식으로 다시 시도해 주세요."
    }
}
