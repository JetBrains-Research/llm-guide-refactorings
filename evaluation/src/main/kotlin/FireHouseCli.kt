
import com.intellij.ml.llm.template.common.extractfunction.EFCandidate
import com.intellij.ml.llm.template.common.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.common.models.LLMRequestProvider
import com.intellij.ml.llm.template.common.models.sendChatRequest
import com.intellij.ml.llm.template.common.prompts.fewShotExtractSuggestion
import com.intellij.ml.llm.template.common.utils.*
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.model.Filters
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import java.io.File
import java.util.concurrent.TimeUnit

class FireHouseProcessor(val mongoManager: MongoManager) : LightPlatformCodeInsightTestCase() {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider

    fun process(repoPath: String) {
        val filter = Filters.exists("jetgpt_analysis", false)
        val docs = mongoManager.collection.find(filter)
        val repository = RepositoryBuilder().setGitDir(File("$repoPath/.git")).build()
        val git = Git(repository)

        docs.forEach { doc ->
            val docId = doc["_id"].toString()
            println("processing document: ${docId}.")

            val commitHash = doc.getString("commit_hash")
            val filename = doc.getString("filename")
            val lineStart = doc.getInteger("start_line") - 1
            val lineEnd = doc.getInteger("end_line") - 1

            // checkout repo to commit hash
            git.checkout().setName(commitHash).call()

            // configure project
            configureByFile(filename)

            var codeSnippet = readCodeSnippet(filename, lineStart, lineEnd)
            codeSnippet = addLineNumbersToCodeSnippet(codeSnippet, lineStart)


            var startTime = System.nanoTime()
            val messageList = fewShotExtractSuggestion(codeSnippet)
//            val response: LLMBaseResponse? = null
            val response = sendChatRequest(
                project, messageList, efLLMRequestProvider.chatModel, efLLMRequestProvider
            )
            val llmProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

            startTime = System.nanoTime()
            var filteredCandidates = emptyList<EFCandidate>()
            if (response != null) {
                val llmResponse = response.getSuggestions()[0]
                val efSuggestionList = identifyExtractFunctionSuggestions(llmResponse.text)
                val candidates =
                    EFCandidateFactory().buildCandidates(efSuggestionList.suggestionList, editor, file).toList()
                filteredCandidates = candidates.filter {
                    isCandidateExtractable(it, editor, file)
                }.sortedByDescending { it.lineEnd - it.lineStart }
            }
            val jetGptProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

//            val jetGptAnalysisDoc = buildJetGPTAnalysisDoc(filteredCandidates, doc.getString("github_url"))
//            jetGptAnalysisDoc.append("processing_time", Document("llm_processing_time", llmProcessingTime).append("jetGPT_processing_time", jetGptProcessingTime))
//            val jetGptAnalysisUpdate = Updates.set("jetGptAnalysis", jetGptAnalysisDoc)

//            mongoManager.collection.updateOne(Filters.eq("_id", doc["_id"]), jetGptAnalysisUpdate)
//            println("updated mongo document: ${doc["_id"].toString()}")

            // switch back to main
            git.checkout().setName("master").call()
        }
    }
}

fun main(args: Array<String>) {
    val parser = ArgParser("firehouse")
    val db by parser.option(
        ArgType.String,
        shortName = "db",
        description = "MongoDB connection string db_name/collection_name (e.g. ef_evaluation/xu_dataset)"
    ).required()
    val repoPath by parser.option(
        ArgType.String,
        shortName = "local-repo",
        description = "Locally cloned repository"
    ).required()

    val mongoManager = MongoManager.FromConnectionString(db)
    val fireHouseProcessor = FireHouseProcessor(mongoManager)
    fireHouseProcessor.process(repoPath)

    parser.parse(args)
}