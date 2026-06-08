package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.startup.Initializer
import androidx.work.WorkManagerInitializer as AndroidWorkManagerInitializer

class WorkManagerInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        RefreshScheduler.scheduleAll(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(AndroidWorkManagerInitializer::class.java)
    }

    companion object {
        fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long = 30) {
            RefreshScheduler.schedulePeriodicRefresh(context, intervalMinutes)
        }

        fun scheduleTokenRefresh(context: Context, intervalMinutes: Long = 30) {
            RefreshScheduler.scheduleTokenRefresh(context, intervalMinutes)
        }
    }
}
