package com.intellij.ml.llm.template.models

import com.google.gson.annotations.SerializedName

data class EFSuggestion(
    @SerializedName("function_name")
    var functionName: String,

    @SerializedName("line_start")
    var lineStart: Int,

    @SerializedName("line_end")
    var lineEnd: Int
)
