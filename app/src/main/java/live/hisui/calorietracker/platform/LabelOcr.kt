package live.hisui.calorietracker.platform

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object LabelOcr {
    suspend fun recognize(context: Context, photo: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            // fromFilePath applies the EXIF rotation CameraX stores instead of rotating pixels.
            recognizer.process(InputImage.fromFilePath(context, photo)).await().text
        } finally {
            recognizer.close()
        }
    }
}
