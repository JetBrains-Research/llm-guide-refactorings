package oracle.evaluation

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSettingType
import com.intellij.ml.llm.template.extractfunction.EFSettings
import com.intellij.ml.llm.template.extractfunction.MethodExtractionType
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

            val llmDataKey =
                if (EFSettings.instance.has(EFSettingType.MULTISHOT_LEARNING)) "llm_multishot_data" else "llm_singleshot_data"

            val llmResponseDataList = mutableListOf<LlmMultishotResponseData>()
            if (doc.containsKey(llmDataKey)) {
                val multishotDataDoc = doc.get(llmDataKey) as Document
                val lst = multishotDataDoc.getList(temperatureKey, Document::class.java) ?: emptyList()
                llmResponseDataList.addAll(MongoCandidateAdapter.mongo2LLMMultishotResponseData(lst))
            }

            val llmDataArrayDoc = MongoCandidateAdapter.llmMultishotResponseData2Mongo(llmResponseDataList)
            val llmDataDoc = (doc.get(llmDataKey) ?: Document()) as Document
            llmDataDoc.append(temperatureKey, llmDataArrayDoc)
            doc.append(llmDataKey, llmDataDoc)

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
                codeTransofrmer.applyCandidate(candidate, project, editor, file)
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
            doc.append("suggestion_evaluation", Document(temperatureKey, candidateWithResultList))

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
                repoType = RepoType.LOCAL_FILE
            ),
            mongoArgs = MongoArgs(
                db = "dbname/collectionName",
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
                temperature = 1.0,
            )
        )

        EFSettings.instance
            .add(EFSettingType.IF_BLOCK_HEURISTIC)
            .add(EFSettingType.PREV_ASSIGNMENT_HEURISTIC)
            .add(EFSettingType.MULTISHOT_LEARNING)
            .add(EFSettingType.VERY_LARGE_BLOCK_HEURISTIC)

        ChatGPTSuggestionsEvaluation(MongoManager.FromConnectionString(args.mongoArgs.db), args).process()
    }
}
