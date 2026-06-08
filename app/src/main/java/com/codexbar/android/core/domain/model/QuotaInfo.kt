package com.codexbar.android.core.domain.model

import java.time.Instant

data class QuotaInfo(
    val service: AiService,
    val accountId: String = DEFAULT_ACCOUNT_ID,
    val accountLabel: String? = null,
    val windows: List<UsageWindow>,
    val extraUsage: ExtraUsage?,
    val tier: String? = null,
    val fetchedAt: Instant
) {
    val displayName: String
        get() = accountLabel?.takeIf { it.isNotBlank() } ?: service.displayName

    companion object {
        const val DEFAULT_ACCOUNT_ID = "default"
    }
}

data class UsageWindow(
    val label: String,
    val utilization: Double, // 0.0 ~ 1.0
    val resetsAt: Instant?
)

data class ExtraUsage(
    val isEnabled: Boolean,
    val monthlyLimit: Double,
    val usedCredits: Double,
    val utilization: Double,
    val currency: String
)
