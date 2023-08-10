package com.intellij.ml.llm.template

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import junit.framework.TestCase

class EFAgainstOracle: LightPlatformCodeInsightTestCase() {
    private var mongoClient: MongoClient = MongoClients.create("mongodb://localhost:27017")
    
    fun `test nothing`() {
        TestCase.assertTrue(true)
    }
}