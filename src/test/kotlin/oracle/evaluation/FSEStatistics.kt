package oracle.evaluation

import com.intellij.ml.llm.template.extractfunction.EFSettingType
import com.intellij.ml.llm.template.extractfunction.EFSettings
import com.intellij.ml.llm.template.extractfunction.EMHeuristic
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


class FSEStatistics(val mongoManager: MongoManager, val args: EvaluationArgs) :
    LightPlatformCodeInsightTestCase() {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private var git: Git? = null
    private val projectPath = ""
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
//        val temperatureKey = "temperature_${args.llmArgs.temperature}".replace(".", "_")
        val temperatureKey = "temperature_${args.llmArgs.temperature}"
        val maxExperiments = 30

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

            var codeSnippet = readCodeSnippet(filename, oracleHfLineStart, oracleHfLineEnd)
            codeSnippet = addLineNumbersToCodeSnippet(codeSnippet, oracleHfLineStart)
//            val multishotProducer = MultiShotCandidateProducer(project, editor, file)
            val multishotSender = MultishotSender(efLLMRequestProvider, project)

            val llmDataKey = "multishot-gpt3-fse"
            for (experimentNo in 1..maxExperiments) {
                val experimentKey = "experiment_$experimentNo"
                println("running experiment: $experimentKey")

                val llmResponseDataList = mutableListOf<LlmMultishotResponseData>()
                if (doc.containsKey(llmDataKey)) {
                    val multishotDataDoc = doc.get(llmDataKey) as Document
                    if (multishotDataDoc.containsKey(temperatureKey)) {
                        val temperatureDoc = multishotDataDoc.get(temperatureKey) as Document
                        if (temperatureDoc.containsKey(experimentKey)) {
                            val llmRespList = temperatureDoc.getList(experimentKey, Document::class.java) ?: emptyList()
                            llmResponseDataList.addAll(MongoCandidateAdapter.mongo2LLMMultishotResponseData(llmRespList))
                        }
                    }
                }

                if (llmResponseDataList.size < maxShots) {
                    // add missing shots
                    println("Existing shots: ${llmResponseDataList.size}, desired shots: $maxShots => prompting LLM for missing shots")
                    llmResponseDataList.addAll(
                        multishotSender.sendRequest(
                            data = codeSnippet,
                            existingShots = llmResponseDataList.map { it.shotNo },
                            maxShots = maxShots
                        )
                    )
                }

                val llmDataArrayDoc = MongoCandidateAdapter.llmMultishotResponseData2Mongo(llmResponseDataList)
//                val llmDataDoc = (doc.get(llmDataKey) ?: Document()) as Document
                if (doc.containsKey(llmDataKey)) {
                    val llmDataDoc = doc.get(llmDataKey) as Document
                    if (llmDataDoc.containsKey(temperatureKey)) {
                        val temperatureDocument = llmDataDoc.get(temperatureKey) as Document
                        temperatureDocument.append(experimentKey, llmDataArrayDoc)
                    }
                }
                else {
                    doc.append(llmDataKey, Document(temperatureKey, Document(experimentKey, llmDataArrayDoc)))
                }

                if (args.mongoArgs.updateDocs) {
                    println("updating document: ${docId}")
                    mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), Document("\$set", doc))
                }
            }

            rollbackLocalFile(filename, args.repoArgs, git)
        }

        mongoManager.close()
    }
}



class FSEStatisticsEval(val mongoManager: MongoManager, val args: EvaluationArgs) :
    LightPlatformCodeInsightTestCase() {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private var git: Git? = null
    private val projectPath = ""
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
        val temperatureKey = "temperature_${args.llmArgs.temperature}".replace(".", "_")
        val maxExperiments = 30
        val llmDataKey = "multishot-gpt3-fse"

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

            for (experimentNo in 1..maxExperiments) {
                configureByFile(filename)
                val multishotProducer = MultiShotCandidateProducer(project, editor, file)

                val experimentKey = "experiment_$experimentNo"

                val llmResponseDataList = mutableListOf<LlmMultishotResponseData>()
                if (doc.containsKey(llmDataKey)) {
                    val multishotDataDoc = doc.get(llmDataKey) as Document
                    if (multishotDataDoc.containsKey(temperatureKey)) {
                        val temperatureDoc = multishotDataDoc.get(temperatureKey) as Document
                        if (temperatureDoc.containsKey(experimentKey)) {
                            val lst = temperatureDoc.getList(experimentKey, Document::class.java) ?: emptyList()
                            llmResponseDataList.addAll(MongoCandidateAdapter.mongo2LLMMultishotResponseData(lst))
                        }
                    }
                }

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

                val rankingResultsDoc = Document()
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

                val emAssistRankingKey = "emassist_ranking"

                val emAssistRankingDoc = doc.getOrPut(emAssistRankingKey) { Document() } as Document
                val rankingLlmDataDoc = emAssistRankingDoc.getOrPut(llmDataKey) { Document() } as Document
                val rankingTemperatureDoc = rankingLlmDataDoc.getOrPut(temperatureKey) { Document() } as Document
                rankingTemperatureDoc.append(experimentKey, rankingResultsDoc)

                if (args.mongoArgs.updateDocs) {
                    println("updating document: ${docId}")
                    mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), Document("\$set", doc))
                }

                rollbackLocalFile(filename, args.repoArgs, git)
            }

        }
        mongoManager.close()
    }
}


class TestEvaluationOnOracleProcessorFse : LightPlatformCodeInsightTestCase() {
    fun `test EvaluationOnOracleProcessorFse`() {
        val xuDatasetArgs = EvaluationArgs(
            repoArgs = RepoArgs(
                repoPath = "",
                repoBranch = "",
                repoType = RepoType.LOCAL_FILE
            ),
            mongoArgs = MongoArgs(
                db = "ef_evaluation/revisited_xu_dataset_fse",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
                mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dc0ba2708d3e16a01d0fb9"))
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
                temperature = 0.6,
            )
        )


        FSEStatistics(MongoManager.FromConnectionString(xuDatasetArgs.mongoArgs.db), xuDatasetArgs).process()
    }


    fun `test FSEStatisticsEval`() {
        val xuDatasetArgs = EvaluationArgs(
            repoArgs = RepoArgs(
                repoPath = "",
                repoBranch = "",
                repoType = RepoType.LOCAL_FILE
            ),
            mongoArgs = MongoArgs(
                db = "ef_evaluation/revisited_xu_dataset_fse",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
//                mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dc0ba2708d3e16a01d0fb9"))
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
                temperature = 0.6,
            )
        )

        EFSettings.instance.reset()
            .addHeuristic(EMHeuristic.IF_BODY)
            .addHeuristic(EMHeuristic.PREV_ASSIGNMENT)
            .addHeuristic(EMHeuristic.KEEP_ADJUSTED_CANDIDATE_ONLY)
//            .addHeuristic(EMHeuristic.MAX_METHOD_LOC_THRESHOLD)
            .addSetting(EFSettingType.MULTISHOT_LEARNING)
//            .addThresholdValue(EMHeuristic.MAX_METHOD_LOC_THRESHOLD, 0.88)

        FSEStatisticsEval(MongoManager.FromConnectionString(xuDatasetArgs.mongoArgs.db), xuDatasetArgs).process()
    }
}
