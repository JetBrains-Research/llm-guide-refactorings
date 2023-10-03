package com.intellij.ml.llm.template.utils

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.utils.addIfNotNull

enum class EFApplicationResult(s: String) {
    OK("OK"),
    FAIL("FAIL")
}

data class EFNotification(
    val payload: Any
)

data class EFCandidateApplicationPayload(
    var result: EFApplicationResult,
    var candidate: EFCandidate,
    var reason: String
)

interface Observer {
    fun update(notification: EFNotification)
}

open class Observable {
    val observers = mutableListOf<Observer>()
    open fun addObserver(observer: Observer) {
        if (observers.contains(observer)) return
        observers.addIfNotNull(observer)
    }

    open fun removeObserver(observer: Observer) {
        observers.remove(observer)
    }

    open fun notifyObservers(notification: EFNotification) {
        observers.forEach {
            (it as Observer).update(notification)
        }
    }
}

class EFObserver : Observer {
    private var notifications = hashMapOf<EFSuggestion, ArrayList<EFNotification>>()
    override fun update(notification: EFNotification) {
        if (notification.payload is EFCandidateApplicationPayload) {
            val payload = notification.payload
            notifications.putIfAbsent(payload.candidate.efSuggestion, ArrayList())
            notifications[payload.candidate.efSuggestion]!!.add(notification)
        }
    }

    fun getNotifications(efApplicationResult: EFApplicationResult): List<EFNotification> {
        return getNotifications().filter { (it.payload as EFCandidateApplicationPayload).result == efApplicationResult }
    }

    fun getNotifications(): List<EFNotification> {
        return notifications.values.flatten()
    }
}

class EFLoggerObserver(private val logger: Logger) : Observer {
    override fun update(notification: EFNotification) {
        logger.info(notification.payload.toString())
    }
}

class EFCandidatesApplicationTelemetryObserver : Observer {
    private var notifications: MutableList<EFCandidateApplicationPayload> = mutableListOf()
    override fun update(notification: EFNotification) {
        if (notification.payload is EFCandidateApplicationPayload) {
            notifications.add(notification.payload)
        }
    }

    fun getData(): List<EFCandidateApplicationPayload> {
        return notifications.toList()
    }
}