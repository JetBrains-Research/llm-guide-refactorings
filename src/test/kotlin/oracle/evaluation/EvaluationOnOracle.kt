package oracle.evaluation

import com.intellij.ml.llm.template.extractfunction.EFSettingType
import com.intellij.ml.llm.template.extractfunction.EFSettings
import com.intellij.ml.llm.template.extractfunction.EMHeuristic
import com.intellij.ml.llm.template.extractfunction.MultiShotCandidateProducer
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.LlmMultishotResponseData
import com.intellij.ml.llm.template.utils.EFCandidateUtils
import com.intellij.ml.llm.template.utils.MongoCandidateAdapter
import com.intellij.ml.llm.template.utils.MongoManager
import com.intellij.ml.llm.template.utils.readCodeSnippet
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
    private val tempDownloadPath = "path/to/tmp/file/donwload"

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
                if (args.llmArgs.model.isNotEmpty())
                    "multishot-${args.llmArgs.model}"
                else
                    if (EFSettings.instance.hasSetting(EFSettingType.MULTISHOT_LEARNING)) "llm_multishot_data" else "llm_singleshot_data"

            var codeSnippet = readCodeSnippet(filename, oracleHfLineStart, oracleHfLineEnd)
//            codeSnippet = addLineNumbersToCodeSnippet(codeSnippet, oracleHfLineStart)

            val multishotProducer = MultiShotCandidateProducer(project, editor, file)
//            val multishotSender = MultishotSender(efLLMRequestProvider, project)

            val llmResponseDataList = mutableListOf<LlmMultishotResponseData>()
            if (doc.containsKey(llmDataKey)) {
                val multishotDataDoc = doc.get(llmDataKey) as Document
                val lst = multishotDataDoc.getList(temperatureKey, Document::class.java) ?: emptyList()
                llmResponseDataList.addAll(MongoCandidateAdapter.mongo2LLMMultishotResponseData(lst))
            }

            // add missing shots
//            llmResponseDataList.addAll(
//                multishotSender.sendRequest(
//                    data = codeSnippet,
//                    existingShots = llmResponseDataList.map { it.shotNo },
//                    maxShots = maxShots
//                )
//            )

//            val llmDataArrayDoc = MongoCandidateAdapter.llmMultishotResponseData2Mongo(llmResponseDataList)
//            val llmDataDoc = (doc.get(llmDataKey) ?: Document()) as Document
//            llmDataDoc.append(temperatureKey, llmDataArrayDoc)
//            doc.append(llmDataKey, llmDataDoc)

            val multishotCandidates = multishotProducer.buildMultishotCandidates(llmResponseDataList)
            val candidates = multishotCandidates.flatMap { it.efCandidates }.toList()
            val llmProcessingTime = llmResponseDataList.sumOf { it.processingTime }
            val jetGptProcessingTime = multishotCandidates.sumOf { it.jetGPTProcessingTime }

            val rankedByPopularity = EFCandidateUtils.rankByPopularity(candidates)
            val rankedByHeat = EFCandidateUtils.rankByHeat(candidates, oracle.hostFunctionData)
            val rankedBySize = EFCandidateUtils.rankBySize(candidates)
            val rankedByOverlap = EFCandidateUtils.rankByOverlap(candidates)
            val rankedByPopAndHeat = EFCandidateUtils.rankByPopAndHeat(candidates, oracle.hostFunctionData)

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
            var rankedByPopAndHeatDoc =
                MongoCandidateAdapter.adaptRankedCandidateList(rankedByPopAndHeat, multishotCandidates)
            rankedByPopAndHeatDoc = MongoCandidateAdapter.enrichWithGithubUrl(rankedByPopAndHeatDoc, githubUrl)

            val usedHeuristicsDoc = Document()
                .append("if_body", EFSettings.instance.hasHeuristic(EMHeuristic.IF_BODY))
                .append("prev_statement", EFSettings.instance.hasHeuristic(EMHeuristic.PREV_ASSIGNMENT))
                .append(
                    "keep_adjusted_only",
                    EFSettings.instance.hasHeuristic(EMHeuristic.KEEP_ADJUSTED_CANDIDATE_ONLY)
                )
                .append(
                    "max_selection_threshold",
                    EFSettings.instance.getThresholdValue(EMHeuristic.MAX_METHOD_LOC_THRESHOLD) ?: 0
                )
                .append(
                    "min_selection_threshold",
                    EFSettings.instance.getThresholdValue(EMHeuristic.MIN_METHOD_LOC_THRESHOLD) ?: 0
                )
                .append("multishot_learning", EFSettings.instance.hasSetting(EFSettingType.MULTISHOT_LEARNING))

            val rankingDoc = Document()
                .append("rank_by_size", rankedBySizeDoc)
                .append("rank_by_popularity", rankedByPopularityDoc)
                .append("rank_by_heat", rankedByHeatDoc)
                .append("rank_by_popularity_times_heat", rankedByPopAndHeatDoc)
                .append("rank_by_overlap", rankedByOverlapDoc)
                .append("settings", usedHeuristicsDoc)
                .append(
                    "processing_time",
                    Document()
                        .append("llm_processing_time", llmProcessingTime)
                        .append("jetgpt_processing_time", jetGptProcessingTime)
                        .append("total_processing_time", llmProcessingTime + jetGptProcessingTime)
                )
