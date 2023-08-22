package com.intellij.ml.llm.template.intentions

import com.intellij.ml.llm.template.common.LLMBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class ApplyCustomExtractFunctionIntention : ApplyExtractFunctionTransformationIntention() {
    override fun getInstruction(project: Project, editor: Editor): String? {
        return Messages.showInputDialog(project, "Enter prompt:", "Codex", null)
    }

    override fun getText(): String = LLMBundle.message("intentions.apply.extract.function.family.name")
    override fun getFamilyName(): String = text
}
