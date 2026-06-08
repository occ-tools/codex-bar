package com.codexbar.android.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.RefreshScheduler
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSavedCredentials()
        _uiState.update {
            it.copy(
                refreshIntervalMinutes = prefsManager.getRefreshInterval(),
                notificationsEnabled = prefsManager.isNotificationsEnabled()
            )
        }
    }

    private fun loadSavedCredentials() {
        val accounts = AiService.entries.flatMap { service ->
            val savedAccounts = prefsManager.loadCredentialAccounts(service)
            if (savedAccounts.isEmpty()) {
                listOf(newAccountState(service, index = 1))
            } else {
                savedAccounts.map { account ->
                    CredentialAccountState(
                        service = service,
                        accountId = account.id,
                        label = account.label,
                        credential = credentialToState(account.credential),
                        isPersisted = true
                    )
                }
            }
        }
        _uiState.update { it.copy(credentialAccounts = accounts) }
    }

    fun startFreshCredentialDrafts() {
        _uiState.update {
            it.copy(
                credentialAccounts = AiService.entries.map { service ->
                    newAccountState(service, index = 1)
                }
            )
        }
    }

    fun addAccount(service: AiService) {
        _uiState.update { state ->
            val index = state.credentialAccounts.count { it.service == service } + 1
            state.copy(
                credentialAccounts = state.credentialAccounts + newAccountState(service, index)
            )
        }
    }

    fun deleteAccount(service: AiService, accountId: String) {
        val current = findAccount(service, accountId) ?: return
        if (current.isPersisted) {
            prefsManager.deleteCredential(service, accountId)
        }
        _uiState.update { state ->
            val remaining = state.credentialAccounts.filterNot {
                it.service == service && it.accountId == accountId
            }
            val withDraft = if (remaining.none { it.service == service }) {
                remaining + newAccountState(service, index = 1)
            } else {
                remaining
            }
            state.copy(credentialAccounts = withDraft)
        }
    }

    fun updateAccountLabel(service: AiService, accountId: String, value: String) {
        updateAccount(service, accountId) { it.copy(label = value, credential = it.credential.copy(validationResult = null)) }
    }

    fun updateField(service: AiService, accountId: String, field: String, value: String) {
        updateAccount(service, accountId) { account ->
            val current = account.credential
            val updated = when (field) {
                "accessToken" -> current.copy(accessToken = value, validationResult = null)
                "refreshToken" -> current.copy(refreshToken = value, validationResult = null)
                "accountId" -> current.copy(accountId = value, validationResult = null)
                "oauthClientId" -> current.copy(oauthClientId = value, validationResult = null)
                "oauthClientSecret" -> current.copy(oauthClientSecret = value, validationResult = null)
                else -> current
            }
            account.copy(credential = updated)
        }
    }

    private fun saveCredential(
        service: AiService,
        accountId: String,
        updateValidation: Boolean
    ): Boolean {
        val account = findAccount(service, accountId) ?: return false
        val built = buildCredential(service, account.credential)
        if (built.credential == null) {
            if (updateValidation && built.error != null) {
                updateCredentialState(service, accountId) {
                    it.copy(validationResult = ValidationResult.Failure(built.error))
                }
            }
            return false
        }

        prefsManager.saveCredential(
            service = service,
            accountId = accountId,
            label = account.label.ifBlank { prefsManager.defaultAccountLabel(service) },
            credential = built.credential
        )
        updateAccount(service, accountId) { it.copy(isPersisted = true) }
        return true
    }

    fun validateCredential(service: AiService, accountId: String) {
        if (!saveCredential(service, accountId, updateValidation = true)) return
        val account = prefsManager.loadCredentialAccount(service, accountId) ?: return
        val repo = repositoryFor(service)

        updateCredentialState(service, accountId) {
            it.copy(isValidating = true, validationResult = null)
        }

        viewModelScope.launch {
            val result = repo.validateCredential(account)
            val validationResult = when (result) {
                is Result.Success -> {
                    RefreshScheduler.enqueueManualQuotaRefresh(context, reason = "credential_validated")
                    ValidationResult.Success
                }
                is Result.Failure -> ValidationResult.Failure(formatAppError(result.error))
            }

            updateCredentialState(service, accountId) {
                it.copy(isValidating = false, validationResult = validationResult)
            }
        }
    }

    fun setRefreshInterval(minutes: Long) {
        prefsManager.setRefreshInterval(minutes)
        RefreshScheduler.scheduleAll(context, minutes)
        if (minutes > 0) {
            RefreshScheduler.enqueueManualQuotaRefresh(context, reason = "interval_changed")
        }
        _uiState.update { it.copy(refreshIntervalMinutes = minutes) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefsManager.setNotificationsEnabled(enabled)
        if (enabled) {
            RefreshScheduler.enqueueManualQuotaRefresh(context, reason = "notifications_enabled")
        }
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun deleteAllCredentials() {
        val current = _uiState.value
        prefsManager.deleteAllCredentials()
        _uiState.update {
            SettingsUiState(
                credentialAccounts = AiService.entries.map { service -> newAccountState(service, index = 1) },
                refreshIntervalMinutes = current.refreshIntervalMinutes,
                notificationsEnabled = current.notificationsEnabled
            )
        }
    }

    private fun credentialToState(credential: Credential): ServiceCredentialState {
        return when (credential) {
            is Credential.ClaudeCredential -> ServiceCredentialState(
                accessToken = credential.accessToken,
                refreshToken = credential.refreshToken ?: ""
            )
            is Credential.CodexCredential -> ServiceCredentialState(
                accessToken = credential.accessToken,
                refreshToken = credential.refreshToken,
                accountId = credential.accountId ?: ""
            )
            is Credential.GeminiCredential -> ServiceCredentialState(
                accessToken = credential.accessToken,
                refreshToken = credential.refreshToken,
                oauthClientId = credential.oauthClientId,
                oauthClientSecret = credential.oauthClientSecret,
                expiresAtDisplay = formatExpiryMs(credential.expiresAtMs)
            )
        }
    }

    private fun buildCredential(service: AiService, state: ServiceCredentialState): CredentialBuild {
        if (state.accessToken.isBlank()) return CredentialBuild(null, null)

        return when (service) {
            AiService.CLAUDE -> CredentialBuild(
                Credential.ClaudeCredential(
                    accessToken = state.accessToken,
                    refreshToken = state.refreshToken.ifBlank { null },
                    expiresAt = state.refreshToken
                        .takeIf { it.isNotBlank() }
                        ?.let { Instant.now().plusSeconds(CLAUDE_ACCESS_TOKEN_LIFETIME_SECONDS) }
                ),
                null
            )
            AiService.CODEX -> {
                if (state.refreshToken.isBlank()) {
                    CredentialBuild(null, "Refresh token is required")
                } else {
                    CredentialBuild(
                        Credential.CodexCredential(
                            accessToken = state.accessToken,
                            refreshToken = state.refreshToken,
                            accountId = state.accountId.ifBlank { null }
                        ),
                        null
                    )
                }
            }
            AiService.GEMINI -> {
                when {
                    state.refreshToken.isBlank() -> CredentialBuild(null, "Refresh token is required")
                    state.oauthClientId.isBlank() -> CredentialBuild(null, "OAuth Client ID is required")
                    state.oauthClientSecret.isBlank() -> CredentialBuild(null, "OAuth Client Secret is required")
                    else -> CredentialBuild(
                        Credential.GeminiCredential(
                            accessToken = state.accessToken,
                            refreshToken = state.refreshToken,
                            expiresAtMs = System.currentTimeMillis() + 3600_000,
                            oauthClientId = state.oauthClientId,
                            oauthClientSecret = state.oauthClientSecret
                        ),
                        null
                    )
                }
            }
        }
    }

    private fun newAccountState(service: AiService, index: Int): CredentialAccountState {
        return CredentialAccountState(
            service = service,
            accountId = prefsManager.createAccountId(service),
            label = prefsManager.defaultAccountLabel(service, index),
            credential = ServiceCredentialState(),
            isPersisted = false
        )
    }

    private fun findAccount(service: AiService, accountId: String): CredentialAccountState? {
        return _uiState.value.credentialAccounts.firstOrNull {
            it.service == service && it.accountId == accountId
        }
    }

    private fun updateAccount(
        service: AiService,
        accountId: String,
        transform: (CredentialAccountState) -> CredentialAccountState
    ) {
        _uiState.update { state ->
            state.copy(
                credentialAccounts = state.credentialAccounts.map {
                    if (it.service == service && it.accountId == accountId) transform(it) else it
                }
            )
        }
    }

    private fun updateCredentialState(
        service: AiService,
        accountId: String,
        transform: (ServiceCredentialState) -> ServiceCredentialState
    ) {
        updateAccount(service, accountId) { it.copy(credential = transform(it.credential)) }
    }

    private fun repositoryFor(service: AiService): QuotaRepository {
        return when (service) {
            AiService.CLAUDE -> claudeRepository
            AiService.CODEX -> codexRepository
            AiService.GEMINI -> geminiRepository
        }
    }

    private fun formatExpiryMs(expiresAtMs: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(expiresAtMs)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun formatAppError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network error: ${error.message}"
            is AppError.AuthError -> if (error.isTerminal) "Authentication failed (re-login required)" else "Authentication error"
            is AppError.RateLimited -> "Rate limited - try again later"
            is AppError.ParseError -> "Parse error: ${error.message}"
            is AppError.CredentialNotFound -> "No credentials saved"
            is AppError.ServiceUnavailable -> "Service temporarily unavailable"
        }
    }

    private data class CredentialBuild(
        val credential: Credential?,
        val error: String?
    )

    private companion object {
        const val CLAUDE_ACCESS_TOKEN_LIFETIME_SECONDS = 8 * 60 * 60L
    }
}
