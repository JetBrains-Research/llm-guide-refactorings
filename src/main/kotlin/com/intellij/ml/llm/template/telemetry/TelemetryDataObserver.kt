package com.intellij.ml.llm.template.telemetry

import com.google.gson.Gson
import com.intellij.ml.llm.template.utils.EFNotification
import com.intellij.ml.llm.template.utils.Observer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.toNioPath
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class TelemetryDataObserver : Observer {
    companion object {
        private const val LOG_DIR_NAME = "ef_plugin_logs"
        private const val LOG_FILE_NAME = "ef_telemetry_data.log"
    }

    private val logFile = PathManager.getLogPath().toNioPath()
        .resolve(LOG_DIR_NAME)
        .resolve(LOG_FILE_NAME).toFile()

    init {
        runBlocking {
            withContext(Dispatchers.IO) {
                if (!logFile.exists()) {
                    logFile.parent.toNioPath().createDirectories()
                    logFile.createNewFile()
                }
            }
        }
    }

    private fun logToPluginFile(telemetryData: EFTelemetryData) {
        runBlocking {
            withContext(Dispatchers.IO) {
                logFile.appendText("${Gson().toJson(telemetryData)}\n")
            }
        }
    }

    override fun update(notification: EFNotification) {
        when (notification.payload) {
            is EFTelemetryData -> logToPluginFile(notification.payload)
        }
    }
}