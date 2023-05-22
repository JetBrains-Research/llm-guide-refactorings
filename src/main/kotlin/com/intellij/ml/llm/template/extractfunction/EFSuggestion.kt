package com.intellij.ml.llm.template.extractfunction

import com.google.gson.annotations.SerializedName

data class EFSuggestionList (
    @SerializedName("suggestion_list")
    val suggestion_list: List<EFSuggestion>
)

data class EFSuggestion(
    @SerializedName("function_name")
    var functionName: String,

    @SerializedName("line_start")
    var lineStart: Int,

    @SerializedName("line_end")
    var lineEnd: Int
)
