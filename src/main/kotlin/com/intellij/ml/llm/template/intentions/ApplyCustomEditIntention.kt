package com.intellij.ml.llm.template.intentions

import com.intellij.ml.llm.template.LLMBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class ApplyCustomEditIntention : ApplyTransformationIntention() {
    override fun getInstruction(project: Project, editor: Editor): String? {
        return Messages.showInputDialog(project, "Enter prompt:", "Codex", null)
    }

    override fun getText(): String = LLMBundle.message("intentions.apply.custom.edit.name")
    override fun getFamilyName(): String = text
}
