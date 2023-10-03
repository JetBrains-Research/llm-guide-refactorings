package com.intellij.ml.llm.template.extractfunction

enum class EMHeuristic(s: String) {
    IF_BODY("IF_BODY"),
    PREV_ASSIGNMENT("PREVIOUS_ASSIGNMENT"),
    KEEP_ADJUSTED_CANDIDATE_ONLY("KEEP_ADJUSTED_ONLY"),
    MIN_METHOD_LOC_THRESHOLD("MIN_METHOD_LOC_THRESHOLD"),
    MAX_METHOD_LOC_THRESHOLD("MAX_METHOD_LOC_THRESHOLD"),
}

enum class EFSettingType(s: String) {
    MULTISHOT_LEARNING("MULTISHOT_LEARNING")
}

class EFSettings {
    private val settings = mutableSetOf<EFSettingType>()
    private val heuristics = mutableSetOf<EMHeuristic>()
    private val thresholds = mutableMapOf<EMHeuristic, Double>()

    companion object {
        val instance: EFSettings by lazy { EFSettings() }
    }

    fun addSetting(settingType: EFSettingType): EFSettings {
        settings.add(settingType)
        return this
    }

    fun addHeuristic(heuristic: EMHeuristic): EFSettings {
        heuristics.add(heuristic)
        return this
    }

    fun hasSetting(settingType: EFSettingType) : Boolean {
        return settings.contains(settingType)
    }

    fun hasHeuristic(heuristic: EMHeuristic) : Boolean {
        return heuristics.contains(heuristic)
    }

    fun addThresholdValue(heuristic: EMHeuristic, thValue: Double): EFSettings {
        thresholds.put(heuristic, thValue)
        return this
    }

    fun getThresholdValue(heuristic: EMHeuristic): Double? {
        return thresholds.get(heuristic)
    }

    fun reset() : EFSettings {
        settings.clear()
        heuristics.clear()
        thresholds.clear()
        return this
    }
}