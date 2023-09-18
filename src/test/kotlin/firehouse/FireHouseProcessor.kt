package firehouse

import com.intellij.ml.llm.template.evaluation.HostFunctionData
import com.intellij.ml.llm.template.evaluation.OracleData
import com.intellij.ml.llm.template.extractfunction.*
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.LlmMultishotResponseData
import com.intellij.ml.llm.template.models.MultishotSender
import com.intellij.ml.llm.template.utils.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import utilities.*

data class FirehouseArgs(
    val repoArgs: RepoArgs,
    val mongoArgs: MongoArgs,
    val llmArgs: LLMArgs
)

data class REMSSuggestion(
    val docId: String,
    val efSuggestions: List<EFSuggestion>
)

class FireHouseProcessor(val mongoManager: MongoManager, val args: FirehouseArgs) : LightPlatformCodeInsightTestCase() {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private val tempDownloadPath = "your/path/to/download/tmp/files"
    private var git: Git? = null
    private val projectPath = ""

    override fun getTestDataPath(): String {
        return projectPath
    }

    init {
        Registry.get("llm.for.code.enable.mock.requests").setValue(false)
    }

    fun process() {
        val docs = mongoManager.collection.find(args.mongoArgs.mongoFilter).limit(args.mongoArgs.mongoDocsFetchLimit)
        val maxShots = args.llmArgs.maxShots
        val temperatureKey = "temperature_${args.llmArgs.temperature}"

        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            val oracle = OracleData(
                hostFunctionData = HostFunctionData(
                    lineStart = doc.getInteger("start_line"),
                    lineEnd = doc.getInteger("end_line"),
                    bodyLoc = doc.getInteger("hf_loc"),
                    githubUrl = doc.getString("github_url")
                ),
                loc = -1,
                lineStart = 0,
                lineEnd = 0,
                filename = doc.getString("remote_filename")
            )
            val oracleHfLineStart = oracle.hostFunctionData.lineStart
            val oracleHfLineEnd = oracle.hostFunctionData.lineEnd
            val githubUrl = oracle.hostFunctionData.githubUrl
            val commitHash = doc.getString("commit_hash") ?: ""

            // configure project
            val filename = configureLocalFile(args.repoArgs, oracle, commitHash, git, tempDownloadPath)
            if (filename.isEmpty()) {
                println("could not create filename")
                return@forEach
            }
            configureByFile(filename)

            val llmDataKey =
                if (EFSettings.instance.has(EFSettingType.MULTISHOT_LEARNING)) "llm_multishot_data" else "llm_singleshot_data"

            var codeSnippet = readCodeSnippet(filename, oracleHfLineStart, oracleHfLineEnd)
            codeSnippet = addLineNumbersToCodeSnippet(codeSnippet, oracleHfLineStart)

            val multishotProducer = MultiShotCandidateProducer(project, editor, file)
            val multishotSender = MultishotSender(efLLMRequestProvider, project)

            val llmResponseDataList = mutableListOf<LlmMultishotResponseData>()
            if (doc.containsKey(llmDataKey)) {
                val multishotDataDoc = doc.get(llmDataKey) as Document
                val lst = multishotDataDoc.getList(temperatureKey, Document::class.java) ?: emptyList()
                llmResponseDataList.addAll(MongoCandidateAdapter.mongo2LLMMultishotResponseData(lst))
            }

            // add missing shots
            llmResponseDataList.addAll(
                multishotSender.sendRequest(
                    data = codeSnippet,
                    existingShots = llmResponseDataList.map { it.shotNo },
                    maxShots = maxShots
                )
            )


            val llmDataArrayDoc = MongoCandidateAdapter.llmMultishotResponseData2Mongo(llmResponseDataList)
            val llmDataDoc = (doc.get(llmDataKey) ?: Document()) as Document
            llmDataDoc.append(temperatureKey, llmDataArrayDoc)
            doc.append(llmDataKey, llmDataDoc)

            val multishotCandidates = multishotProducer.buildMultishotCandidates(llmResponseDataList)
            val candidates = multishotCandidates.flatMap { it.efCandidates }.toList()
            val llmProcessingTime = llmResponseDataList.sumOf { it.processingTime }
            val jetGptProcessingTime = multishotCandidates.sumOf { it.jetGPTProcessingTime }

            val rankedByPopularity = EFCandidateUtils.rankByPopularity(candidates)
            val rankedByHeat = EFCandidateUtils.rankByHeat(candidates, oracle.hostFunctionData)
            val rankedBySize = EFCandidateUtils.rankBySize(candidates)
            val rankedByOverlap = EFCandidateUtils.rankByOverlap(candidates)

            var rankedByPopularityDoc =
                MongoCandidateAdapter.adaptRankedCandidateList(rankedByPopularity, multishotCandidates)
            rankedByPopularityDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedByPopularityDoc, githubUrl)
            var rankedByHeatDoc = MongoCandidateAdapter.adaptRankedCandidateList(rankedByHeat, multishotCandidates)
            rankedByHeatDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedByHeatDoc, githubUrl)
            var rankedBySizeDoc = MongoCandidateAdapter.adaptRankedCandidateList(rankedBySize, multishotCandidates)
            rankedBySizeDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedBySizeDoc, githubUrl)
            var rankedByOverlapDoc =
                MongoCandidateAdapter.adaptRankedCandidateList(rankedByOverlap, multishotCandidates)
            rankedByOverlapDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedByOverlapDoc, githubUrl)

            enrichWithExtractedCodeSnippet(filename, rankedByHeat, rankedByHeatDoc)

            val rankingDoc = Document()
                .append("rank_by_size", rankedBySizeDoc)
                .append("rank_by_popularity", rankedByPopularityDoc)
                .append("rank_by_heat", rankedByHeatDoc)
                .append("rank_by_overlap", rankedByOverlapDoc)
                .append(
                    "processing_time",
                    Document()
                        .append("llm_processing_time", llmProcessingTime)
                        .append("jetgpt_processing_time", jetGptProcessingTime)
                        .append("total_processing_time", llmProcessingTime + jetGptProcessingTime)
                )
            val shotTypeKey = buildShotKey()
            val jetgptRankingDoc = (doc.get("jetgpt_ranking") ?: Document()) as Document
            val shotTypeDoc = (jetgptRankingDoc.get(shotTypeKey) ?: Document()) as Document
            shotTypeDoc.append(temperatureKey, rankingDoc)
            jetgptRankingDoc.append(shotTypeKey, shotTypeDoc)
            doc.append("jetgpt_ranking", jetgptRankingDoc)

            if (args.mongoArgs.updateDocs) {
                println("updating document: ${docId}")
                mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), Document("\$set", doc))
            }

            rollbackLocalFile(filename, args.repoArgs, git)
        }

        mongoManager.close()
    }

    private fun enrichWithExtractedCodeSnippet(filename: String, candidates: List<EFCandidate>, docCandidates: List<Document>): List<Document> {
        for (idx in 0..candidates.size-1) {
            val extractedCodeSnippet = getExtractedCodeSnippet(candidates[idx], project, editor, file)
            docCandidates[idx].append("extracted_code_snippet", extractedCodeSnippet)
            configureByFile(filename)
        }

        return docCandidates
    }

    private fun switchToBranch(git: Git, branchName: String) {
        git.checkout().setForced(true).setName(branchName).call()
        git.reset().setMode(ResetCommand.ResetType.HARD).call()
    }

    private fun buildShotKey(): String {
        return if (EFSettings.instance.has(EFSettingType.MULTISHOT_LEARNING)) "multishot" else "singleshot"
    }
}