//            val shotTypeKey = buildShotKey()
            val jetgptRankingDoc = (doc.get("jetgpt_ranking") ?: Document()) as Document
            val shotTypeDoc = (jetgptRankingDoc.get(llmDataKey) ?: Document()) as Document
            shotTypeDoc.append(temperatureKey, rankingDoc)
            jetgptRankingDoc.append(llmDataKey, shotTypeDoc)
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
        if (args.llmArgs.model.isNotEmpty())
            return "multishot-${args.llmArgs.model}"
        return if (EFSettings.instance.hasSetting(EFSettingType.MULTISHOT_LEARNING)) "multishot" else "singleshot"
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
                db = "ef_evaluation/revisited_xu_dataset",
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
                db = "RefactoringMiner/SilvaDataset",
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

        val corenlpDatasetArgs = EvaluationOnOracleProcessorArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB,
            ),
            mongoArgs = MongoArgs(
                db = "RefactoringMiner/CoreNLP_dataset",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
//                    Filters.ne("oracle.manually_marked", true),
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


        val fixPointArgs = EvaluationOnOracleProcessorArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB
            ),
            mongoArgs = MongoArgs(
                db = "ef_evaluation_abhiram/paper_examples",
                updateDocs = true,
                mongoDocsFetchLimit = 1,
                mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dead4abe9ffd545ddd67a2")),
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
                model = "gpt-3"
            )
        )

        val extendedCorpusArgs = EvaluationOnOracleProcessorArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB
            ),
            mongoArgs = MongoArgs(
                db = "playground_refminer/extended_corpus",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
//                mongoFilter = Document(),
//                mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dead4abe9ffd545ddd67a2")),
                mongoFilter = Filters.exists("jetgpt_ranking", false),
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

        EFSettings.instance.reset()
            .addHeuristic(EMHeuristic.IF_BODY)
            .addHeuristic(EMHeuristic.PREV_ASSIGNMENT)
            .addHeuristic(EMHeuristic.KEEP_ADJUSTED_CANDIDATE_ONLY)
            .addHeuristic(EMHeuristic.MAX_METHOD_LOC_THRESHOLD)
            .addSetting(EFSettingType.MULTISHOT_LEARNING)
            .addThresholdValue(EMHeuristic.MAX_METHOD_LOC_THRESHOLD, 0.88)

//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(xuDatasetArgs.mongoArgs.db), xuDatasetArgs).process()
//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(silvaDatasetArgs.mongoArgs.db), silvaDatasetArgs).process()
//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(ijceDatasetArgs.mongoArgs.db), ijceDatasetArgs).process()
//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(corenlpDatasetArgs.mongoArgs.db), corenlpDatasetArgs).process()
//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(gpt4Args.mongoArgs.db), gpt4Args).process()
//        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(fixPointArgs.mongoArgs.db), fixPointArgs).process()
        EvaluationOnOracleProcessor(MongoManager.FromConnectionString(extendedCorpusArgs.mongoArgs.db), extendedCorpusArgs).process()
    }


    fun `test run jetgpt on gpt4 data`() {
        EFSettings.instance.reset()
            .addHeuristic(EMHeuristic.IF_BODY)
            .addHeuristic(EMHeuristic.PREV_ASSIGNMENT)
            .addHeuristic(EMHeuristic.KEEP_ADJUSTED_CANDIDATE_ONLY)
            .addHeuristic(EMHeuristic.MAX_METHOD_LOC_THRESHOLD)
            .addSetting(EFSettingType.MULTISHOT_LEARNING)
            .addThresholdValue(EMHeuristic.MAX_METHOD_LOC_THRESHOLD, 0.88)

        val temperatures = listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
        temperatures.forEach { temperature ->
            println("running for temperature: $temperature")

            val gpt4Args = EvaluationOnOracleProcessorArgs(
                repoArgs = RepoArgs(
                    repoType = RepoType.LOCAL_FILE
                ),
                mongoArgs = MongoArgs(
                    db = "ef_evaluation_abhiram/xu_dataset_gpt4",
                    updateDocs = true,
                    mongoDocsFetchLimit = 0,
//            mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dc0ba2708d3e16a01d0fc4"))
                    mongoFilter = Filters.and(
                        Filters.gt("oracle.loc", 1),
//                        Filters.exists("jetgpt_ranking", false),
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
                    temperature = temperature,
                    model = "gpt-4"
                )
            )

            EvaluationOnOracleProcessor(MongoManager.FromConnectionString(gpt4Args.mongoArgs.db), gpt4Args).process()
        }
    }
}
