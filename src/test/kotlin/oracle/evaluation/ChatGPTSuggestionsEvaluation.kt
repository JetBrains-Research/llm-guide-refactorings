package oracle.evaluation

import com.intellij.ml.llm.template.extractfunction.*
import com.intellij.ml.llm.template.models.LlmMultishotResponseData
import com.intellij.ml.llm.template.utils.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import utilities.*
import java.io.File

data class ChatGPTSuggestionsEvaluationArgs(
    val repoArgs: RepoArgs,
    val mongoArgs: MongoArgs,
    val llmArgs: LLMArgs,
)

class ChatGPTSuggestionsEvaluation(val mongoManager: MongoManager, val args: ChatGPTSuggestionsEvaluationArgs) :
    LightPlatformCodeInsightTestCase() {
    private var git: Git? = null
    private val projectPath = ""
    private val tempDownloadPath = "/Users/dpomian/hardwork/research/jetbrains/llm-guide-refactorings/src/test/tmp"

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
        val temperatureKey = "temperature_${args.llmArgs.temperature}"
        docs.forEach { doc ->
            val docId = doc["_id"]
            println("processing document: ${docId.toString()}")

            // configure project
            val oracle = MongoCandidateAdapter.mongo2Oracle(doc)
            val githubUrl = oracle.hostFunctionData.githubUrl
            val commitHash = doc.getString("sha_before_ef") ?: ""

            val filename = configureLocalFile(args.repoArgs, oracle, commitHash, git, tempDownloadPath)
            if (filename.isEmpty()) {
                println("could not create filename")
                return@forEach
            }
            configureByFile(filename)

            val llmDataKey = if (args.llmArgs.model.isNotEmpty()) "multishot-${args.llmArgs.model}" else "llm_multishot_data"

            val llmResponseDataList = mutableListOf<LlmMultishotResponseData>()
            if (doc.containsKey(llmDataKey)) {
                val multishotDataDoc = doc.get(llmDataKey) as Document
                val lst = multishotDataDoc.getList(temperatureKey, Document::class.java) ?: emptyList()
                llmResponseDataList.addAll(MongoCandidateAdapter.mongo2LLMMultishotResponseData(lst))
            }

//            val llmDataArrayDoc = MongoCandidateAdapter.llmMultishotResponseData2Mongo(llmResponseDataList)
//            val llmDataDoc = (doc.get(llmDataKey) ?: Document()) as Document
//            llmDataDoc.append(temperatureKey, llmDataArrayDoc)
//            doc.append(llmDataKey, llmDataDoc)

            // try each candidate
            val applicationTelemetryObserver = EFCandidatesApplicationTelemetryObserver()
            val codeTransofrmer = CodeTransformer(MethodExtractionType.PARENT_CLASS)
            codeTransofrmer.addObserver(applicationTelemetryObserver)
            val groupedData = llmResponseDataList.groupBy { it.shotNo }
            val candidates = mutableListOf<EFCandidate>()
            for ((shotNo, multishotResponseDataList) in groupedData) {
                if (multishotResponseDataList.isEmpty()) continue
                val multishotResponseData = multishotResponseDataList[0]
                val llmRawResponse = multishotResponseData.llmResponse
                if (llmRawResponse == null) continue
                if (llmRawResponse.getSuggestions().isEmpty()) continue
                val llmResponse = llmRawResponse.getSuggestions()[0]
                val efSuggestionList = identifyExtractFunctionSuggestions(llmResponse.text)
                candidates.addAll(
                    EFCandidateFactory().buildCandidates(efSuggestionList.suggestionList, editor, file).toList()
                )
            }
            candidates.distinctBy { listOf(it.lineStart, it.lineEnd) }.forEach { candidate ->
                configureByFile(filename)
                codeTransofrmer.applyCandidate2(candidate, project, editor, file)
            }

            val candidateWithResultList = mutableListOf<Document>()
            applicationTelemetryObserver.getData().forEach { applicationPayload ->
                candidateWithResultList.add(
                    MongoCandidateAdapter.adaptCandidate(
                        applicationPayload.candidate,
                        applicationPayload
                    )
                )
            }
            MongoCandidateAdapter.enrichWithGithubUrl(candidateWithResultList, githubUrl)
            doc.append("suggestion_evaluation", Document(llmDataKey, Document(temperatureKey, candidateWithResultList)))

            if (args.mongoArgs.updateDocs) {
                println("updating document: ${docId}")
                mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), Document("\$set", doc))
            }

            rollbackLocalFile(filename, args.repoArgs, git)
        }

        mongoManager.close()
    }
}

