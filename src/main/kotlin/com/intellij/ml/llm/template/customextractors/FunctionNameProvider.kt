package com.intellij.ml.llm.template.customextractors

data class FunctionName(val name: String)
class FunctionNameProvider(private val functionName: String) {
    fun getFunctionName(): FunctionName {
        return FunctionName(functionName)
    }
}
