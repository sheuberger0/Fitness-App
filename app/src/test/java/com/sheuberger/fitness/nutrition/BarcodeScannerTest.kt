package com.sheuberger.fitness.nutrition

import com.google.mlkit.common.MlKitException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Prüft, wie Fehler des Google-Scanners in der App ankommen. Der Scanner selbst läuft nur auf dem Handy. */
class BarcodeScannerTest {

    @Test
    fun schliessenUndDoppeltesTippenSindKeinFehler() {
        assertEquals(ScanResult.Cancelled, BarcodeScanner.resultForError(MlKitException.CODE_SCANNER_CANCELLED))
        assertEquals(ScanResult.Cancelled, BarcodeScanner.resultForError(MlKitException.CODE_SCANNER_TASK_IN_PROGRESS))
    }

    @Test
    fun bekannteFehlerHabenEigeneMeldungen() {
        assertEquals(
            ScanResult.Failed(BarcodeScanner.NO_CAMERA),
            BarcodeScanner.resultForError(MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED),
        )
        assertEquals(
            ScanResult.Failed(BarcodeScanner.OLD_PLAY_SERVICES),
            BarcodeScanner.resultForError(MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD),
        )
    }

    @Test
    fun unbekannteFehler_allgemeineMeldungMitHinweisAufsEintippen() {
        assertEquals(ScanResult.Failed(BarcodeScanner.GENERIC), BarcodeScanner.resultForError(null))
        assertEquals(ScanResult.Failed(BarcodeScanner.GENERIC), BarcodeScanner.resultForError(MlKitException.INTERNAL))
        assertEquals(ScanResult.Failed(BarcodeScanner.GENERIC), BarcodeScanner.resultForError(-12345))
    }
}
