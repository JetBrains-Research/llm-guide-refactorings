//package evaluation
//
//import com.intellij.ml.llm.template.extractfunction.EFCandidate
//import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
//import com.intellij.ml.llm.template.models.LLMRequestProvider
//import com.intellij.ml.llm.template.models.sendChatRequest
//import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
//import com.intellij.ml.llm.template.utils.*
//import com.intellij.openapi.editor.Editor
//import com.intellij.psi.PsiNameIdentifierOwner
//import com.intellij.psi.util.PsiTreeUtil
//import com.intellij.testFramework.LightPlatformCodeInsightTestCase
//import com.mongodb.client.MongoClient
//import com.mongodb.client.MongoClients
//import com.mongodb.client.model.Filters
//import com.mongodb.client.model.Updates
//import org.bson.*
//import org.eclipse.jgit.api.Git
//import org.eclipse.jgit.lib.RepositoryBuilder
//import org.junit.jupiter.api.TestInstance
//import java.io.File
//import java.util.concurrent.TimeUnit
//
//
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//class BatchProcessingExtractFunction : LightPlatformCodeInsightTestCase() {
//    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
//    private var mongoClient: MongoClient = MongoClients.create("mongodb://localhost:27017")
//    private var projectPath = ""
//
//    override fun getTestDataPath(): String {
//        return projectPath
//    }
//
//    fun testNothing() {
//        println("testing nothing")
//    }
//
////    fun `test gather extract function suggestions`() {
////        val DOC_LIMIT = 10
////        val USE_MOCK = false
////
////        Registry.get("llm.for.code.enable.mock.requests").setValue(USE_MOCK)
////
////        val dbName = "lizzard"
////        val collectionName = "chatgpt_ef_suggestions"
////        dropCollection(dbName, collectionName)
////        val filter = Document("nloc", Document("\$gte", 100))
////            .append("filename", Document("\$not", Document("\$regex", "generated")))
////        val documents = getMongoDBDocuments(dbName, "lizzy_stats", filter, DOC_LIMIT)
////        documents.forEach {
////            doc ->
////            println("processing: ${doc["_id"].toString()}")
////            this.projectPath = doc["prj_path"].toString()
////            val lineNumber: Int = doc["start_line"].toString().toInt()
////            val filename = doc["filename"].toString().replace(doc["prj_path"].toString(), "")
////            configureByFile(filename)
////            val functionCode = getFunctionCode(editor, lineNumber)
////            val messageList = fewShotExtractSuggestion(functionCode)
////            val response = sendChatRequest(
////                project, messageList, efLLMRequestProvider.chatModel, efLLMRequestProvider
////            )
////            if (response != null) {
////                val docToInsert = Document().append("mongo_id", doc["_id"].toString())
////                docToInsert.append("chatgpt_rq", Gson().toJson(messageList))
////                docToInsert.append("function_code", functionCode)
////
////                val suggestionList = BsonArray()
////
////                for (suggestion in response.getSuggestions()){
////                    val efSuggestionList = identifyExtractFunctionSuggestions(suggestion.text)
////                    efSuggestionList.suggestion_list.forEach{ efs ->
////                        suggestionList.apply {
////                            add(BsonDocument("lineStart", BsonInt32(efs.lineStart)).append("lineEnd", BsonInt32(efs.lineEnd)).append("functionName", BsonString(efs.functionName)))
////                        }
////                    }
////
////                    docToInsert.append("suggestions", suggestionList)
////                }
////                docToInsert.append("raw_response", Gson().toJson(response))
////                docToInsert.append("filename", filename)
////                docToInsert.append("prj_path", doc["prj_path"].toString())
////                docToInsert.append("github_url", doc["github_url"].toString())
////
////                persistDocument(dbName, collectionName, docToInsert)
////            }
////        }
////    }
//
////    fun `test perform extract function based on suggestions`() {
////        println("over here")
////        val DOC_LIMIT = 0
////        val DB_NAME = "lizzard"
////        val SRC_COL_NAME = "chatgpt_ef_suggestions"
////        val DEST_COL_NAME = "code_transformations_status_6"
////        dropCollection(DB_NAME, DEST_COL_NAME)
////        val filter = Document()
////        val documents = getMongoDBDocuments(DB_NAME, SRC_COL_NAME, filter, DOC_LIMIT)
////        val codeTransformer = CodeTransformer()
////
////        documents.forEach{ doc ->
////            this.projectPath = doc["prj_path"].toString()
////            val filename = doc["filename"].toString()
////            val suggestionsDoc = doc["suggestions"] as List<*>
////            val suggestions = suggestionsDoc.map {
////                val suggestionDoc = it as Document
////                EFSuggestion(
////                    functionName = suggestionDoc.getString("functionName"),
////                    lineStart = suggestionDoc.getInteger("lineStart"),
////                    lineEnd = suggestionDoc.getInteger("lineEnd")
////                )
////            }
////            val efSuggestionList = EFSuggestionList(suggestions)
////
////            val transformationResults = BsonArray()
////
////            efSuggestionList.suggestion_list.forEach{ efs ->
////                val efObserver = EFObserver()
////                codeTransformer.addObserver(efObserver)
////
////                configureByFile(filename)
////                EFCandidateFactory().buildCandidates(efs, editor, file).forEach { candidate ->
////                    configureByFile(filename)
////                    codeTransformer.applyCandidate(candidate, project, editor, file)
////                }
////
////                efObserver.getNotifications().forEach { notification ->
////                    with(notification) {
////                        val suggestionBson = BsonDocument()
////                            .append("functionName", BsonString(candidate.efSuggestion.functionName))
////                            .append("lineStart", BsonInt32(candidate.efSuggestion.lineStart))
////                            .append("lineEnd", BsonInt32(candidate.efSuggestion.lineEnd))
////                            .append("githubUrl", BsonString( replaceGithubUrlLineRange( doc["github_url"].toString(), candidate.efSuggestion.lineStart, candidate.efSuggestion.lineEnd )))
////
////                        transformationResults.apply {
////                            add(
////                                BsonDocument()
////                                    .append("suggestion", suggestionBson)
////                                    .append("functionName", BsonString(candidate.functionName))
////                                    .append("lineStart", BsonInt32(candidate.lineStart))
////                                    .append("lineEnd", BsonInt32(candidate.lineEnd))
////                                    .append("offsetStart", BsonInt32(candidate.offsetStart))
////                                    .append("offsetEnd", BsonInt32(candidate.offsetEnd))
////                                    .append("type", BsonString(candidate.type.toString()))
////                                    .append("githubUrl", BsonString( replaceGithubUrlLineRange( doc["github_url"].toString(), candidate.lineStart, candidate.lineEnd )))
////                                    .append("status", BsonString(notification.result.toString()))
////                                    .append("reason", BsonString(notification.reason))
////                            )
////                        }
////                    }
////                }
////
////                codeTransformer.removeObserver(efObserver)
////            }
////            doc.append("transformation_results", transformationResults)
////            persistDocument(DB_NAME, DEST_COL_NAME, doc)
////        }
////
////        println("Project path: ${this.projectPath}")
////    }
//
//    private fun persistDocument(dbName: String, collectionName: String, doc: Document) {
//        val collection = mongoClient.getDatabase(dbName).getCollection(collectionName)
//        collection.insertOne(doc)
//    }
//
//    private fun dropCollection(dbName: String, collectionName: String) {
//        val col = mongoClient.getDatabase(dbName).getCollection(collectionName)
//        col.drop()
//    }
//
//    private fun getMongoDBDocuments(
//        dbName: String,
//        collectionName: String,
//        filter: Document,
//        limit: Int = 0
//    ): List<Document> {
//        val collection = mongoClient.getDatabase(dbName).getCollection(collectionName)
//        val docs: MutableList<Document> = mutableListOf()
//        val results = collection.find(filter).limit(limit)
//        results.forEach { document ->
//            docs.add(document)
//        }
//        return docs
//    }
//
//    private fun getFunctionCode(editor: Editor, lineNumber: Int): String {
//        var code = ""
//        var psiElement = file.findElementAt(editor.document.getLineStartOffset(lineNumber))
//        if (psiElement != null) {
//            val namedElement = PsiTreeUtil.getParentOfType(psiElement, PsiNameIdentifierOwner::class.java)
//            if (namedElement != null) {
//                val textRange = namedElement.textRange
//                editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
//                code = editor.selectionModel.selectedText.toString()
//                code = addLineNumbersToCodeSnippet(
//                    code,
//                    editor.document.getLineNumber(editor.selectionModel.selectionStart) + 1
//                )
//            }
//        }
//        return code
//    }
//
//
//    fun `test evaluation firehouse IJCE`() {
//        val filter = Filters.exists("jetGptAnalysis", false)
//        val mongoCollection = mongoClient.getDatabase("ef_evaluation").getCollection("firehouse_ijce")
//        val docs = mongoCollection.find(filter)
//        val repositoryPath =
//            "/Users/dpomian/hardwork/research/jetbrains/extract_method_firehouse/githubclones/JetBrains/intellij-community"
//        val repository = RepositoryBuilder().setGitDir(File("$repositoryPath/.git")).build()
//        val git = Git(repository)
//
//        docs.forEach { doc ->
//            val commitHash = doc.getString("commit_hash")
//            val filename = doc.getString("filename")
//            val lineStart = doc.getInteger("start_line") - 1
//            val lineEnd = doc.getInteger("end_line") - 1
//            println("commit hash: $commitHash")
//
//            // checkout repo to commit hash
//            git.checkout().setName(commitHash).call()
//
//            // configure project
//            configureByFile(filename)
//
//            var codeSnippet = readCodeSnippet(filename, lineStart, lineEnd)
//            codeSnippet = addLineNumbersToCodeSnippet(codeSnippet, lineStart)
//
//
//            var startTime = System.nanoTime()
//            val messageList = fewShotExtractSuggestion(codeSnippet)
////            val response: LLMBaseResponse? = null
//            val response = sendChatRequest(
//                project, messageList, efLLMRequestProvider.chatModel, efLLMRequestProvider
//            )
//            val llmProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
//
//            startTime = System.nanoTime()
//            var filteredCandidates = emptyList<EFCandidate>()
//            if (response != null) {
//                val llmResponse = response.getSuggestions()[0]
//                val efSuggestionList = identifyExtractFunctionSuggestions(llmResponse.text)
//                val candidates =
//                    EFCandidateFactory().buildCandidates(efSuggestionList.suggestionList, editor, file).toList()
//                filteredCandidates = candidates.filter {
//                    isCandidateExtractable(it, editor, file)
//                }.sortedByDescending { it.lineEnd - it.lineStart }
//            }
//            val jetGptProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
//
//            val jetGptAnalysisDoc = buildJetGPTAnalysisDoc(filteredCandidates, doc.getString("github_url"))
//            jetGptAnalysisDoc.append("processing_time", Document("llm_processing_time", llmProcessingTime).append("jetGPT_processing_time", jetGptProcessingTime))
//            val jetGptAnalysisUpdate = Updates.set("jetGptAnalysis", jetGptAnalysisDoc)
//
//            mongoCollection.updateOne(Filters.eq("_id", doc["_id"]), jetGptAnalysisUpdate)
//            println("updated mongo document: ${doc["_id"].toString()}")
//
//            // switch back to main
//            git.checkout().setName("master").call()
//        }
//    }
//
//
//    private fun buildJetGPTAnalysisDoc(
//        efCandidates: List<EFCandidate>,
//        githubUrl: String
//    ): Document {
//        var githubUrl = githubUrl
//        val suggestions: MutableList<Document> = ArrayList()
//        for (efCandidate in efCandidates) {
//            val lineStart: Int = efCandidate.lineStart
//            val lineEnd: Int = efCandidate.lineEnd
//            val length = lineEnd - lineStart + 1
//            val offsetStart: Int = efCandidate.offsetStart
//            val offsetEnd: Int = efCandidate.offsetEnd
//            val functionName = efCandidate.functionName
//            githubUrl = githubUrl.replace("#L\\d+-L\\d+".toRegex(), String.format("#L%d-L%d", lineStart, lineEnd))
//            githubUrl = githubUrl.replace("R\\d+-R\\d+".toRegex(), String.format("R%d-R%d", lineStart, lineEnd))
//            suggestions.add(
//                Document().append("function_name", functionName).append("line_start", lineStart)
//                    .append("line_end", lineEnd).append("length", length).append("offset_start", offsetStart)
//                    .append("offset_end", offsetEnd).append("github_url", githubUrl)
//            )
//        }
//        return Document("suggestions", suggestions)
//    }
//}