class FirehouseProcessorWithREMS(val mongoManager: MongoManager, val args: FirehouseArgs) :
    LightPlatformCodeInsightTestCase() {
    private val tempDownloadPath = "your/path/to/download/tmp/files"
    val git: Git? = null
    private val projectPath = ""

    override fun getTestDataPath(): String {
        return projectPath
    }

    init {
        Registry.get("llm.for.code.enable.mock.requests").setValue(false)
    }

    fun run(remsSuggestions: List<REMSSuggestion>) {
        remsSuggestions.forEach { remsSuggestion ->
            val doc =
                mongoManager.collection.find(Filters.eq("_id", org.bson.types.ObjectId(remsSuggestion.docId))).first()
            if (doc == null) {
                println("could not find document: ${remsSuggestion.docId}")
                return@forEach
            }

            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            val oracle = OracleData(
                hostFunctionData = HostFunctionData(
                    lineStart = doc.getInteger("start_line"),
                    lineEnd = doc.getInteger("end_line"),
                    bodyLoc = doc.getInteger("hf_loc"),
                    githubUrl = doc.getString("github_url")
                ),
                loc = -1,
                lineStart = 0,
                lineEnd = 0,
                filename = doc.getString("remote_filename")
            )
            val commitHash = doc.getString("commit_hash") ?: ""
            // configure project
            val filename = configureLocalFile(args.repoArgs, oracle, commitHash, git, tempDownloadPath)
            if (filename.isEmpty()) {
                println("could not create filename")
                return@forEach
            }
            configureByFile(filename)

            val candidateApplicationObserver = EFCandidatesApplicationTelemetryObserver()
            val candidates = EFCandidateFactory().buildDistinctCandidates(remsSuggestion.efSuggestions, editor, file)
            candidates.forEach { candidate -> isCandidateExtractable(candidate, editor, file, listOf(candidateApplicationObserver)) }

            val mongoCandidateDocs = mutableListOf<Document>()
            candidateApplicationObserver.getData().forEach { payload ->
                val mongoCandidateDoc = MongoCandidateAdapter.adaptCandidate(payload.candidate, payload)
                val extractedCodeSnippet = getExtractedCodeSnippet(payload.candidate, project, editor, file)
                mongoCandidateDoc.append("extracted_code_snippet", extractedCodeSnippet)
                mongoCandidateDocs.add(mongoCandidateDoc)
                configureByFile(filename)
            }
            if (args.mongoArgs.updateDocs) {
                doc.append("rems_candidates", mongoCandidateDocs)

                println("updating document: ${docId}")
                mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), Document("\$set", doc))
            }
            rollbackLocalFile(filename, args.repoArgs, git)
        }

        mongoManager.close()
    }
}

