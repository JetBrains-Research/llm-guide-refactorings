package com.intellij.ml.llm.template.extractfunction

enum class EFSettingType(s: String) {
    IF_BLOCK_HEURISTIC("DIALOG"),
    PREV_ASSIGNMENT_HEURISTIC("PARENT_CLASS"),
    VERY_LARGE_BLOCK_HEURISTIC("VERY_LARGE_BLOCK"),
    MULTISHOT_LEARNING("MULTISHOT_LEARNING")
}

class EFSettings {
    private val settings = mutableSetOf<EFSettingType>()

    companion object {
        val instance: EFSettings by lazy { EFSettings() }
    }

    fun add(settingType: EFSettingType): EFSettings {
        settings.add(settingType)
        return this
    }

    fun has(settingType: EFSettingType) : Boolean {
        return settings.contains(settingType)
    }
}