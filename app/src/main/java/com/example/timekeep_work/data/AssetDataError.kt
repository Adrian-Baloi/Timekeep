package com.example.timekeep_work.data

/** Stable error categories exposed by the data layer to the technician-facing UI. */
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

    data object AuthorizationRejected : AssetDataError {
        override val technicianMessage =
            "Halo rejected this request after refreshing the access token. Sign out and sign in again to apply the current scopes. " +
                "If it still fails, verify the API application permissions and the signed-in technician's Halo role."
        override val retryable = false
    }

    data object Forbidden : AssetDataError {
        override val technicianMessage =
            "Your Halo sign-in is valid, but the API application or your Halo role does not allow this operation. " +
                "Ask a Halo administrator to verify the least-privilege asset, user, and site permissions, then sign out and sign in again."
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

/** Wraps a low-level failure with the category and retry guidance understood by the UI. */
class AssetRepositoryException(
    val error: AssetDataError,
    cause: Throwable? = null,
) : Exception(error.technicianMessage, cause)
