package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.CredentialAccount
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.gemini.GeminiDto
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import com.codexbar.android.core.security.EncryptedPrefsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val claudeTokenRefreshService: ClaudeTokenRefreshService,
    private val codexTokenRefreshService: CodexTokenRefreshService,
    private val geminiTokenRefreshService: GeminiTokenRefreshService,
    private val prefsManager: EncryptedPrefsManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val outcomes = coroutineScope {
            AiService.entries
                .flatMap { prefsManager.loadCredentialAccounts(it) }
                .map { account -> async { refreshIfNeeded(account) } }
                .awaitAll()
        }

        return if (outcomes.any { it == RefreshOutcome.RETRY }) Result.retry() else Result.success()
    }

    /**
     * Returns OK if refresh was not needed or succeeded, RETRY for transient failures,
     * and TERMINAL when stored credentials need user action before refresh can work.
     */
    private suspend fun refreshIfNeeded(account: CredentialAccount): RefreshOutcome {
        return when (val credential = account.credential) {
            is Credential.ClaudeCredential -> refreshClaude(account, credential)
            is Credential.CodexCredential -> refreshCodex(account, credential)
            is Credential.GeminiCredential -> refreshGemini(account, credential)
        }
    }

    private suspend fun refreshClaude(
        account: CredentialAccount,
        credential: Credential.ClaudeCredential
    ): RefreshOutcome {
        val expiresAt = credential.expiresAt ?: return RefreshOutcome.OK
        // Refresh if within 10 minutes of expiry
        if (Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) return RefreshOutcome.OK

        val refreshToken = credential.refreshToken ?: return RefreshOutcome.TERMINAL
        return try {
            val response = claudeTokenRefreshService.refreshToken(refreshToken = refreshToken)
            if (response.isSuccessful) {
                val body = response.body() ?: return RefreshOutcome.RETRY
                prefsManager.saveCredential(
                    AiService.CLAUDE,
                    account.id,
                    account.label,
                    Credential.ClaudeCredential(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: refreshToken,
                        expiresAt = Instant.now().plusSeconds(body.expiresIn.toLong()),
                        scopes = credential.scopes,
                        rateLimitTier = credential.rateLimitTier
                    )
                )
                RefreshOutcome.OK
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                if (isTerminalOAuthFailure(response.code(), errorBody)) {
                    RefreshOutcome.TERMINAL
                } else {
                    RefreshOutcome.RETRY
                }
            }
        } catch (_: Exception) {
            RefreshOutcome.RETRY
        }
    }

    private suspend fun refreshCodex(
        account: CredentialAccount,
        credential: Credential.CodexCredential
    ): RefreshOutcome {
        // Codex has no expiry field - always attempt a proactive refresh
        return try {
            val request = CodexDto.TokenRefreshRequest(refreshToken = credential.refreshToken)
            val response = codexTokenRefreshService.refreshToken(request)
            if (response.isSuccessful) {
                val body = response.body() ?: return RefreshOutcome.RETRY
                prefsManager.saveCredential(
                    AiService.CODEX,
                    account.id,
                    account.label,
                    Credential.CodexCredential(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: credential.refreshToken,
                        accountId = credential.accountId
                    )
                )
                RefreshOutcome.OK
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                val isTerminal = CodexDto.TERMINAL_ERROR_CODES.any { errorBody.contains(it) }
                if (isTerminal) {
                    prefsManager.deleteCredential(AiService.CODEX, account.id)
                }
                if (isTerminal) RefreshOutcome.TERMINAL else RefreshOutcome.RETRY
            }
        } catch (_: Exception) {
            RefreshOutcome.RETRY
        }
    }

    private suspend fun refreshGemini(
        account: CredentialAccount,
        credential: Credential.GeminiCredential
    ): RefreshOutcome {
        // Refresh if within 10 minutes of expiry
        if (System.currentTimeMillis() < credential.expiresAtMs - REFRESH_BUFFER_SECONDS * 1000) return RefreshOutcome.OK

        return try {
            val request = GeminiDto.TokenRefreshRequest(
                refreshToken = credential.refreshToken,
                clientId = credential.oauthClientId,
                clientSecret = credential.oauthClientSecret
            )
            val response = geminiTokenRefreshService.refreshToken(request)
            if (response.isSuccessful) {
                val body = response.body() ?: return RefreshOutcome.RETRY
                val expiresIn = body.expiresIn ?: 3600
                prefsManager.saveCredential(
                    AiService.GEMINI,
                    account.id,
                    account.label,
                    Credential.GeminiCredential(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: credential.refreshToken,
                        expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L),
                        oauthClientId = credential.oauthClientId,
                        oauthClientSecret = credential.oauthClientSecret
                    )
                )
                RefreshOutcome.OK
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                if (isTerminalOAuthFailure(response.code(), errorBody)) {
                    RefreshOutcome.TERMINAL
                } else {
                    RefreshOutcome.RETRY
                }
            }
        } catch (_: Exception) {
            RefreshOutcome.RETRY
        }
    }

    private fun isTerminalOAuthFailure(statusCode: Int, errorBody: String): Boolean {
        if (statusCode != 400 && statusCode != 401) return false
        val lower = errorBody.lowercase()
        return lower.contains("invalid_grant") || lower.contains("invalid_client")
    }

    private enum class RefreshOutcome {
        OK,
        RETRY,
        TERMINAL
    }

    companion object {
        /** Refresh buffer: refresh tokens that expire within this many seconds. */
        const val REFRESH_BUFFER_SECONDS = 600L // 10 minutes
    }
}
