package com.codexbar.android.core.domain.model

data class CredentialAccount(
    val id: String,
    val service: AiService,
    val label: String,
    val credential: Credential
) {
    val displayName: String
        get() = label.takeIf { it.isNotBlank() } ?: service.displayName
}
