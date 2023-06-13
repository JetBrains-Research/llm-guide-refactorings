package com.intellij.ml.llm.template.ui

import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.models.FunctionNameProvider
import com.intellij.ml.llm.template.models.MyMethodExtractor
import com.intellij.ml.llm.template.utils.CodeTransformer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.ui.MethodSignatureComponent
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel


class ExtractFunctionPanel(
    project: Project,
    editor: Editor,
    file: PsiFile,
    candidates: List<EFCandidate>,
    codeTransformer: CodeTransformer,
    highlighter: AtomicReference<ScopeHighlighter>
) {
    val myExtractFunctionsCandidateTable: JBTable
    private val myExtractFunctionsScrollPane: JBScrollPane
    private val myProject: Project = project
    private val myMethodSignaturePreview: MethodSignatureComponent
    private val myCandidates = candidates
    private val myEditor = editor
    private var myPopup: JBPopup? = null
    private val myCodeTransformer = codeTransformer
    private val myFile = file
    private val myHighlighter = highlighter

    init {
        val tableModel = buildTableModel(myCandidates)
        val candidateSignatureMap = buildCandidateSignatureMap(myCandidates)
        myMethodSignaturePreview = buildMethodSignaturePreview()
        myExtractFunctionsCandidateTable = buildExtractFunctionCandidateTable(tableModel, candidateSignatureMap)
        myExtractFunctionsScrollPane = buildExtractFunctionScrollPane()
    }

    private fun buildCandidateSignatureMap(candidates: List<EFCandidate>): Map<EFCandidate, String> {
        val candidateSignatureMap: MutableMap<EFCandidate, String> = mutableMapOf()

        candidates.forEach { candidate ->
            candidateSignatureMap[candidate] = generateFunctionSignature(candidate)
        }

        return candidateSignatureMap
    }

    private fun buildExtractFunctionCandidateTable(
        tableModel: DefaultTableModel,
        candidateSignatureMap: Map<EFCandidate, String>
    ): JBTable {
        val extractFunctionCandidateTable = object : JBTable(tableModel) {
            override fun processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        if (!isEditing && e.modifiersEx == 0) {
                            doExtractMethod(myCandidates[selectedRow])
                        }
                    }
                    e.consume()
                    return true
                }
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        myPopup?.cancel()
                    }
                }
                return super.processKeyBinding(ks, e, condition, pressed)
            }

            override fun processMouseEvent(e: MouseEvent?) {
                if (e != null && e.clickCount == 2) {
                    doExtractMethod(myCandidates[selectedRow])
                }
                super.processMouseEvent(e)
            }
        }
        extractFunctionCandidateTable.minimumSize = Dimension(-1, 100)
        extractFunctionCandidateTable.tableHeader = null

        extractFunctionCandidateTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        extractFunctionCandidateTable.selectionModel.addListSelectionListener {
            val candidate = myCandidates[extractFunctionCandidateTable.selectedRow]
            myEditor.selectionModel.setSelection(candidate.offsetStart, candidate.offsetEnd)

            myMethodSignaturePreview.setSignature(candidateSignatureMap[candidate])
            val scopeHighlighter: ScopeHighlighter = myHighlighter.get()
            scopeHighlighter.dropHighlight()
            val range = TextRange(candidate.offsetStart, candidate.offsetEnd)
            scopeHighlighter.highlight(com.intellij.openapi.util.Pair(range, listOf(range)))
        }
        extractFunctionCandidateTable.selectionModel.setSelectionInterval(0, 0)
        extractFunctionCandidateTable.cellEditor = null

        extractFunctionCandidateTable.columnModel.getColumn(0).maxWidth = 50
        extractFunctionCandidateTable.columnModel.getColumn(1).cellRenderer = FunctionNameTableCellRenderer()
        extractFunctionCandidateTable.setShowGrid(false)

        return extractFunctionCandidateTable
    }

    private fun buildExtractFunctionScrollPane(): JBScrollPane {
        val extractFunctionsScrollPane = JBScrollPane(myExtractFunctionsCandidateTable)

        extractFunctionsScrollPane.border = JBUI.Borders.empty()
        extractFunctionsScrollPane.maximumSize = Dimension(500, 100)

        return extractFunctionsScrollPane
    }

    private fun buildTableModel(candidates: List<EFCandidate>): DefaultTableModel {
        val columnNames = arrayOf("Function Length", "Function Name")
        val model = object : DefaultTableModel() {
            override fun getColumnClass(column: Int): Class<*> {
                // Return the class that corresponds to the specified column.
                return if (column == 0) String::class.java else Integer::class.java
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                // Makes the cells in the table non-editable
                return false
            }
        }
        model.setColumnIdentifiers(columnNames)
        candidates.forEach { efCandidate ->
            val functionLength = efCandidate.lineEnd - efCandidate.lineStart + 1
            val functionName = String.format("${efCandidate.functionName}()")
            model.addRow(arrayOf(functionLength, functionName))
        }
        return model
    }

    private fun buildMethodSignaturePreview(): MethodSignatureComponent {
        val methodSignaturePreview =
            MethodSignatureComponent("", myProject, com.intellij.ide.highlighter.JavaFileType.INSTANCE)
        methodSignaturePreview.isFocusable = false
        methodSignaturePreview.minimumSize = Dimension(500, 200)
        methodSignaturePreview.preferredSize = Dimension(500, 200)
        methodSignaturePreview.maximumSize = Dimension(500, 200)

        return methodSignaturePreview
    }

    fun createPanel(): JComponent {
        val popupPanel = panel {
            row {
                cell(myExtractFunctionsScrollPane).align(AlignX.FILL)
            }

            row {
                cell(myMethodSignaturePreview)
                    .align(AlignX.FILL)
                    .applyToComponent { minimumSize = JBDimension(100, 100) }
            }

            row {
                button(LLMBundle.message("ef.candidates.popup.extract.function.button.title"), actionListener = {
                    myPopup?.closeOk(null)
                    doExtractMethod(myCandidates[myExtractFunctionsCandidateTable.selectedRow])
                }).comment(
                    LLMBundle.message(
                        "ef.candidates.popup.invoke.extract.function",
                        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ExtractMethod"))
                    )
                )
            }
        }
        popupPanel.preferredFocusedComponent = myExtractFunctionsCandidateTable
        return popupPanel
    }

    fun setDelegatePopup(jbPopup: JBPopup) {
        myPopup = jbPopup
    }

    private fun generateFunctionSignature(psiMethod: PsiMethod): String {
        val builder = StringBuilder()

        // Add the method name
        builder.append(psiMethod.name)

        // Add the parameters
        builder.append("(")
        psiMethod.parameterList.parameters.joinTo(
            builder,
            separator = ", \n\t"
        ) { "${it.type.presentableText} ${it.name}" }
        builder.append(")")

        // Add the return type if it's not a constructor
        if (!psiMethod.isConstructor) {
            builder.append(": ${psiMethod.returnType?.presentableText ?: "Unit"}")
        }

        // Add function body
        builder.append(" {\n\t...\n}")

        return builder.toString()
    }


    private fun generateFunctionSignature(efCandidate: EFCandidate): String {
        var signature = ""
        MyMethodExtractor(FunctionNameProvider(efCandidate.functionName)).findAndSelectExtractOption(
            myEditor,
            myFile,
            TextRange(efCandidate.offsetStart, efCandidate.offsetEnd)
        )?.thenApply { options ->
            val elementsToReplace = MethodExtractor().prepareRefactoringElements(options)
            elementsToReplace.method.setName(efCandidate.functionName)
            signature = generateFunctionSignature(elementsToReplace.method)
        }
        return signature
    }

    fun doExtractMethod(efCandidate: EFCandidate) {
        myPopup!!.cancel()
        val runnable = Runnable {
            myCodeTransformer.applyCandidate(efCandidate, myProject, myEditor, myFile)
        }
        runnable.run()
    }
}