class TestFirehouseProcessor : LightPlatformCodeInsightTestCase() {
    val firehouseArgs = FirehouseArgs(
        repoArgs = RepoArgs(
            repoType = RepoType.ONLINE_GITHUB
        ),
        mongoArgs = MongoArgs(
            db = "dbName/collectionName",
            updateDocs = true,
            mongoDocsFetchLimit = 0,
            mongoFilter = org.bson.Document(),
//            mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dc0ba2708d3e16a01d0fc4"))
//                mongoFilter = Filters.and(
//                    Filters.gt("oracle.loc", 1),
//                    Document(
//                        "\$expr",
//                        Document(
//                            "\$gt", listOf("\$oracle.hf_body_loc", "\$oracle.loc")
//                        )
//                    )
//                )
        ),
        llmArgs = LLMArgs(
            maxShots = 5,
            temperature = 1.0,
        )
    )

    fun `test FirehouseProcessor JetGPT`() {
        EFSettings.instance
            .add(EFSettingType.IF_BLOCK_HEURISTIC)
            .add(EFSettingType.PREV_ASSIGNMENT_HEURISTIC)
            .add(EFSettingType.MULTISHOT_LEARNING)

        FireHouseProcessor(MongoManager.FromConnectionString(firehouseArgs.mongoArgs.db), firehouseArgs).process()
    }

    fun `test Firehouse with REMS`() {
        val remsSuggestions = listOf(
            REMSSuggestion(
                docId = "64f21f6627a544013cde9c57",
                efSuggestions = listOf(
                    EFSuggestion(
                        functionName = "",
                        lineStart = 63,
                        lineEnd = 67
                    )
                )
            )
        )
        FirehouseProcessorWithREMS(MongoManager.FromConnectionString(firehouseArgs.mongoArgs.db), firehouseArgs).run(
            remsSuggestions
        )
    }
}
