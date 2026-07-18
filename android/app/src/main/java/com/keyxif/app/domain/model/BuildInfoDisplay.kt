package com.keyxif.app.domain.model

import java.util.Locale

data class BuildInfoRow(
    val label: String,
    val value: String,
)

fun String?.meaningfulBuildTextOrNull(): String? {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return null

    val normalized = value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    return value.takeUnless { normalized in blockedBuildTexts }
}

fun String?.isMeaningfulBuildText(): Boolean = meaningfulBuildTextOrNull() != null

fun KeyboardBuildInfo.toDisplayRows(
    includeNickname: Boolean = false,
): List<BuildInfoRow> {
    return buildList {
        housing.meaningfulBuildTextOrNull()?.let { add(BuildInfoRow("BOARD", it)) }
        switchName.meaningfulBuildTextOrNull()?.let { add(BuildInfoRow("Switch", it)) }
        plate.meaningfulBuildTextOrNull()?.let { add(BuildInfoRow("Plate", it)) }
        mount.meaningfulBuildTextOrNull()?.let { add(BuildInfoRow("Mount", it)) }
        keycap.meaningfulBuildTextOrNull()?.let { add(BuildInfoRow("Keycap", it)) }
        if (includeNickname) {
            nickname.meaningfulBuildTextOrNull()?.let { add(BuildInfoRow("Nickname", it)) }
        }
    }
}

fun KeyboardBuildInfo.displayNicknameOrNull(): String? {
    return nickname.meaningfulBuildTextOrNull()
}

fun KeyboardBuildInfo.displayTitleOrNull(): String? {
    return listOf(nickname, housing)
        .firstNotNullOfOrNull { it.meaningfulBuildTextOrNull() }
}

private val blockedBuildTexts = setOf(
    "untitled keyboard",
    "untitled",
    "keyboard build",
    "unknown",
    "null",
    "none",
    "n/a",
    "na",
    "미입력",
    "없음",
    "정보 없음",
    "빌드 정보 미입력",
)
