package com.example.timekeep_work.scanner

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Adapts Google Code Scanner's callback API to the app's suspend-based [ScannerGateway]. */
class GoogleCodeScannerGateway(
    private val context: Context,
    private val identifierNormalizer: IdentifierNormalizer,
) : ScannerGateway {
    private val scanner by lazy {
        val options = GmsBarcodeScannerOptions.Builder()
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    override suspend fun scan(): ScanResult {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            return ScanResult.ScannerUnavailable
        }

        return try {
            awaitScan()
        } catch (_: CancellationException) {
            ScanResult.Cancellation
        } catch (error: MlKitException) {
            if (error.errorCode == MlKitException.UNAVAILABLE) {
                ScanResult.ScannerModuleUnavailable
            } else {
                ScanResult.UnexpectedFailure
            }
        } catch (_: Exception) {
            ScanResult.UnexpectedFailure
        }
    }

    /** Completes once for the first success, cancellation, or failure callback. */
    private suspend fun awaitScan(): ScanResult = suspendCancellableCoroutine { continuation ->
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                if (!continuation.isActive) return@addOnSuccessListener
                val result = when (val normalized = identifierNormalizer.normalize(barcode.rawValue)) {
                    is IdentifierNormalizationResult.Success -> ScanResult.Success(normalized.identifier)
                    is IdentifierNormalizationResult.Invalid -> ScanResult.InvalidValue(normalized.reason)
                }
                continuation.resume(result)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(ScanResult.Cancellation)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resume(error.toScanResult())
            }
    }
}

private fun Exception.toScanResult(): ScanResult = when (this) {
    is CancellationException -> ScanResult.Cancellation
    is MlKitException -> if (errorCode == MlKitException.UNAVAILABLE) {
        ScanResult.ScannerModuleUnavailable
    } else {
        ScanResult.UnexpectedFailure
    }
    else -> ScanResult.UnexpectedFailure
}
