package com.intellij.ml.llm.template.extractfunction

import com.google.gson.annotations.SerializedName

enum class EfCandidateType(s: String) {
    AS_IS("AS_IS"),
    ADJUSTED("ADJUSTED"),
    INVALID("INVALID")
}
data class EFCandidate(
    @SerializedName("functionName")
    var functionName: String,

    @SerializedName("offsetStart")
    var offsetStart: Int,

    @SerializedName("offsetEnd")
    var offsetEnd: Int,

    @SerializedName("lineStart")
    var lineStart: Int,

    @SerializedName("lineEnd")
    var lineEnd: Int,
) {
    @SerializedName("ef_suggestion")
    lateinit var efSuggestion: EFSuggestion

    @SerializedName("type")
    lateinit var type: EfCandidateType

    @SerializedName("popularity")
    var popularity: Int = -1

    @SerializedName("heat")
    var heat: Int = -1

    @SerializedName("overlap")
    var overlap: Int = 0

    @SerializedName("heuristic")
    var heuristic = ""

    @SerializedName("length")
    val length = lineEnd - lineStart + 1

    fun isValid(): Boolean {
        return type != EfCandidateType.INVALID
    }
}