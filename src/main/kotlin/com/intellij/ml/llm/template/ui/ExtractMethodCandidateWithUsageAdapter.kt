package com.intellij.ml.llm.template.ui

import com.intellij.usages.UsageInfo2UsageAdapter




class ExtractMethodCandidateWithUsageAdapter {
    private var myCandidate: PhpExtractMethodCandidate? = null
    private var myUsageAdapter: UsageInfo2UsageAdapter? = null

    fun PhpExtractMethodCandidateWithUsageAdapter(
        candidate: PhpExtractMethodCandidate?,
        adapter: UsageInfo2UsageAdapter?
    ) {
        myCandidate = candidate
        myUsageAdapter = adapter
    }

    fun getCandidate(): PhpExtractMethodCandidate? {
        return myCandidate
    }

    fun getUsageAdapter(): UsageInfo2UsageAdapter? {
        return myUsageAdapter
    }
}