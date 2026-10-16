package com.example.timekeep_work.scanner

/** Abstraction over the device scanner so the workflow can be tested without Google Play services. */
interface ScannerGateway {
    suspend fun scan(): ScanResult
}

/** Outcomes the UI must handle when one scanner session finishes. */
sealed interface ScanResult {
    data class Success(val identifier: NormalizedIdentifier) : ScanResult

    data object Cancellation : ScanResult

    data object ScannerUnavailable : ScanResult

    data object ScannerModuleUnavailable : ScanResult

    data class InvalidValue(val reason: InvalidIdentifierReason) : ScanResult

    data object UnexpectedFailure : ScanResult
}
