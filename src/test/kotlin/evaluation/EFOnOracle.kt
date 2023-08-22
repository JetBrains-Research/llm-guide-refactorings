package evaluation

import com.intellij.ml.llm.template.common.extractfunction.EFCandidate
import com.intellij.ml.llm.template.common.extractfunction.EfCandidateType
import com.intellij.ml.llm.template.common.models.LLMBaseResponse
import com.intellij.ml.llm.template.common.utils.EFApplicationResult
import com.intellij.ml.llm.template.common.utils.EFCandidateApplicationPayload
import com.intellij.ml.llm.template.common.utils.EFCandidatesApplicationTelemetryObserver
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.conversions.Bson
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.RepositoryBuilder
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.io.File


data class EFSimpleCandidate(
    var offsetStart: Int,
    var offsetEnd: Int,
    var lineStart: Int,
    var lineEnd: Int,
) {
    companion object {
        fun fromEFCandidate(efCandidate: EFCandidate): EFSimpleCandidate {
            return EFSimpleCandidate(
                offsetStart = efCandidate.offsetStart,
                offsetEnd = efCandidate.offsetEnd,
                lineStart = efCandidate.lineStart,
                lineEnd = efCandidate.lineEnd
            ).also { it.functionName = efCandidate.functionName }
        }

        fun fromEFCandidateApplicationPayload(payload: EFCandidateApplicationPayload): EFSimpleCandidate {
            return EFSimpleCandidate(
                offsetStart = payload.candidate.offsetStart,
                offsetEnd = payload.candidate.offsetEnd,
                lineStart = payload.candidate.lineStart,
                lineEnd = payload.candidate.lineEnd
            ).also {
                it.functionName = payload.candidate.functionName
                it.candidateType = payload.candidate.type
                it.applicationResult = payload.result
                it.applicationResultReason = payload.reason
            }
        }
    }

    lateinit var functionName: String
    lateinit var candidateType: EfCandidateType
    lateinit var applicationResult: EFApplicationResult
    lateinit var applicationResultReason: String
}

data class EFTry(
    val efCandidates: List<EFCandidate>,
    val efCandidateApplicationPayloadList: List<EFCandidateApplicationPayload>,
    val llmRawResponse: LLMBaseResponse?,
    val llmProcessingTime: Long,
    val jetGPTProcessingTime: Long,
    val tryNumber: Int
)

class EFOnOracle : LightPlatformCodeInsightTestCase() {
    private var mongoClient: MongoClient = MongoClients.create("mongodb://localhost:27017")
    private var projectPath = ""
    private val minSimilarityScore = 0.09
    private val minEfLoc = 1
    private val minHfLoc = 3

    override fun getTestDataPath(): String {
        return projectPath
    }

    fun `test extract function with JetGPT on Oracle`() {
//        val dbName = "RefactoringMiner"
//        val collectionName = "JB__IntelliJ_Community__Processed_FromFile2"
        val dbName = "playground_refminer"
        val collectionName = "ijce"
        val limit = 25
        val repositoryPath = "/Users/dpomian/hardwork/research/jetbrains/intellij-community"
        val branchName = "master"
        val mongoCollection = mongoClient.getDatabase(dbName).getCollection(collectionName)
        val filters = Filters.and(
            Filters.exists("oracle", true),
            Filters.gt("oracle.loc", 1),
            Filters.exists("jetgpt_analysis", false),
            Document(
                "\$expr",
                Document(
                    "\$gt", listOf("\$oracle.hf_body_loc", "\$oracle.loc")
                )
            )
        )
        val docs = fetchDocuments(mongoClient, dbName, collectionName, filters, limit)
        val repository = RepositoryBuilder().setGitDir(File("$repositoryPath/.git")).build()
        val jetGPTCandidatesProducer = JetGPTCandidatesProducer()
        val git = Git(repository)

        // switch to main branch
        switchToBranch(git, branchName)
        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")
            val commitHash = doc.getString("sha_before_ef")
            val filename = "$repositoryPath/${(doc.get("host_function_before_ef") as Document).getString("filename")}"
            val githubUrl = (doc.get("host_function_before_ef") as Document).getString("url")
            val (lineStart, lineEnd) = extractLinesFromGithubUrl(githubUrl)

            // checkout repo to commit hash
            git.checkout().setName(commitHash).call()

            // configure project
            configureByFile(filename)

            val triesMap = buildCandidates(jetGPTCandidatesProducer, filename, lineStart, lineEnd, docId)
            buildTryBreakdown(triesMap, githubUrl, filename, mongoCollection, docId!!)
//            buildRankBySize(triesMap, githubUrl, filename, mongoCollection, docId!!)
//            buildRankByPopularity(triesMap, githubUrl, filename, mongoCollection, docId!!)
            buildTotalResponse(triesMap, githubUrl, filename, mongoCollection, docId)

            // switch back to main
            switchToBranch(git, branchName)
        }

