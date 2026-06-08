package com.codexbar.android.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codexbar.android.core.workmanager.RefreshScheduler

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == QuotaNotificationService.ACTION_REFRESH) {
            RefreshScheduler.enqueueManualQuotaRefresh(context, reason = "notification")
        }
    }
}
