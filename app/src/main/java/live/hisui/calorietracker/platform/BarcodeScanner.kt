package live.hisui.calorietracker.platform

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

sealed interface ScanOutcome {
    data class Code(val raw: String) : ScanOutcome
    data object Cancelled : ScanOutcome
    data object Unavailable : ScanOutcome
}

object BarcodeScanner {
    // The code scanner runs in Google Play services, so it needs no camera permission.
    suspend fun scan(context: Context): ScanOutcome {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
            .enableAutoZoom()
            .build()
        return try {
            val raw = GmsBarcodeScanning.getClient(context, options).startScan().await().rawValue
            if (raw == null) ScanOutcome.Cancelled else ScanOutcome.Code(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MlKitException) {
            if (e.errorCode == MlKitException.CODE_SCANNER_CANCELLED) ScanOutcome.Cancelled else ScanOutcome.Unavailable
        } catch (e: Exception) {
            ScanOutcome.Unavailable
        }
    }
}
