import kotlin.math.max

/*
 * This is a dynamic programming implementation of rod cutting problem.
 * @Params price- array of prices of all possible cut sizes of rod of array length
 * @Return maximum value obtained by cutting rod
 * */
fun rodCutting(price: IntArray): Int {
    val value = IntArray(price.size + 1)
    value[0] = 0

    for (i in 1..price.size) {
        var maxVal = Int.MIN_VALUE
        for (j in 0 until i) maxVal = max(maxVal,
            price[j] + value[i - j - 1])
        value[i] = maxVal
    }
    return value[price.size]
}

fun someRandomFunction(x: Int)
{
    val y = x + 1
    val sum = y + x
    return sum
}

fun randomFunction2(): Int {
    val x = 2
    val prod = x*x
    return prod }

fun randomFunction3(): Int
{   val x = 2
    val prod = x*x
    return prod }


private fun MockProject.replaceReflektQueries(
    config: PluginConfig,
    instancesAnalyzer: IrInstancesAnalyzer,
    libraryArgumentsWithInstances: LibraryArgumentsWithInstances,
) {
    // Extract reflekt arguments from external libraries
    IrGenerationExtension.registerExtension(
        this,
        ExternalLibraryInstancesCollectorExtension(
            irInstancesAnalyzer = instancesAnalyzer,
            irInstancesFqNames = libraryArgumentsWithInstances.instances,
        ),
    )
    generateReflektImpl(config, instancesAnalyzer, libraryArgumentsWithInstances)

    IrGenerationExtension.registerExtension(
        this,
        SmartReflektIrGenerationExtension(
            irInstancesAnalyzer = instancesAnalyzer,
            classpath = config.dependencyJars,
            messageCollector = config.messageCollector,
        ),
    )
}

private fun checkLocalClass(options: ExtractOptions) {
    val container: PsiElement? = PsiTreeUtil.getParentOfType(options.elements.first(), PsiMember::class.java)
    val analyzer = CodeFragmentAnalyzer(options.elements)
    val localClasses = findLocalClassesIn(container)
    fun isExtracted(element: PsiElement): Boolean {
        return element.textRange in TextRange(options.elements.first().textRange.startOffset, options.elements.last().textRange.endOffset)
    }
    for (localClass in localClasses) {
        val classExtracted: Boolean = isExtracted(localClass)
        val extractedReferences = Collections.synchronizedList(ArrayList<PsiElement>())
        val remainingReferences = Collections.synchronizedList(ArrayList<PsiElement>())
        ReferencesSearch.search(localClass).forEach(Processor { psiReference: PsiReference ->
            val element = psiReference.element
            val elementExtracted: Boolean = isExtracted(element)
            if (elementExtracted && !classExtracted) {
                extractedReferences.add(element)
                return@Processor false
            }
            if (!elementExtracted && classExtracted) {
                remainingReferences.add(element)
                return@Processor false
            }
            true
        })
        if (extractedReferences.isNotEmpty()) {
            throw ExtractException(JavaRefactoringBundle.message("extract.method.error.class.outside.defined"), extractedReferences)
        }
        if (remainingReferences.isNotEmpty()) {
            throw ExtractException(JavaRefactoringBundle.message("extract.method.error.class.outside.used"), remainingReferences)
        }
        if (classExtracted) {
            analyzer.findUsedVariablesAfter()
                .filter { isExtracted(it) && PsiUtil.resolveClassInType(it.type) === localClass }
                .forEach { throw ExtractException(JavaRefactoringBundle.message("extract.method.error.class.outside.used"), it) }
        }
    }
}
