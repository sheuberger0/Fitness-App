package com.sheuberger.fitness.nutrition

import android.content.Context

/** Ergebnis eines Scans. */
sealed interface ScanResult {
    data class Code(val value: String) : ScanResult
    data object Cancelled : ScanResult
    data class Failed(val message: String) : ScanResult
}

/**
 * Öffnet den Barcode-Scanner von Google Play-Diensten. Platzhalter: Die eigentliche Anbindung kommt in
 * einem eigenen Schritt.
 */
class BarcodeScanner(private val context: Context) {
    fun scan(onResult: (ScanResult) -> Unit) {
        onResult(ScanResult.Failed("Der Scanner ist noch nicht eingebaut. Gib die Ziffern von Hand ein."))
    }
}
