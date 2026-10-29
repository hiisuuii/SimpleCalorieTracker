package live.hisui.calorietracker.platform

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

// Written through MediaStore so the files land in a public folder that Files and My Files can browse without
// any storage permission. Below API 29 that route doesn't exist, so logging is unavailable there.
@Singleton
class LogFiles @Inject constructor(@ApplicationContext context: Context) {
    private val resolver = context.contentResolver
    private val collection: Uri get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun create(name: String): OutputStream? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            // Not text/plain: MediaStore would append ".txt" to the .log name.
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return resolver.openOutputStream(uri, "wa")
    }

    fun uris(): List<Uri> = query().map { it.second }

    fun prune(keep: Int) {
        query().drop(keep).forEach { (_, uri) -> resolver.delete(uri, null, null) }
    }

    private fun query(): List<Pair<String, Uri>> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val found = mutableListOf<Pair<String, Uri>>()
        resolver.query(collection, projection, selection, arrayOf("$FOLDER%"), null)?.use { c ->
            while (c.moveToNext()) {
                found += c.getString(1) to ContentUris.withAppendedId(collection, c.getLong(0))
            }
        }
        // File names start with the session date and time, so a descending name sort is newest first.
        return found.sortedByDescending { it.first }
    }

    companion object {
        const val FOLDER = "Documents/CalorieTracker/logs/"
        const val KEEP = 5
    }
}