        mongoClient.close()
    }


    fun `test extract function with JetGPT on Oracle GitHub`() {
        val dbName = "RefactoringMiner"
        val collectionName = "SilvaDataset"
        val limit = 0
//        val filters = buildMongodbFiltersById("64d3f01da9a592ad0895a53b")
        val filters: Bson = buildMongodbFilters(minSimilarityScore, minEfLoc, minHfLoc)
        val docs = fetchDocuments(mongoClient, dbName, collectionName, filters, limit)
        val mongoCollection = mongoClient.getDatabase(dbName).getCollection(collectionName)
        val jetGPTCandidatesProducer = JetGPTCandidatesProducer()

        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            val repoOwner = doc.getString("repo_owner")
            val repoName = doc.getString("repo_name")
            val commitHash = doc.getString("sha_before_ef")
            val repoFilePath = (doc.get("host_function_before_ef") as Document).getString("filename")
            val githubUrl = (doc.get("host_function_before_ef") as Document).getString("url")
            val (lineStart, lineEnd) = extractLinesFromGithubUrl(githubUrl)

            val githubToken = "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"
            val outputFileName = repoFilePath.split("/").last()
            val outputFile =
                "/Users/dpomian/hardwork/research/jetbrains/llm-guide-refactorings/src/test/testdata/$outputFileName"
            val returnCode = downloadGithubFile(
                repoOwner = repoOwner,
                repoName = repoName,
                repoFilePath = repoFilePath,
                githubToken = githubToken,
                commitHash = commitHash,
                outputFile = outputFile
            )
//            val returnCode = 0
            if (returnCode == 0) {
                configureByFile(outputFile)

                val triesMap = buildCandidates(jetGPTCandidatesProducer, outputFile, lineStart, lineEnd, docId)
                buildTryBreakdown(triesMap, githubUrl, outputFile, mongoCollection, docId!!)
                buildTotalResponse(triesMap, githubUrl, outputFile, mongoCollection, docId)
            } else {
                println("Download process failed for doc: $docId")
            }
        }

    }

    fun `test extract function with JetGPT on Xu dataset`() {
        val dbName = "ef_evaluation"
        val collectionName = "xu_dataset"
        val limit = 0
//        val filters = buildMongodbFiltersById("64d3f01da9a592ad0895a53b")
        val filters: Bson = buildMongodbFilters(minSimilarityScore, minEfLoc, minHfLoc)
        val docs = fetchDocuments(mongoClient, dbName, collectionName, filters, limit)
        val mongoCollection = mongoClient.getDatabase(dbName).getCollection(collectionName)
        val jetGPTCandidatesProducer = JetGPTCandidatesProducer()

        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            val outputFile = doc.getString("local_filename")
            val githubUrl = (doc.get("host_function_before_ef") as Document).getString("url")

            val (lineStart, lineEnd) = extractLinesFromGithubUrl(githubUrl)

            configureByFile(outputFile)
            val triesMap = buildCandidates(jetGPTCandidatesProducer, outputFile, lineStart, lineEnd, docId)
            buildTryBreakdown(triesMap, githubUrl, outputFile, mongoCollection, docId!!)
            buildTotalResponse(triesMap, githubUrl, outputFile, mongoCollection, docId)
        }
    }

    fun `test enrich Xu dataset with ranking`() {
        val dbName = "ef_evaluation"
        val collectionName = "xu_dataset"
        val limit = 0
//        val filters = buildMongodbFiltersById("64dc0ba2708d3e16a01d0fb9")
        val filters = Filters.exists("jetgpt_ranking", true)
        val docs = fetchDocuments(mongoClient, dbName, collectionName, filters, limit)
        val mongoCollection = mongoClient.getDatabase(dbName).getCollection(collectionName)
        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            val totalCandidates = mutableListOf<Document>()
            val jetgptAnalysisProperties = listOf(
                "jetgpt_analysis_1",
                "jetgpt_analysis_2",
                "jetgpt_analysis_3",
                "jetgpt_analysis_4",
                "jetgpt_analysis_5"
            )

            var llmProcessingTime = 0L
            var jetgptProcessingTime = 0L
            jetgptAnalysisProperties.forEach {
                var jetgptAnalysisDoc = doc.get(it)
                if (jetgptAnalysisDoc != null) {
                    jetgptAnalysisDoc = jetgptAnalysisDoc as Document
                    val candidates = jetgptAnalysisDoc.getList("suggestions", Document::class.java)
                    totalCandidates.addAll(candidates)
                    llmProcessingTime += jetgptAnalysisDoc.getLong("llm_processing_time")
                    jetgptProcessingTime += jetgptAnalysisDoc.getLong("jetgpt_processing_time")
                }
            }
            val totalProcessingTime = llmProcessingTime + jetgptProcessingTime

            // keep only OK candidates
            val okCandidates = totalCandidates.filter { it.getString("application_result") == "OK" }.toList()

            // remove duplicates
            val uniqueOkCandidates = removeCandidatesDuplicates(okCandidates)

            // rank candidates by size
            val rankedBySize = rankMongoCandidatesBySize(uniqueOkCandidates)

            // rank candidates by popularity
            val rankedByPopularity = rankMongoCandidatesByPopularity(okCandidates)

            // rank candidates by heatmap
            val rankedByHeat = rankMongoCandidatesByHeat(okCandidates)

            val rankBySizeDoc = Document()
                .append("suggestions", rankedBySize)
                .append("processing_time", Document("llm_processing_time", llmProcessingTime).append("jetgpt_processing_time", jetgptProcessingTime).append("total_processing_time", totalProcessingTime))
                .append("llmRawResponse", null)

            val rankByPopularityDoc = Document()
                .append("suggestions", rankedByPopularity)
                .append("processing_time", Document("llm_processing_time", llmProcessingTime).append("jetgpt_processing_time", jetgptProcessingTime).append("total_processing_time", totalProcessingTime))
                .append("llmRawResponse", null)

            val rankByHeatDoc = Document()
                .append("suggestions", rankedByHeat)
                .append("processing_time", Document("llm_processing_time", llmProcessingTime).append("jetgpt_processing_time", jetgptProcessingTime).append("total_processing_time", totalProcessingTime))
                .append("llmRawResponse", null)

            val rankingDoc = Document()
                .append("rank_by_size", rankBySizeDoc)
                .append("rank_by_popularity", rankByPopularityDoc)
                .append("rank_by_heat", rankByHeatDoc)

            doc.append("jetgpt_ranking", rankingDoc)
            mongoCollection.updateOne(Filters.eq("_id", doc.get("_id")), Document("\$set", doc))
        }
    }

    fun `test recompute code snippet`() {
        val dbName = "ef_evaluation"
        val collectionName = "xu_dataset"
        val limit = 0
        val filters = Filters.exists("jetgpt_analysis", true)
        val docs = fetchDocuments(mongoClient, dbName, collectionName, filters, limit)
        val mongoCollection = mongoClient.getDatabase(dbName).getCollection(collectionName)

        docs.forEach { doc ->
            println("processing document: ${doc.get("_id").toString()}")
            val jetgptAnalysisDoc: Document = doc.get("jetgpt_analysis") as Document
            val suggestions = jetgptAnalysisDoc.getList("suggestions", Document::class.java)
            suggestions.forEach { suggestion ->
                configureByFile(doc.getString("local_filename"))

                val efCandidate = EFCandidate(
                    functionName = suggestion.getString("function_name"),
                    offsetStart = suggestion.getInteger("offset_start"),
                    offsetEnd = suggestion.getInteger("offset_end"),
                    lineStart = suggestion.getInteger("line_start"),
                    lineEnd = suggestion.getInteger("line_end")
                )
                val codeSnippet = getExtractedCodeSnippet(efCandidate, project, editor, file)
                suggestion.set("extracted_function_snippet", codeSnippet)
                val x = 0
            }
            mongoCollection.replaceOne(Filters.eq("_id", doc.get("_id")), doc)
        }
    }

    private fun switchToBranch(git: Git, branchName: String) {
        git.checkout().setForced(true).setName(branchName).call()
        git.reset().setMode(ResetCommand.ResetType.HARD).call()
    }

    private fun buildResponseAndUpdate(
        candidates: List<EFSimpleCandidate>,
        llmRawResponse: LLMBaseResponse?,
        llmProcessingTime: Long,
        llmTryNumber: Int,
        jetGPTProcessingTime: Long,
        githubUrl: String,
        filename: String,
        mongoCollection: MongoCollection<Document>,
        docId: Any,
        attributeName: String
    ) {
        val jetGptAnalysisDoc = buildJetGPTAnalysisDoc(
            efCandidates = candidates,
            llmRawResponse = llmRawResponse,
            llmTryNumber = llmTryNumber,
            llmProcessingTime = llmProcessingTime,
            jetGPTProcessingTime = jetGPTProcessingTime,
            githubUrl = githubUrl,
            localFileName = filename
        )
        val jetGptAnalysisUpdate = Updates.set(attributeName, jetGptAnalysisDoc)
        mongoCollection.updateOne(eq("_id", docId), jetGptAnalysisUpdate)
    }

    private fun buildTryBreakdown(
        triesMap: Map<Int, EFTry>,
        githubUrl: String,
        filename: String,
        mongoCollection: MongoCollection<Document>,
        docId: Any
    ) {
        triesMap.forEach { eftry ->
            val llmProcessingTime = eftry.value.llmProcessingTime
            val jetGPTProcessingTime = eftry.value.jetGPTProcessingTime
            val llmRawResponse = eftry.value.llmRawResponse
            val efSimpleCandidates = eftry.value.efCandidateApplicationPayloadList.map {
                EFSimpleCandidate.fromEFCandidateApplicationPayload(it)
            }
            val currentTry = eftry.key
            buildResponseAndUpdate(
                candidates = efSimpleCandidates,
                llmRawResponse = llmRawResponse,
                llmProcessingTime = llmProcessingTime,
                llmTryNumber = eftry.key,
                jetGPTProcessingTime = jetGPTProcessingTime,
                githubUrl = githubUrl,
                filename = filename,
                mongoCollection = mongoCollection,
                docId = docId,
                attributeName = "jetgpt_analysis_$currentTry"
            )
        }
    }

    private fun buildTotalResponse(
        triesMap: Map<Int, EFTry>,
        githubUrl: String,
        filename: String,
        mongoCollection: MongoCollection<Document>,
        docId: Any
    ) {
        val candidatesSet = mutableSetOf<EFSimpleCandidate>()

        triesMap.forEach { eftry ->
            candidatesSet.addAll(
                eftry.value.efCandidateApplicationPayloadList
                    .map { EFSimpleCandidate.fromEFCandidateApplicationPayload(it) }
                    .filter { it.applicationResult == EFApplicationResult.OK }
            )
        }

        buildResponseAndUpdate(
            candidates = candidatesSet.toList(),
            llmRawResponse = null,
            llmProcessingTime = triesMap.values.sumByLong { it.llmProcessingTime },
            llmTryNumber = 0,
            jetGPTProcessingTime = triesMap.values.sumByLong { it.jetGPTProcessingTime },
            githubUrl = githubUrl,
            filename = filename,
            mongoCollection = mongoCollection,
            docId = docId,
            attributeName = "jetgpt_analysis"
        )
    }

    private fun buildCandidates(
        jetGPTCandidatesProducer: JetGPTCandidatesProducer,
        filename: String,
        lineStart: Int,
        lineEnd: Int,
        docId: Any?
    ): Map<Int, EFTry> {
        val numberOfTries = 5
        val triesMap: MutableMap<Int, EFTry> = mutableMapOf()
        for (currentTry in 1..numberOfTries) {
            val candidateApplicationObserver = EFCandidatesApplicationTelemetryObserver()
            try {
                val currentCandidates =
                    jetGPTCandidatesProducer.produceCandidates(
                        filename,
                        lineStart,
                        lineEnd,
                        project,
                        editor,
                        file,
                        candidateApplicationObserver
                    ) as List<EFCandidate>
                val efTry = EFTry(
                    efCandidates = currentCandidates,
                    efCandidateApplicationPayloadList = candidateApplicationObserver.getData(),
                    llmRawResponse = jetGPTCandidatesProducer.llmRawResponse,
                    llmProcessingTime = jetGPTCandidatesProducer.llmProcessingTime,
                    jetGPTProcessingTime = jetGPTCandidatesProducer.jetGPTProcessingTime,
                    tryNumber = currentTry
                )
                triesMap.put(currentTry, efTry)
            } catch (e: NullPointerException) {
                System.out.println("error while processing document: $docId")
            }
        }

        return triesMap
    }


    private fun extractLinesFromGithubUrl(url: String): Pair<Int, Int> {
        val pattern = "#L(\\d+)-L(\\d+)".toRegex()
        val matchResult = pattern.find(url)

        return matchResult?.let { match ->
            val (num1, num2) = match.destructured
            Pair(num1.toInt(), num2.toInt())
        }!!
    }


    private fun buildJetGPTAnalysisDoc(
        efCandidates: List<EFSimpleCandidate>,
        llmRawResponse: LLMBaseResponse?,
        llmTryNumber: Int,
        llmProcessingTime: Long,
        jetGPTProcessingTime: Long,
        githubUrl: String,
        localFileName: String
    ): Document {
        var githubUrl = githubUrl
        val suggestions: MutableList<Document> = ArrayList()
        val doc = Document()
        for (efSimpleCandidate in efCandidates) {

            val lineStart: Int = efSimpleCandidate.lineStart
            val lineEnd: Int = efSimpleCandidate.lineEnd
            val length = lineEnd - lineStart + 1
            val offsetStart: Int = efSimpleCandidate.offsetStart
            val offsetEnd: Int = efSimpleCandidate.offsetEnd
            val functionName = efSimpleCandidate.functionName
            githubUrl = githubUrl.replace("#L\\d+-L\\d+".toRegex(), String.format("#L%d-L%d", lineStart, lineEnd))
            githubUrl = githubUrl.replace("R\\d+-R\\d+".toRegex(), String.format("R%d-R%d", lineStart, lineEnd))

            var extractedCodeSnippet = ""
            if (efSimpleCandidate.applicationResult != EFApplicationResult.FAIL) {
                val efCandidate = EFCandidate(
                    efSimpleCandidate.functionName,
                    efSimpleCandidate.offsetStart,
                    efSimpleCandidate.offsetEnd,
                    efSimpleCandidate.lineStart,
                    efSimpleCandidate.lineEnd
                )
                configureByFile(localFileName)
                extractedCodeSnippet = getExtractedCodeSnippet(efCandidate, project, editor, file)
            }
            suggestions.add(
                Document()
                    .append("candidate_type", efSimpleCandidate.candidateType)
                    .append("application_result", efSimpleCandidate.applicationResult)
                    .append("application_reason", efSimpleCandidate.applicationResultReason)
                    .append("function_name", functionName)
                    .append("line_start", lineStart)
                    .append("line_end", lineEnd)
                    .append("length", length)
                    .append("offset_start", offsetStart)
                    .append("offset_end", offsetEnd)
                    .append("github_url", githubUrl)
                    .append("extracted_function_snippet", extractedCodeSnippet)
            )
        }
        doc.append("suggestions", suggestions)
        doc.append("llm_processing_time", llmProcessingTime)
        doc.append("jetgpt_processing_time", jetGPTProcessingTime)
        doc.append("total_processing_time", llmProcessingTime + jetGPTProcessingTime)
        doc.append("try_number", llmTryNumber)
        if (llmRawResponse != null) {
            doc.append("llmRawResponse", llmRawResponse.getSuggestions()[0].text)
        } else {
            doc.append("llmRawResponse", "null")
        }
        return doc
    }

//    private fun getExtractedCodeSnippet(efCandidate: EFCandidate): String {
//        var codeSnippet = ""
//        val codeTransformer = CodeTransformer(MethodExtractionType.PARENT_CLASS)
//        val efCandidatesApplicationTelemetryObserver = EFCandidatesApplicationTelemetryObserver()
//        codeTransformer.addObserver(efCandidatesApplicationTelemetryObserver)
//        val candidateAppliedSuccessfully = codeTransformer.applyCandidate(efCandidate, project, editor, file)
//        if (candidateAppliedSuccessfully) {
//            val psiMethods = PsiTreeUtil.collectElementsOfType(file, PsiMethod::class.java).filter { psiMethod ->
//                psiMethod.name == efCandidate.functionName
//            }
//            codeSnippet = if (!psiMethods.isEmpty()) {
//                val psiMethod = psiMethods[0]
//                editor.document.getText(TextRange(psiMethod.startOffset, psiMethod.endOffset))
//            } else {
//                "no method found with name ${efCandidate.functionName}"
//            }
//        }
//        return codeSnippet
//    }
}