class TestChatGPTSuggestionsEvaluation : LightPlatformCodeInsightTestCase() {
    fun `test ChatGPT suggestion evaluation`() {
        val args = ChatGPTSuggestionsEvaluationArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB
            ),
            mongoArgs = MongoArgs(
                db = "playground_refminer/ijce",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
//                mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dead40be9ffd545ddd65a4"))
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
                    Filters.exists("llm_multishot_data", true),
                    Filters.exists("suggestion_evaluation", false),
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

        val corenlpDatasetArgs = ChatGPTSuggestionsEvaluationArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB,
            ),
            mongoArgs = MongoArgs(
                db = "RefactoringMiner/CoreNLP_dataset",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
                    Filters.ne("oracle.manually_marked", true),
                    Filters.exists("llm_multishot_data", true),
                    Filters.exists("suggestion_evaluation", false),
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

        val xuDatasetArgs = ChatGPTSuggestionsEvaluationArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.LOCAL_FILE,
            ),
            mongoArgs = MongoArgs(
                db = "ef_evaluation/revisited_xu_dataset",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
                mongoFilter = Filters.and(
                    Filters.gt("oracle.loc", 1),
                    Filters.ne("oracle.manually_marked", true),
                    Filters.exists("llm_multishot_data", true),
                    Filters.exists("suggestion_evaluation", false),
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

        val silvaDatasetArgs = ChatGPTSuggestionsEvaluationArgs(
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
                    Filters.exists("llm_multishot_data", true),
                    Filters.exists("suggestion_evaluation", false),
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

        val fixPointArgs = ChatGPTSuggestionsEvaluationArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB
            ),
            mongoArgs = MongoArgs(
                db = "ef_evaluation_abhiram/paper_examples",
                updateDocs = true,
                mongoDocsFetchLimit = 1,
                mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dead51be9ffd545ddd69d5")),
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

        val extendedCorpusArgs = ChatGPTSuggestionsEvaluationArgs(
            repoArgs = RepoArgs(
                repoType = RepoType.ONLINE_GITHUB
            ),
            mongoArgs = MongoArgs(
                db = "playground_refminer/extended_corpus",
                updateDocs = true,
                mongoDocsFetchLimit = 0,
//                mongoFilter = Document(),
//                mongoFilter = Filters.eq("_id", org.bson.types.ObjectId("64dead4abe9ffd545ddd67a2")),
                mongoFilter = Filters.exists("suggestion_evaluation", false),
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

//        ChatGPTSuggestionsEvaluation(MongoManager.FromConnectionString(args.mongoArgs.db), args).process()
//        ChatGPTSuggestionsEvaluation(MongoManager.FromConnectionString(corenlpDatasetArgs.mongoArgs.db), corenlpDatasetArgs).process()
//        ChatGPTSuggestionsEvaluation(MongoManager.FromConnectionString(xuDatasetArgs.mongoArgs.db), xuDatasetArgs).process()
//        ChatGPTSuggestionsEvaluation(MongoManager.FromConnectionString(silvaDatasetArgs.mongoArgs.db), silvaDatasetArgs).process()
//        ChatGPTSuggestionsEvaluation(MongoManager.FromConnectionString(fixPointArgs.mongoArgs.db), fixPointArgs).process()
        ChatGPTSuggestionsEvaluation(MongoManager.FromConnectionString(extendedCorpusArgs.mongoArgs.db), extendedCorpusArgs).process()
    }
}
