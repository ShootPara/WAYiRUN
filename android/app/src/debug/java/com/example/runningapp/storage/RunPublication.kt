package com.example.runningapp.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Intent is retained separately from the last server observation and photo upload. */
@Entity(tableName = "run_publications", foreignKeys = [ForeignKey(entity = StoredRun::class,
    parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)])
data class RunPublication(
    @PrimaryKey val runId: String,
    val ownerId: String?,
    val known: Boolean = false,
    val shared: Boolean = false,
    val photoVisible: Boolean = true,
    val revision: Long = 0,
    val publicUrl: String? = null,
    val wantShared: Boolean? = null,
    val wantPhoto: Boolean? = null,
    val intentId: String = "",
    val requestJson: String? = null,
    val error: String? = null,
)
