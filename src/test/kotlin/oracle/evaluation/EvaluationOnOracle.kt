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
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import utilities.*
import java.io.File


data class EvaluationOnOracleProcessorArgs(
    val repoArgs: RepoArgs,
    val mongoArgs: MongoArgs,
    val llmArgs: LLMArgs,
)

class EvaluationOnOracleProcessor(val mongoManager: MongoManager, val args: EvaluationOnOracleProcessorArgs) :
    LightPlatformCodeInsightTestCase() {

    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private val projectPath = ""
    private var git: Git? = null
    private val tempDownloadPath = "your/path/to/download/tmp/files"

    override fun getTestDataPath(): String {
        return projectPath
    }

    init {
        Registry.get("llm.for.code.enable.mock.requests").setValue(false) // or another value type
        when (args.repoArgs.repoType) {
            RepoType.LOCAL_GIT_CLONE -> {
                val repository = RepositoryBuilder().setGitDir(File("${args.repoArgs.repoPath}/.git")).build()
                git = Git(repository)
            }

            else -> {}
        }
    }

    fun process() {
        val docs = mongoManager.collection.find(args.mongoArgs.mongoFilter).limit(args.mongoArgs.mongoDocsFetchLimit)
        val maxShots = args.llmArgs.maxShots
        val temperatureKey = "temperature_${args.llmArgs.temperature}"

        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            val oracle = MongoCandidateAdapter.mongo2Oracle(doc)
            val oracleHfLineStart = oracle.hostFunctionData.lineStart
            val oracleHfLineEnd = oracle.hostFunctionData.lineEnd
            val githubUrl = oracle.hostFunctionData.githubUrl
            val commitHash = doc.getString("sha_before_ef") ?: ""

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
            var rankedByOverlapDoc = MongoCandidateAdapter.adaptRankedCandidateList(rankedByOverlap, multishotCandidates)
            rankedByOverlapDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedByOverlapDoc, githubUrl)

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



    private fun buildShotKey(): String {
        return if (EFSettings.instance.has(EFSettingType.MULTISHOT_LEARNING)) "multishot" else "singleshot"
    }
}

class TestEvaluationOnOracleProcessor : LightPlatformCodeInsightTestCase() {
    fun `test EvaluationOnOracleProcessor`() {
        val xuDatasetArgs = EvaluationOnOracleProcessorArgs(
            repoArgs = RepoArgs(
                repoPath = "",
                repoBranch = "",
                repoType = RepoType.LOCAL_FILE
            ),
            mongoArgs = MongoArgs(
                db = "dbName/collectionName",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
//            mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dc0ba2708d3e16a01d0fc4"))
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
                    Document(
                        "\$expr",
                        Document(
                            "\$gt", listOf("\$oracle.hf_body_loc", "\$oracle.loc")
                        )
                    )
                )
            ),
            llmArgs = LLMArgs(
                maxShots = 5,
                temperature = 1.0,
            )
        )

        val ijceDatasetArgs = EvaluationOnOracleProcessorArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB,
            ),
            mongoArgs = MongoArgs(
                db = "playground_refminer/ijce",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
                    Document(
                        "\$expr",
                        Document(
                            "\$gt", listOf("\$oracle.hf_body_loc", "\$oracle.loc")
                        )
                    )
                )
            ),
            llmArgs = LLMArgs(
                maxShots = 5,
                temperature = 1.0,
            ),
        )

        val silvaDatasetArgs = EvaluationOnOracleProcessorArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB,
            ),
            mongoArgs = MongoArgs(
                db = "dbName/collectionName",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
                    Filters.ne("oracle.manually_marked", true),
                    Document(
                        "\$expr",
                        Document(
                            "\$gt", listOf("\$oracle.hf_body_loc", "\$oracle.loc")
                        )
                    )
                )
            ),
            llmArgs = LLMArgs(
                temperature = 1.0,
                maxShots = 5,
            ),
        )

        val corenlpArgs = EvaluationOnOracleProcessorArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB,
            ),
            mongoArgs = MongoArgs(
                db = "dbName/collectionName",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
                    Filters.ne("oracle.manually_marked", true),
                    Document(
                        "\$expr",
                        Document(
                            "\$gt", listOf("\$oracle.hf_body_loc", "\$oracle.loc")
                        )
                    )
                )
            ),
            llmArgs = LLMArgs(
                temperature = 1.0,
                maxShots = 5,
            ),
        )

        EFSettings.instance
            .add(EFSettingType.IF_BLOCK_HEURISTIC)
            .add(EFSettingType.PREV_ASSIGNMENT_HEURISTIC)
            .add(EFSettingType.MULTISHOT_LEARNING)
            .add(EFSettingType.VERY_LARGE_BLOCK_HEURISTIC)

//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(xuDatasetArgs.mongoArgs.db), xuDatasetArgs).process()
//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(ijceDatasetArgs.mongoArgs.db), ijceDatasetArgs).process()
//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(silvaDatasetArgs.mongoArgs.db), silvaDatasetArgs).process()
        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(corenlpArgs.mongoArgs.db), corenlpArgs).process()
    }
}
