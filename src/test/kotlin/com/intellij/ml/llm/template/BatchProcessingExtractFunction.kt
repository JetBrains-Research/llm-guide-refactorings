package com.intellij.ml.llm.template
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.utils.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.bson.*
import org.junit.jupiter.api.TestInstance


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchProcessingExtractFunction : LightPlatformCodeInsightTestCase() {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    private var mongoClient: MongoClient = MongoClients.create("mongodb://localhost:27017")
    private var projectPath = "src/test"

    override fun getTestDataPath(): String {
        return projectPath
    }

    fun testNothing() {
        println("testing nothing")
    }

//    fun `test gather extract function suggestions`() {
//        val DOC_LIMIT = 10
//        val USE_MOCK = false
//
//        Registry.get("llm.for.code.enable.mock.requests").setValue(USE_MOCK)
//
//        val dbName = "lizzard"
//        val collectionName = "chatgpt_ef_suggestions"
//        dropCollection(dbName, collectionName)
//        val filter = Document("nloc", Document("\$gte", 100))
//            .append("filename", Document("\$not", Document("\$regex", "generated")))
//        val documents = getMongoDBDocuments(dbName, "lizzy_stats", filter, DOC_LIMIT)
//        documents.forEach {
//            doc ->
//            println("processing: ${doc["_id"].toString()}")
//            this.projectPath = doc["prj_path"].toString()
//            val lineNumber: Int = doc["start_line"].toString().toInt()
//            val filename = doc["filename"].toString().replace(doc["prj_path"].toString(), "")
//            configureByFile(filename)
//            val functionCode = getFunctionCode(editor, lineNumber)
//            val messageList = fewShotExtractSuggestion(functionCode)
//            val response = sendChatRequest(
//                project, messageList, efLLMRequestProvider.chatModel, efLLMRequestProvider
//            )
//            if (response != null) {
//                val docToInsert = Document().append("mongo_id", doc["_id"].toString())
//                docToInsert.append("chatgpt_rq", Gson().toJson(messageList))
//                docToInsert.append("function_code", functionCode)
//
//                val suggestionList = BsonArray()
//
//                for (suggestion in response.getSuggestions()){
//                    val efSuggestionList = identifyExtractFunctionSuggestions(suggestion.text)
//                    efSuggestionList.suggestion_list.forEach{ efs ->
//                        suggestionList.apply {
//                            add(BsonDocument("lineStart", BsonInt32(efs.lineStart)).append("lineEnd", BsonInt32(efs.lineEnd)).append("functionName", BsonString(efs.functionName)))
//                        }
//                    }
//
//                    docToInsert.append("suggestions", suggestionList)
//                }
//                docToInsert.append("raw_response", Gson().toJson(response))
//                docToInsert.append("filename", filename)
//                docToInsert.append("prj_path", doc["prj_path"].toString())
//                docToInsert.append("github_url", doc["github_url"].toString())
//
//                persistDocument(dbName, collectionName, docToInsert)
//            }
//        }
//    }

//    fun `test perform extract function based on suggestions`() {
//        println("over here")
//        val DOC_LIMIT = 0
//        val DB_NAME = "lizzard"
//        val SRC_COL_NAME = "chatgpt_ef_suggestions"
//        val DEST_COL_NAME = "code_transformations_status_6"
//        dropCollection(DB_NAME, DEST_COL_NAME)
//        val filter = Document()
//        val documents = getMongoDBDocuments(DB_NAME, SRC_COL_NAME, filter, DOC_LIMIT)
//        val codeTransformer = CodeTransformer()
//
//        documents.forEach{ doc ->
//            this.projectPath = doc["prj_path"].toString()
//            val filename = doc["filename"].toString()
//            val suggestionsDoc = doc["suggestions"] as List<*>
//            val suggestions = suggestionsDoc.map {
//                val suggestionDoc = it as Document
//                EFSuggestion(
//                    functionName = suggestionDoc.getString("functionName"),
//                    lineStart = suggestionDoc.getInteger("lineStart"),
//                    lineEnd = suggestionDoc.getInteger("lineEnd")
//                )
//            }
//            val efSuggestionList = EFSuggestionList(suggestions)
//
//            val transformationResults = BsonArray()
//
//            efSuggestionList.suggestion_list.forEach{ efs ->
//                val efObserver = EFObserver()
//                codeTransformer.addObserver(efObserver)
//
//                configureByFile(filename)
//                EFCandidateFactory().buildCandidates(efs, editor, file).forEach { candidate ->
//                    configureByFile(filename)
//                    codeTransformer.applyCandidate(candidate, project, editor, file)
//                }
//
//                efObserver.getNotifications().forEach { notification ->
//                    with(notification) {
//                        val suggestionBson = BsonDocument()
//                            .append("functionName", BsonString(candidate.efSuggestion.functionName))
//                            .append("lineStart", BsonInt32(candidate.efSuggestion.lineStart))
//                            .append("lineEnd", BsonInt32(candidate.efSuggestion.lineEnd))
//                            .append("githubUrl", BsonString( replaceGithubUrlLineRange( doc["github_url"].toString(), candidate.efSuggestion.lineStart, candidate.efSuggestion.lineEnd )))
//
//                        transformationResults.apply {
//                            add(
//                                BsonDocument()
//                                    .append("suggestion", suggestionBson)
//                                    .append("functionName", BsonString(candidate.functionName))
//                                    .append("lineStart", BsonInt32(candidate.lineStart))
//                                    .append("lineEnd", BsonInt32(candidate.lineEnd))
//                                    .append("offsetStart", BsonInt32(candidate.offsetStart))
//                                    .append("offsetEnd", BsonInt32(candidate.offsetEnd))
//                                    .append("type", BsonString(candidate.type.toString()))
//                                    .append("githubUrl", BsonString( replaceGithubUrlLineRange( doc["github_url"].toString(), candidate.lineStart, candidate.lineEnd )))
//                                    .append("status", BsonString(notification.result.toString()))
//                                    .append("reason", BsonString(notification.reason))
//                            )
//                        }
//                    }
//                }
//
//                codeTransformer.removeObserver(efObserver)
//            }
//            doc.append("transformation_results", transformationResults)
//            persistDocument(DB_NAME, DEST_COL_NAME, doc)
//        }
//
//        println("Project path: ${this.projectPath}")
//    }

    private fun persistDocument(dbName: String, collectionName: String, doc: Document) {
        val collection = mongoClient.getDatabase(dbName).getCollection(collectionName)
        collection.insertOne(doc)
    }

    private fun dropCollection(dbName: String, collectionName: String) {
        val col = mongoClient.getDatabase(dbName).getCollection(collectionName)
        col.drop()
    }

    private fun getMongoDBDocuments(dbName: String, collectionName: String, filter:Document, limit:Int = 0): List<Document> {
        val collection = mongoClient.getDatabase(dbName).getCollection(collectionName)
        val docs: MutableList<Document> = mutableListOf()
        val results = collection.find(filter).limit(limit)
        results.forEach{ document ->
            docs.add(document)
        }
        return docs
    }

    private fun getFunctionCode(editor: Editor, lineNumber: Int) : String {
        var code = ""
        var psiElement = file.findElementAt(editor.document.getLineStartOffset(lineNumber))
        if (psiElement != null) {
            val namedElement = PsiTreeUtil.getParentOfType(psiElement, PsiNameIdentifierOwner::class.java)
            if (namedElement != null) {
                val textRange = namedElement.textRange
                editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
                code = editor.selectionModel.selectedText.toString()
                code = addLineNumbersToCodeSnippet(code, editor.document.getLineNumber(editor.selectionModel.selectionStart)+1)
            }
        }
        return code
    }
}