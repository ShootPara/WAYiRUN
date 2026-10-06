package com.example.runningapp.tracking

import androidx.lifecycle.ViewModel

/** Activity recreation retains this state; only an outstanding external return survives process death. */
class RunEntryState : ViewModel() {
    var needsEntry = true
    var externalRunId: String? = null
    var recreationRunId: String? = null

    fun stopped(changingConfigurations: Boolean, finishedRunId: String?) {
        if (changingConfigurations) recreationRunId = finishedRunId else needsEntry = true
    }

    fun resumed(): String? {
        val restore = externalRunId ?: recreationRunId
        externalRunId = null
        recreationRunId = null
        needsEntry = false
        return restore
    }
}
