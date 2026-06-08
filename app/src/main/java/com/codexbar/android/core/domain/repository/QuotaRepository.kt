package com.codexbar.android.core.domain.repository

import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.CredentialAccount
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result

interface QuotaRepository {
    suspend fun fetchQuota(): Result<QuotaInfo, AppError>
    suspend fun fetchQuota(account: CredentialAccount): Result<QuotaInfo, AppError>

    suspend fun validateCredential(): Result<Unit, AppError> {
        return when (val result = fetchQuota()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }

    suspend fun validateCredential(account: CredentialAccount): Result<Unit, AppError> {
        return when (val result = fetchQuota(account)) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
