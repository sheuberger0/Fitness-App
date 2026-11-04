package com.sheuberger.fitness.nutrition

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
// ML Kit hat eine Klasse "Barcode", und unser Objekt Barcode (Prüfziffer) liegt im selben Paket.
// Ein Import ohne neuen Namen würde unseres in dieser Datei verdecken, deshalb heißt die von ML Kit hier anders.
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode

/** Ergebnis eines Scans. */
sealed interface ScanResult {
    data class Code(val value: String) : ScanResult
    data object Cancelled : ScanResult
    data class Failed(val message: String) : ScanResult
}

/**
 * Öffnet den Barcode-Scanner der Google Play-Dienste ("Google code scanner").
 *
 * Die Kamera läuft in den Play-Diensten, nicht in dieser App. Deshalb braucht die App keine
 * Kamera-Berechtigung und bekommt nur die erkannten Ziffern. Der Scanner selbst ist ein Zusatzmodul der
 * Play-Dienste; fehlt es noch, fordert die App es an und meldet, dass es gerade heruntergeladen wird.
 *
 * [scan] ruft `onResult` genau einmal auf, im Hauptthread (dort landen die Rückrufe der Play-Dienste).
 * Der Code ist noch ungeprüft: Das macht die UI mit [Barcode.validate].
 */
class BarcodeScanner(private val context: Context) {

    fun scan(onResult: (ScanResult) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            // Nur die Codes auf Lebensmitteln. So greift der Scanner nicht nach QR-Codes oder Paketnummern daneben.
            .setBarcodeFormats(MlKitBarcode.FORMAT_EAN_13, MlKitBarcode.FORMAT_EAN_8, MlKitBarcode.FORMAT_UPC_A)
            // Zoomt selbst heran, wenn der Code klein oder weit weg ist.
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)

        ModuleInstall.getClient(context).areModulesAvailable(scanner)
            .addOnSuccessListener { availability ->
                if (availability.areModulesAvailable()) start(scanner, onResult) else requestDownload(scanner, onResult)
            }
            // Die Abfrage selbst ging schief (z. B. alte Play-Dienste). Dann einfach starten: Der Scanner meldet
            // dann selbst, was fehlt.
            .addOnFailureListener { start(scanner, onResult) }
    }

    private fun start(scanner: GmsBarcodeScanner, onResult: (ScanResult) -> Unit) {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                onResult(if (value.isNullOrBlank()) ScanResult.Failed(UNREADABLE) else ScanResult.Code(value))
            }
            // Zurück-Taste oder Schließen im Scanner.
            .addOnCanceledListener { onResult(ScanResult.Cancelled) }
            .addOnFailureListener { e ->
                val code = (e as? MlKitException)?.errorCode
                if (code == MlKitException.CODE_SCANNER_UNAVAILABLE) {
                    requestDownload(scanner, onResult)
                } else {
                    onResult(resultForError(code))
                }
            }
    }

    /**
     * Bittet die Play-Dienste, das Scanner-Modul zu laden. Das läuft im Hintergrund weiter, auch wenn du die
     * App schließt. Auf das Ende warten lohnt sich nicht: Beim nächsten Tippen ist es meist da.
     */
    private fun requestDownload(scanner: GmsBarcodeScanner, onResult: (ScanResult) -> Unit) {
        ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
        onResult(ScanResult.Failed(DOWNLOADING))
    }

    internal companion object {
        const val DOWNLOADING = "Der Scanner wird gerade heruntergeladen. Bitte gleich nochmal versuchen."
        const val UNREADABLE = "Der Code ließ sich nicht lesen. Bitte nochmal scannen oder die Ziffern eintippen."
        const val NO_CAMERA = "Ohne Kamerazugriff kann der Scanner nichts lesen. Gib die Ziffern von Hand ein."
        const val OLD_PLAY_SERVICES =
            "Die Google Play-Dienste sind zu alt für den Scanner. Bitte aktualisieren oder die Ziffern von Hand eingeben."
        const val GENERIC = "Der Scanner hat nicht funktioniert. Gib die Ziffern von Hand ein."

        /** Übersetzt einen Fehlercode von ML Kit in ein Ergebnis. null heißt: kein ML-Kit-Fehler. */
        fun resultForError(errorCode: Int?): ScanResult = when (errorCode) {
            // Manche Versionen melden das Schließen als Fehler statt als Abbruch.
            MlKitException.CODE_SCANNER_CANCELLED -> ScanResult.Cancelled
            // Doppelt getippt: Der erste Scanner ist schon offen und liefert das Ergebnis. Der zweite Aufruf
            // bleibt still, statt eine Fehlermeldung über den laufenden Scan zu legen.
            MlKitException.CODE_SCANNER_TASK_IN_PROGRESS -> ScanResult.Cancelled
            MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED -> ScanResult.Failed(NO_CAMERA)
            MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD -> ScanResult.Failed(OLD_PLAY_SERVICES)
            else -> ScanResult.Failed(GENERIC)
        }
    }
}
