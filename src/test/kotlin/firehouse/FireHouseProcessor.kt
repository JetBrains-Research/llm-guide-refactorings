package firehouse

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
import org.bson.types.ObjectId
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.RepositoryBuilder
import java.io.File

data class Args(
    var repoPath: String,
    var updateDocs: Boolean,
    var db: String,
    var repoBranch: String
)

class FireHouseProcessor(val mongoManager: MongoManager, val args: Args) : LightPlatformCodeInsightTestCase() {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private val projectPath = ""

    override fun getTestDataPath(): String {
        return projectPath
    }

    init {
        Registry.get("llm.for.code.enable.mock.requests").setValue(false) // or another value type
    }

    fun process() {
//        val filter = Filters.exists("jetgpt_ranking", false)
        val filter = Filters.eq("_id", ObjectId("64e634f8bfc767153049b9e9"))
        val limit = 1
        val docs = mongoManager.collection.find(filter).limit(limit)
        val repository = RepositoryBuilder().setGitDir(File("${args.repoPath}/.git")).build()
        val git = Git(repository)
        val maxShots = 5

        docs.forEach { doc ->
            val docId = doc["_id"].toString()
            println("processing document: ${docId}")

            val commitHash = doc.getString("commit_hash")
            val filename = doc.getString("filename")
            val lineStart = doc.getInteger("start_line") - 1
            val lineEnd = doc.getInteger("end_line") - 1
            val githubUrl = doc.getString("github_url")

            // checkout repo to commit hash
            switchToBranch(git, args.repoBranch)
            git.checkout().setName(commitHash).call()

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
            }
            else {
                llmMultishotResponseDataList = multishotSender.sendRequest(codeSnippet, maxShots)
                val llmMultishotDataDoc = MongoCandidateAdapter.llmMultishotResponseData2Mongo(llmMultishotResponseDataList)
                doc.append("llm_multishot_data", llmMultishotDataDoc)
            }

            val multishotCandidates = multishotProducer.buildMultishotCandidates(llmMultishotResponseDataList)
            val candidates = multishotCandidates.flatMap { it.efCandidates }.toList()
            val llmProcessingTime = llmMultishotResponseDataList.sumOf { it.processingTime }
            val jetGptProcessingTime = multishotCandidates.sumOf { it.jetGPTProcessingTime }

            val rankedByPopularity = EFCandidateUtils.rankByPopularity(candidates)
            val rankedByHeat = EFCandidateUtils.rankByHeat(candidates)
            val rankedBySize = EFCandidateUtils.rankBySize(candidates)

            var rankedByPopularityDoc = MongoCandidateAdapter.adaptRankedCandidateList(rankedByPopularity, multishotCandidates)
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

            switchToBranch(git, args.repoBranch)
        }

        mongoManager.close()
    }

    private fun switchToBranch(git: Git, branchName: String) {
        git.checkout().setForced(true).setName(branchName).call()
        git.reset().setMode(ResetCommand.ResetType.HARD).call()
    }
}

class TestFirehouseProcessor : LightPlatformCodeInsightTestCase() {
    fun `test FirehouseProcessor`() {
        val args = Args(
            db = "ef_evaluation/firehouse_ijce",
            repoPath = "/Users/dpomian/hardwork/research/jetbrains/extract_method_firehouse/githubclones/JetBrains/intellij-community",
            repoBranch = "master",
            updateDocs = true
        )
        FireHouseProcessor(MongoManager.FromConnectionString(args.db), args).process()
    }
}
