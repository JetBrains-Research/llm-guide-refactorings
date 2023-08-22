package evaluation

import com.intellij.ml.llm.template.common.extractfunction.EFCandidate
import com.intellij.ml.llm.template.common.utils.PsiUtils
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters
import org.bson.Document

class XuOracle : LightPlatformCodeInsightTestCase() {
    private var mongoClient: MongoClient = MongoClients.create("mongodb://localhost:27017")
    private var projectPath = ""

    override fun getTestDataPath(): String {
        return projectPath
    }

    /**
     * enrich oracle data with the following information
     * 1. Host Function before EF
     * 2. Extracted Function information
     */
    fun `test enrich oracle`() {
        val dbName = "ef_evaluation"
        val collectionName = "xu_dataset"
        val mongoFilter = Filters.and(Filters.size("offset_ranges", 1), Filters.exists("extracted_function", true))
//        val mongoFilter = Filters.and(Filters.size("offset_ranges", 1),Filters.eq("project_name", "JHotDraw5.2"))
//        val mongoFilter = buildMongodbFiltersById("64dadca6e4026f5379e307e7")
        val docs = fetchDocuments(mongoClient, "ef_evaluation", "xu_dataset", mongoFilter, 0)
        val mongoCollection = mongoClient
            .getDatabase(dbName)
            .getCollection(collectionName)

        docs.forEach { doc ->
            println("processing document: ${doc["_id"].toString()}")
            val filename = doc.getString("local_filename")
            configureByFile(filename)

            val offsetRanges = doc.getList("offset_ranges", Document::class.java)
            val offsetStart = offsetRanges[0].getInteger("offset_start")
            val offsetEnd = offsetRanges[0].getInteger("offset_end")
            val function = PsiUtils.getParentFunctionOrNull(offsetStart, file)
            val hostFunctionSrc = function!!.text

            val repoName = doc.getString("repo_name")
            val repoOwner = doc.getString("repo_owner")

            val repoFilePath = filename.substring(filename.indexOf(doc.getString("project_name")))
            val hostFunctionLineStart = editor.document.getLineNumber(function.startOffset) + 1
            val hostFunctionLineEnd = editor.document.getLineNumber(function.endOffset) + 1
            val hostFunctionUrl =
                "https://github.com/$repoOwner/$repoName/blob/main/projects/$repoFilePath#L$hostFunctionLineStart-L$hostFunctionLineEnd"

            val efCandidate = EFCandidate(
                functionName = "foo",
                offsetStart = offsetStart,
                offsetEnd = offsetEnd,
                lineStart = 0,
                lineEnd = 0
            )
            val efLineStart = editor.document.getLineNumber(offsetStart) + 1
            val efLineEnd = editor.document.getLineNumber(offsetEnd) + 1
            val efSrc = getExtractedCodeSnippet(efCandidate, project, editor, file)
            val oracleUrl =
                "https://github.com/$repoOwner/$repoName/blob/main/projects/$repoFilePath#L$efLineStart-L$efLineEnd"
            val oracleLoc = efLineEnd - efLineStart + 1

            val hfBeforeEfDoc = Document()
                .append("filename", filename)
                .append("function_src", hostFunctionSrc)
                .append("url", hostFunctionUrl)
            val efDoc = Document()
                .append("filename", filename)
                .append("function_src", efSrc)
                .append("url", oracleUrl)
            val oracleDoc = Document()
                .append("url", oracleUrl)
                .append("filename", filename)
                .append("added_statement_count", 0)
                .append("line_start", efLineStart)
                .append("line_end", efLineEnd)
                .append("loc", oracleLoc)
                .append("line_intervals", listOf(efLineStart, efLineEnd))
            doc.append("host_function_before_ef", hfBeforeEfDoc)
            doc.append("extracted_function", efDoc)
            doc.append("oracle", oracleDoc)
            mongoCollection.replaceOne(Filters.eq("_id", doc.get("_id")), doc)

            val x = 0
        }
    }
}