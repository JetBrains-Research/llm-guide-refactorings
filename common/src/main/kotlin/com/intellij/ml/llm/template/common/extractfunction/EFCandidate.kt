package com.intellij.ml.llm.template.common.extractfunction

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

    fun isValid(): Boolean {
        return type != EfCandidateType.INVALID
    }
}