package com.example.runningapp.tracking

import androidx.lifecycle.ViewModel

/** Activity recreation retains this state; saved external/photo workflow IDs permit exact-run returns. */
class RunEntryState : ViewModel() {
    var needsEntry = true
    var externalRunId: String? = null
    var photoRunId: String? = null
    var recreationRunId: String? = null

    fun stopped(changingConfigurations: Boolean, finishedRunId: String?) {
        if (changingConfigurations) recreationRunId = finishedRunId else needsEntry = true
    }

    fun resumed(): String? {
        val restore = photoRunId ?: externalRunId ?: recreationRunId
        externalRunId = null
        recreationRunId = null
        needsEntry = false
        return restore
    }
}
