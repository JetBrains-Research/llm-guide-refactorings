package oracle.evaluation

import com.google.gson.Gson
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMBaseResponse
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.openai.OpenAIChatResponse
import com.intellij.ml.llm.template.models.sendChatRequest
import com.intellij.ml.llm.template.prompts.multishotExtractFunctionPrompt
import com.intellij.ml.llm.template.utils.*
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.eclipse.jgit.api.Git
import utilities.*

data class FixPointLLMArgs(
    val repoArgs: RepoArgs,
    val mongoArgs: MongoArgs,
    val llmArgs: LLMArgs,
)

class FixPointLLM(val mongoManager: MongoManager, val args: FixPointLLMArgs) : LightPlatformCodeInsightTestCase() {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private val tempDownloadPath = "/Users/dpomian/hardwork/research/jetbrains/llm-guide-refactorings/src/test/tmp"
    private var git: Git? = null
    private val projectPath = ""
    override fun getTestDataPath(): String {
        return projectPath
    }

    fun llmIterations() {
        fun Set<EFSuggestion>.containsByLineNumbers(efSuggestion: EFSuggestion): Boolean {
            return this.any { it.lineStart == efSuggestion.lineStart && it.lineEnd == efSuggestion.lineEnd }
        }

        var foundFixPoint = false
        var iteration = 0
        val efSuggestionsSet = mutableSetOf<EFSuggestion>()
        val llmRawResponses = mutableMapOf<Int, LLMBaseResponse>()
        val documentsToInsert = mutableListOf<Document>()
        val docs = mongoManager.collection.find(args.mongoArgs.mongoFilter).limit(args.mongoArgs.mongoDocsFetchLimit)

        docs.forEach { doc ->
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
            val prompt = multishotExtractFunctionPrompt(codeSnippet)

            while (!foundFixPoint) {
                // send llm request
                val llmResponse = sendChatRequest(
                    project = project,
                    messages = prompt,
                    model = efLLMRequestProvider.chatModel,
                    temperature = args.llmArgs.temperature
                )

                val suggestionsSetLen = efSuggestionsSet.size
                val efNewSuggestions = mutableListOf<EFSuggestion>()

                // identify suggestions
                if (llmResponse != null) {
                    val efSuggestionList = identifyExtractFunctionSuggestions(llmResponse.getSuggestions()[0].text)
                    efSuggestionList.suggestionList.forEach {
                        if (!efSuggestionsSet.containsByLineNumbers(it)) {
                            efSuggestionsSet.add(it)
                            efNewSuggestions.add(it)
                        }
                    }
                    llmRawResponses.put(iteration, llmResponse)
                }

                // if there are no new solutions => stop
                if (efNewSuggestions.isEmpty()) foundFixPoint = true
//                if (iteration == 10) foundFixPoint = true

                iteration += 1

                val docToInsert = Document()
                    .append("llm_raw_response", Gson().toJson(llmResponse, OpenAIChatResponse::class.java))
                    .append("shot_no", iteration)
                    .append("all-choices", efSuggestions2Mongo(efSuggestionsSet.toList()))
                    .append("new-choices", efSuggestions2Mongo(efNewSuggestions))

                documentsToInsert.add(docToInsert)
            }

            doc.append(
                "multishot-${args.llmArgs.model}",
                Document("temperature_${args.llmArgs.temperature}", documentsToInsert)
            )

            if (args.mongoArgs.updateDocs) {
                mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), Document("\$set", doc))
            }

            mongoManager.close()

            println("found fix point in $iteration iterations")

            rollbackLocalFile(filename, args.repoArgs, git)
        }
    }

    fun efSuggestions2Mongo(efSuggestions: List<EFSuggestion>): List<Document> {
        val result = mutableListOf<Document>()
        efSuggestions.forEach { efSuggestion ->
            result.add(Document()
                .append("function_name", efSuggestion.functionName)
                .append("line_start", efSuggestion.lineStart)
                .append("line_end", efSuggestion.lineEnd)
            )
        }
        return result
    }
}


class FixPointTest : LightPlatformCodeInsightTestCase() {
    fun `test fix point`() {
        val fixPointArgs = FixPointLLMArgs(
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

//        EFSettings.instance.reset()
//            .addHeuristic(EMHeuristic.IF_BODY)
//            .addHeuristic(EMHeuristic.PREV_ASSIGNMENT)
//            .addHeuristic(EMHeuristic.KEEP_ADJUSTED_CANDIDATE_ONLY)
//            .addHeuristic(EMHeuristic.MAX_METHOD_LOC_THRESHOLD)
//            .addSetting(EFSettingType.MULTISHOT_LEARNING)
//            .addThresholdValue(EMHeuristic.MAX_METHOD_LOC_THRESHOLD, 0.88)

        FixPointLLM(MongoManager.FromConnectionString(fixPointArgs.mongoArgs.db), fixPointArgs).llmIterations()
    }
}
