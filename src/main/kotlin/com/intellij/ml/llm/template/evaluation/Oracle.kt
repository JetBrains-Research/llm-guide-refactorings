package com.intellij.ml.llm.template.evaluation

data class OracleData (
    val hostFunctionData: HostFunctionData,
    val loc: Int,
    val lineStart: Int,
    val lineEnd: Int,
    val filename: String,
)

data class HostFunctionData (
    val lineStart: Int,
    val lineEnd: Int,
    val bodyLoc: Int,
    val githubUrl: String = ""
)