package evaluation

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.MethodExtractionType
import com.intellij.ml.llm.template.utils.CodeTransformer
import com.intellij.ml.llm.template.utils.EFCandidatesApplicationTelemetryObserver
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.io.BufferedReader
import java.io.InputStreamReader

internal fun fetchDocuments(
    mongoClient: MongoClient,
    dbName: String,
    collectionName: String,
    filters: Bson,
    limit: Int
): FindIterable<Document> {
    val mongoCollection = mongoClient
        .getDatabase(dbName)
        .getCollection(collectionName)
    val docs = mongoCollection
        .find(filters)
        .limit(limit)
    return docs
}

internal fun buildMongodbFilters(
    minSimilarityScore: Double,
    minEfLoc: Int,
    minHfLoc: Int
): Bson {
    return Filters.and(
        Filters.gte("similarityAnalysis.similarity_score", minSimilarityScore),
        Filters.gt("similarityAnalysis.extracted_function_body_loc", minEfLoc),
        Filters.gte("similarityAnalysis.host_function_body_loc", minHfLoc),
        Filters.exists("jetgpt_analysis", false),
        Filters.exists("similarityAnalysis", true),
        Document(
            "\$expr",
            Document(
                "\$gt",
                listOf(
                    "\$similarityAnalysis.host_function_body_loc",
                    "\$similarityAnalysis.extracted_function_body_loc"
                )
            )
        )
    )
}

internal fun buildMongodbFiltersById(mongoId: String): Bson {
    return Filters.eq("_id", ObjectId(mongoId))
}

internal fun downloadGithubFile(
    repoOwner: String,
    repoName: String,
    repoFilePath: String,
    githubToken: String,
    commitHash: String,
    outputFile: String
): Int {
    val pythonScriptPath =
        "/Users/dpomian/hardwork/research/extract_function_research/tools/evaluation/evaluator/github_cli.py"
    val virtualEnvPath = "/Users/dpomian/.venvs/ef_research"
    val command = mutableListOf("$virtualEnvPath/bin/python", pythonScriptPath)
    command.add("dl")
    command.add("-repo-info")
    command.add("$repoOwner/$repoName/$githubToken")
    command.add("-repo-filepath")
    command.add(repoFilePath)
    command.add("-sha")
    command.add(commitHash)
    command.add("-local-path")
    command.add(outputFile)

    val processBuilder = ProcessBuilder(command)
    processBuilder.redirectErrorStream(true) // Merge error and output streams

    val process = processBuilder.start()
    val inputStream = process.inputStream
    val reader = BufferedReader(InputStreamReader(inputStream))

    var line: String?
    while (reader.readLine().also { line = it } != null) {
        println(line)
    }

    val exitCode = process.waitFor() // Wait for the process to complete
    println("Python script exited with code: $exitCode")
    return exitCode
}

internal fun getExtractedCodeSnippet(
    efCandidate: EFCandidate,
    project: Project,
    editor: Editor,
    file: PsiFile
): String {
    var codeSnippet = ""
    val codeTransformer = CodeTransformer(MethodExtractionType.PARENT_CLASS)
    val efCandidatesApplicationTelemetryObserver = EFCandidatesApplicationTelemetryObserver()
    codeTransformer.addObserver(efCandidatesApplicationTelemetryObserver)

    val psiMethodsBefore = PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)

    val candidateAppliedSuccessfully = codeTransformer.applyCandidate(efCandidate, project, editor, file)
    if (candidateAppliedSuccessfully) {
        val psiMethods = PsiTreeUtil.collectElementsOfType(file, PsiMethod::class.java).filter { psiMethod ->
            !psiMethodsBefore.contains(psiMethod)
        }
        codeSnippet = if (!psiMethods.isEmpty()) {
            val psiMethod = psiMethods[0]
            editor.document.getText(TextRange(psiMethod.startOffset, psiMethod.endOffset))
        } else {
            "no method found with name ${efCandidate.functionName}"
        }
    }
    return codeSnippet
}

internal fun rankMongoCandidatesBySize(candidates: List<Document>): List<Document> {
    val rankedCandidates = candidates.sortedByDescending { it.getInteger("length") }
    return rankedCandidates
}

internal fun rankMongoCandidatesByPopularity(candidates: List<Document>): List<Document> {
    val candidatesMap = mutableMapOf<Pair<Int, Int>, MutableList<Document>>()
    candidates.forEach {
        val key = Pair(it.getInteger("line_start"), it.getInteger("line_end"))
        if (candidatesMap.containsKey(key)) {
            candidatesMap[key]!!.add(it)
        } else {
            candidatesMap[key] = mutableListOf(it)
        }
    }

    val uniqueCandidates = mutableListOf<Document>()
    for ((key, candidateList) in candidatesMap) {
        val candidate = candidateList[0]
        candidate.set("popularity", candidateList.size)
        uniqueCandidates.add(candidate)
    }

    val rankedCandidates = uniqueCandidates.sortedByDescending { it.getInteger("popularity") }

    // move one liners at the end
    val (matchingObjects, remainingObjects) = rankedCandidates.partition { it.getInteger("length") == 1 }
    val updatedList = remainingObjects + matchingObjects

    return updatedList
}

internal fun rankMongoCandidatesByHeat(candidates: List<Document>): List<Document> {
    val rankedByPopularity = rankMongoCandidatesByPopularity(candidates)
    for (ci in rankedByPopularity.indices) {
        for (oi in rankedByPopularity.indices) {
            if (oi == ci) continue
            val currentCandidate = rankedByPopularity.get(ci)
            val otherCandidate = rankedByPopularity.get(oi)
            val ciStart = currentCandidate.getInteger("line_start")
            val ciEnd = currentCandidate.getInteger("line_end")
            val oiStart = otherCandidate.getInteger("line_start")
            val oiEnd = otherCandidate.getInteger("line_end")
            val heat = maxOf(0, minOf(ciEnd, oiEnd) - maxOf(ciStart, oiStart) + 1)
            if (currentCandidate.containsKey("heat")) {
                val currentHeat = currentCandidate.getInteger("heat")
                currentCandidate.set("heat", currentHeat + heat)
            }
            else {
                currentCandidate.set("heat", heat)
            }
        }
    }

    val rankedByHeat = rankedByPopularity.sortedByDescending { it.getInteger("heat") }
    return rankedByHeat
}

internal fun removeCandidatesDuplicates(candidates: List<Document>): List<Document> {
    val uniqueCandidates = candidates.distinctBy { it.getInteger("line_start") to it.getInteger("line_end") }
    return uniqueCandidates
}