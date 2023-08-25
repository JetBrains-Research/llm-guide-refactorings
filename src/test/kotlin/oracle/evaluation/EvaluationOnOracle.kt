package oracle.evaluation

import com.intellij.ml.llm.template.extractfunction.EFSettingType
import com.intellij.ml.llm.template.extractfunction.EFSettings
import com.intellij.ml.llm.template.extractfunction.MultiShotCandidateProducer
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.LlmMultishotResponseData
import com.intellij.ml.llm.template.models.MultishotSender
import com.intellij.ml.llm.template.utils.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.model.Filters
import org.bson.Document

data class EvaluationOnOracleProcessorArgs(
    val db: String,
    val updateDocs: Boolean
)

class EvaluationOnOracleProcessor(val mongoManager: MongoManager, val args: EvaluationOnOracleProcessorArgs) :
    LightPlatformCodeInsightTestCase() {

    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private val projectPath = ""

    override fun getTestDataPath(): String {
        return projectPath
    }

    init {
        Registry.get("llm.for.code.enable.mock.requests").setValue(false) // or another value type
    }

    fun process() {
//        val filter = Filters.eq("_id", org.bson.types.ObjectId("64dc0bd154151567a94bf6c0"))
//        val filter = Filters.eq("_id", org.bson.types.ObjectId("64dc0bd154151567a94bf6be"))
        val filter = Filters.and(
            Filters.gt("oracle.loc", 1),
            Document(
                "\$expr",
                Document(
                    "\$gt", listOf("\$oracle.hf_body_loc", "\$oracle.loc")
                )
            )
        )

        val limit = 0
        val docs = mongoManager.collection.find(filter).limit(limit)
        val maxShots = 5

        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            val githubUrl = (doc.get("host_function_before_ef") as Document).getString("url")

            val oracleDoc = doc.get("oracle") as Document
            val filename = oracleDoc.getString("filename")
            val lineStart = oracleDoc.getInteger("line_start")
            val lineEnd = oracleDoc.getInteger("line_end")

            // configure project
            configureByFile(filename)

            var codeSnippet = readCodeSnippet(filename, lineStart, lineEnd)
            codeSnippet = addLineNumbersToCodeSnippet(codeSnippet, lineStart)

            val multishotProducer = MultiShotCandidateProducer(project, editor, file)
            val multishotSender = MultishotSender(efLLMRequestProvider, project)

            var llmMultishotResponseDataList = emptyList<LlmMultishotResponseData>()
            if (doc.containsKey("llm_multishot_data")) {
                val lst = doc.getList("llm_multishot_data", Document::class.java)
                llmMultishotResponseDataList = MongoCandidateAdapter.mongo2LLMMultishotResponseData(lst)
            } else {
                llmMultishotResponseDataList = multishotSender.sendRequest(codeSnippet, maxShots)
                val llmMultishotDataDoc =
                    MongoCandidateAdapter.llmMultishotResponseData2Mongo(llmMultishotResponseDataList)
                doc.append("llm_multishot_data", llmMultishotDataDoc)
            }

            val multishotCandidates = multishotProducer.buildMultishotCandidates(llmMultishotResponseDataList)
            val candidates = multishotCandidates.flatMap { it.efCandidates }.toList()
            val llmProcessingTime = llmMultishotResponseDataList.sumOf { it.processingTime }
            val jetGptProcessingTime = multishotCandidates.sumOf { it.jetGPTProcessingTime }

            val rankedByPopularity = EFCandidateUtils.rankByPopularity(candidates)
            val rankedByHeat = EFCandidateUtils.rankByHeat(candidates)
            val rankedBySize = EFCandidateUtils.rankBySize(candidates)

            var rankedByPopularityDoc =
                MongoCandidateAdapter.adaptRankedCandidateList(rankedByPopularity, multishotCandidates)
            rankedByPopularityDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedByPopularityDoc, githubUrl)
            var rankedByHeatDoc = MongoCandidateAdapter.adaptRankedCandidateList(rankedByHeat, multishotCandidates)
            rankedByHeatDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedByHeatDoc, githubUrl)
            var rankedBySizeDoc = MongoCandidateAdapter.adaptRankedCandidateList(rankedBySize, multishotCandidates)
            rankedBySizeDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedBySizeDoc, githubUrl)

            val jetgptRankingDoc = Document()
                .append("rank_by_size", rankedBySizeDoc)
                .append("rank_by_popularity", rankedByPopularityDoc)
                .append("ranked_by_heat", rankedByHeatDoc)
                .append(
                    "processing_time",
                    Document()
                        .append("llm_processing_time", llmProcessingTime)
                        .append("jetgpt_processing_time", jetGptProcessingTime)
                        .append("total_processing_time", llmProcessingTime + jetGptProcessingTime)
                )
            doc.append("jetgpt_ranking", jetgptRankingDoc)

            if (args.updateDocs) {
                println("updating document: ${docId}")
                mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), Document("\$set", doc))
            }
        }

        mongoManager.close()
    }
}

class TestEvaluationOnOracleProcessor : LightPlatformCodeInsightTestCase() {
    fun `test EvaluationOnOracleProcessor`() {
        val args = EvaluationOnOracleProcessorArgs(
            db = "ef_evaluation/revisited_xu_dataset",
//            repoPath = "/Users/dpomian/hardwork/research/jetbrains/extract_method_firehouse/githubclones/JetBrains/intellij-community",
//            repoBranch = "master",
            updateDocs = true
        )
        EFSettings.instance
            .add(EFSettingType.IF_BLOCK_HEURISTIC)
//            .add(EFSettingType.PREV_ASSIGNMENT_HEURISTIC)
        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(args.db), args).process()
    }
}
