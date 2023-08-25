package firehouse

import com.intellij.ml.llm.template.utils.MongoManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.bson.Document
import java.nio.file.Files
import java.nio.file.Paths

data class FHCsvReaderArgs(
    var filename: String,
    val db: String,
    val projectPath: String,
    val updateDocs: Boolean
)

class FireHouseCsvReader(val mongoManager: MongoManager, val args: FHCsvReaderArgs) {
    fun run() {
        val csvData = readCsvFile(args.filename)
        val docsList = adaptCsvToMongo(csvData)
        docsList.forEach { doc ->
            val count = mongoManager.collection.countDocuments(Document("github_url", doc.getString("github_url")))
            if (args.updateDocs) {
                if (count.toInt() == 0) {
                    mongoManager.collection.insertOne(doc)
                }
            }
        }
        mongoManager.close()
    }

    private fun readCsvFile(filename: String): List<CSVRecord> {
        val path = Paths.get(filename)
        var result: MutableList<CSVRecord>
        Files.newBufferedReader(path).use { reader ->
            val csvParser = CSVParser(reader, CSVFormat.DEFAULT.withHeader())
            result = csvParser.records
        }
        return result
    }

    private fun adaptCsvToMongo(csvData: List<CSVRecord>): List<Document> {
        val result = mutableListOf<Document>()
        csvData.forEach { csvRow ->
            val mongoDoc = Document()
                .append("commit_hash", csvRow[0])
                .append("author_name", csvRow[1])
                .append("committer_name", csvRow[2])
                .append("filename", csvRow[3])
                .append("class_name", csvRow[4])
                .append("function_name", csvRow[5])
                .append("start_line", csvRow[6].toInt())
                .append("end_line", csvRow[7].toInt())
                .append("hf_loc", csvRow[8].toInt())
                .append("github_url", csvRow[9])
                .append("github_diff_url", csvRow[10])
                .append("timestamp", csvRow[11])
            result.add(mongoDoc)
        }

        return result
    }
}

class FireHouseCsvReaderTest: LightPlatformCodeInsightTestCase() {
    fun `test build dataset`() {
        val args = FHCsvReaderArgs(
            filename = "/Users/dpomian/hardwork/research/jetbrains/extract_method_firehouse/output/JetBrains_intellij-community_20230822_1828.csv",
            db = "ef_evaluation/firehouse_ijce",
            projectPath = "/Users/dpomian/hardwork/research/jetbrains/extract_method_firehouse/githubclones/JetBrains/intellij-community",
            updateDocs = true
        )

        FireHouseCsvReader(MongoManager.FromConnectionString(args.db), args).run()
    }
}