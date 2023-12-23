package com.intellij.ml.llm.template.ui

import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.MethodExtractionType
import com.intellij.ml.llm.template.customextractors.FunctionNameProvider
import com.intellij.ml.llm.template.customextractors.MyMethodExtractor
import com.intellij.ml.llm.template.telemetry.EFTelemetryDataElapsedTimeNotificationPayload
import com.intellij.ml.llm.template.telemetry.EFTelemetryDataManager
import com.intellij.ml.llm.template.telemetry.EFTelemetryDataUtils
import com.intellij.ml.llm.template.telemetry.TelemetryDataAction
import com.intellij.ml.llm.template.utils.CodeTransformer
import com.intellij.ml.llm.template.utils.EFNotification
import com.intellij.ml.llm.template.utils.Observable
import com.intellij.ml.llm.template.utils.PsiUtils
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
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
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
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
        highlighter: AtomicReference<ScopeHighlighter>,
        efTelemetryDataManager: EFTelemetryDataManager? = null
) : Observable() {
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
    private val myEFTelemetryDataManager = efTelemetryDataManager
    private val logger = Logger.getInstance("#com.intellij.ml.llm")
    private var prevSelectedCandidateIndex = 0

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
                            doExtractMethod(selectedRow)
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
                    doExtractMethod(selectedRow)
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
            myEditor.scrollingModel.scrollTo(LogicalPosition(candidate.lineStart, 0), ScrollType.CENTER)

            // compute elapsed time
            notifyObservers(EFNotification(EFTelemetryDataElapsedTimeNotificationPayload(TelemetryDataAction.STOP, prevSelectedCandidateIndex)))
            notifyObservers(EFNotification(EFTelemetryDataElapsedTimeNotificationPayload(TelemetryDataAction.START, extractFunctionCandidateTable.selectedRow)))
            prevSelectedCandidateIndex = extractFunctionCandidateTable.selectedRow
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
        methodSignaturePreview.minimumSize = Dimension(500, 120)
        methodSignaturePreview.preferredSize = Dimension(500, 150)
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
                        .applyToComponent { minimumSize = JBDimension(100, 120) }
            }

            row {
                button(LLMBundle.message("ef.candidates.popup.extract.function.button.title"), actionListener = {
                    doExtractMethod(myExtractFunctionsCandidateTable.selectedRow)
                }).comment(
                        LLMBundle.message(
                                "ef.candidates.popup.invoke.extract.function",
                                KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ExtractMethod"))
                        )
                )
                button("Exit", actionListener = {
                    myPopup?.cancel()
                })
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

        // Add the return type if it's not a constructor
        if (!psiMethod.isConstructor) {
//            builder.append(psiMethod.returnType?.canonicalText ?: "void")
            builder.append("${psiMethod.returnType?.presentableText ?: "void"} ")
        }

        // Add the method name
        builder.append(psiMethod.name)

        // Add the parameters
        builder.append("(")
        psiMethod.parameterList.parameters.joinTo(
                buffer = builder,
                separator = ", \n\t"
        ) { "${it.type.presentableText} ${it.name}" }
        builder.append(")")


        // Add function body
        builder.append(" {\n\t...\n}")

        return builder.toString()
    }

    private fun generateFunctionSignature(efCandidate: EFCandidate): String {
        var signature = LLMBundle.message("ef.candidates.popup.cannot.compute.function.signature")
        when (myFile.language) {
            JavaLanguage.INSTANCE ->
                MyMethodExtractor(FunctionNameProvider(efCandidate.functionName)).findAndSelectExtractOption(
                        myProject,
                        myEditor,
                        myFile,
                        TextRange(efCandidate.offsetStart, efCandidate.offsetEnd),
                        MethodExtractionType.PARENT_CLASS
                )?.thenApply { options ->
                    val elementsToReplace = MethodExtractor().prepareRefactoringElements(options)
                    elementsToReplace.method.setName(efCandidate.functionName)
                    signature = generateFunctionSignature(elementsToReplace.method)
                }

            KotlinLanguage.INSTANCE -> {
                fun computeKotlinFunctionSignature(
                        functionName: String,
                        file: KtFile,
                        elements: List<PsiElement>,
                        targetSibling: PsiElement
                ): @Nls String {
                    var kotlinSignature = LLMBundle.message("ef.candidates.popup.cannot.compute.function.signature")
                    val extractionData = ExtractionData(file, elements.toRange(false), targetSibling)
                    try {
                        val analysisResult = extractionData.performAnalysis()
                        if (analysisResult.status == AnalysisResult.Status.SUCCESS) {
                            val config = ExtractionGeneratorConfiguration(
                                    analysisResult.descriptor!!,
                                    ExtractionGeneratorOptions(
                                            inTempFile = true,
                                            target = ExtractionTarget.FUNCTION,
                                            dummyName = functionName,
                                    )
                            )
                            kotlinSignature = config.getDeclarationPattern().replace(Regex("[\\w.]+${efCandidate.functionName}"), efCandidate.functionName)
                            val regex = Regex("\\b(\\w+)(?:\\.\\w+)+\\b")
                            kotlinSignature = kotlinSignature.replace(regex) { matchResult ->
                                matchResult.value.substringAfterLast('.')
                            }
                            kotlinSignature = kotlinSignature.replace(",", ",\n\t")
                            kotlinSignature = kotlinSignature.replace("$0", "\t...")
                        }
                    } catch (t: Throwable) {
                        logger.error("Error computing signature for candidate:\n$efCandidate\n")
                        logger.error(t)
                    }

                    return kotlinSignature
                }

                val elements = myFile.elementsInRange(TextRange(efCandidate.offsetStart, efCandidate.offsetEnd))
                if (elements.isNotEmpty()) {
                    val targetSibling = PsiUtils.getParentFunctionOrNull(elements[0], myFile.language)
                    if (targetSibling != null) {
                        signature = computeKotlinFunctionSignature(
                                efCandidate.functionName,
                                myFile as KtFile,
                                elements,
                                targetSibling
                        )
                    }
                }
            }
        }
        return signature
    }

    private fun doExtractMethod(index: Int) {
        notifyObservers(EFNotification(EFTelemetryDataElapsedTimeNotificationPayload(TelemetryDataAction.STOP, prevSelectedCandidateIndex)))
        addSelectionToTelemetryData(index)
        val efCandidate = myCandidates[index]
        myPopup!!.cancel()
        val runnable = Runnable {
            myCodeTransformer.applyCandidate(efCandidate, myProject, myEditor, myFile)
        }
        runnable.run()
    }

    private fun addSelectionToTelemetryData(index: Int) {
        val efCandidate = myCandidates[index]
        val hostFunctionTelemetryData = myEFTelemetryDataManager?.getData()?.hostFunctionTelemetryData
        myEFTelemetryDataManager?.addUserSelectionTelemetryData(
                EFTelemetryDataUtils.buildUserSelectionTelemetryData(
                        efCandidate,
                        index,
                        hostFunctionTelemetryData,
                        myFile
                )
        )
    }
}