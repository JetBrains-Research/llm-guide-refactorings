package com.intellij.ml.llm.template.utils

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion

enum class EFApplicationResult(s: String) {
    OK("OK"),
    FAIL("FAIL")
}

data class EFNotification(
    var result: EFApplicationResult,
    var candidate: EFCandidate,
    var reason: String
)

interface Observer {
    fun update(notification: EFNotification)
}

interface Observable {
    fun addObserver(observer: Observer)
    fun removeObserver(observer: Observer)
    fun notifyObservers(notification: EFNotification)
}

class EFObserver : Observer {
    private var notifications = hashMapOf<EFSuggestion, ArrayList<EFNotification>>()
    override fun update(notification: EFNotification) {
        notifications.putIfAbsent(notification.candidate.efSuggestion, ArrayList())
        notifications[notification.candidate.efSuggestion]!!.add(notification)
    }

    fun getNotifications(efApplicationResult: EFApplicationResult): List<EFNotification> {
        return getNotifications().filter { it.result == efApplicationResult }
    }

    fun getNotifications(): List<EFNotification> {
        return notifications.values.flatten()
    }
}
