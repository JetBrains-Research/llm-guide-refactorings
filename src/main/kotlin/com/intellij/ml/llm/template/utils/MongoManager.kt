package com.intellij.ml.llm.template.utils

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import org.bson.Document

class MongoManager(val dbName: String, val collectionName: String) {
    private var mongoClient: MongoClient = MongoClients.create("mongodb://localhost:27017")

    companion object {
        fun FromConnectionString(connectionString: String): MongoManager {
            val split = connectionString.split("/")
            return MongoManager(split[0], split[1])
        }
    }

    val collection: MongoCollection<Document>
        get() {
            return mongoClient.getDatabase(dbName).getCollection(collectionName)
        }

    fun close() {
        mongoClient.close()
    }
}