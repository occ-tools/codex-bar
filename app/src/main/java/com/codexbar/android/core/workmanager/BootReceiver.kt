package com.codexbar.android.core.workmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED && !context.isUserUnlocked()) {
            return
        }

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                RefreshScheduler.scheduleAll(context)
                RefreshScheduler.enqueueManualQuotaRefresh(context, reason = "boot")
            }
        }
    }

    private fun Context.isUserUnlocked(): Boolean {
        val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
        return userManager?.isUserUnlocked ?: true
    }
}
