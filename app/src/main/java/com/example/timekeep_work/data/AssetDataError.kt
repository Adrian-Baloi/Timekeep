package com.example.timekeep_work.data

sealed interface AssetDataError {
    val technicianMessage: String
    val retryable: Boolean

    data object NoNetwork : AssetDataError {
        override val technicianMessage = "No network connection. Check the connection and try again."
        override val retryable = true
    }

    data object Timeout : AssetDataError {
        override val technicianMessage = "Halo ITSM did not respond in time. Try again."
        override val retryable = true
    }

    data object Unauthenticated : AssetDataError {
        override val technicianMessage = "Your Halo ITSM session is not valid. Sign in again."
        override val retryable = false
    }

    data object Forbidden : AssetDataError {
        override val technicianMessage = "Your Halo ITSM account is not permitted to perform this action."
        override val retryable = false
    }

    data object NotFound : AssetDataError {
        override val technicianMessage = "The requested Halo ITSM asset was not found."
        override val retryable = false
    }

    data object ValidationFailure : AssetDataError {
        override val technicianMessage = "Halo ITSM could not accept the asset details. Review the change and try again."
        override val retryable = false
    }

    data object ServerFailure : AssetDataError {
        override val technicianMessage = "Halo ITSM is temporarily unavailable. Try again later."
        override val retryable = true
    }

    data object MalformedResponse : AssetDataError {
        override val technicianMessage = "Halo ITSM returned asset data the app could not read."
        override val retryable = false
    }

    data object Unexpected : AssetDataError {
        override val technicianMessage = "The asset request could not be completed. No changes were made."
        override val retryable = false
    }
}

class AssetRepositoryException(
    val error: AssetDataError,
    cause: Throwable? = null,
) : Exception(error.technicianMessage, cause)
