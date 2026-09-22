package com.example.runningapp.health

import com.example.runningapp.storage.RunDao
import com.example.runningapp.storage.decode
import com.example.runningapp.domain.healthRun
import kotlinx.coroutines.CancellationException

/** Durable intent precedes external writes; deletion wins over an in-flight export. */
class HealthEngine(private val dao: RunDao, private val port: HealthPort,
    private val owner: ()->String?, private val enabled: ()->Boolean) {
    suspend fun runOnce(): Boolean {
        val account=owner()
        if(enabled())dao.seedHealth(account)
        for(op in dao.pendingHealth(account)) {
            if(op.state=="DELETE") {
                port.delete(op.runId)
                dao.markHealth(op.runId,"DELETE","DELETED")
                continue
            }
            if(!enabled() || owner()!=account)continue
            val row=dao.get(op.runId)
            if(row==null) {dao.deleteHealth(op.runId);continue}
            if(row.cloudOwnerId!=null && row.cloudOwnerId!=account)continue
            val projection=try {healthRun(row.decode().snapshot)} catch(cancel:CancellationException){throw cancel}
                catch(_:IllegalArgumentException){dao.markHealth(op.runId,"PENDING","BLOCKED","This run has zero duration or inconsistent recorded times; it is still safe in WAYiRUN.");continue}
            port.write(projection,row.zoneId)
            dao.markHealth(op.runId,"PENDING","DONE")
        }
        return dao.pendingHealth(account).any {it.state=="DELETE" || enabled()}
    }
}
