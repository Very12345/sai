package com.phoneagent.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phoneagent.app.PhoneAgentApplication

/**
 * Converts runs left active by process death into explicit paused runs. Their append-only
 * event logs stay intact and can be inspected before the user elects to resume them.
 */
class GoalRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cutoff = inputData.getLong(CUTOFF_KEY, 0L)
        if (cutoff <= 0L) return Result.failure()
        val dao = (applicationContext as PhoneAgentApplication).container.database.dao()
        dao.unfinishedSessionsBefore(cutoff).forEach { session ->
            dao.updateSessionState(session.id, "PAUSED")
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "goal-process-recovery"
        const val CUTOFF_KEY = "startup_epoch_millis"
    }
}
