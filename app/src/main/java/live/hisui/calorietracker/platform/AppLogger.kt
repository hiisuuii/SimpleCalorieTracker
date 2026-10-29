package live.hisui.calorietracker.platform

import android.util.Log
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

// The ring buffer is always filled, so a crash report carries the lead-up even when file logging is off.
@Singleton
class AppLogger @Inject constructor(private val files: LogFiles) : Timber.Tree() {
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "app-log").apply { isDaemon = true } }
    private val recent = ArrayDeque<String>()
    private var sink: OutputStream? = null

    val available: Boolean get() = files.available

    fun install() {
        Timber.plant(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            reportCrash(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!files.available) return
        writer.execute {
            if (enabled && sink == null) {
                sink = open("")
                sink?.let { Timber.i("Logging started") }
            } else if (!enabled && sink != null) {
                sink?.closeQuietly()
                sink = null
            }
        }
    }

    fun logFiles() = files.uris()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val line = format(priority, tag, message, t)
        synchronized(recent) {
            recent.addLast(line)
            if (recent.size > RECENT_LINES) recent.removeFirst()
        }
        writer.execute { sink?.writeLine(line) }
    }

    private fun reportCrash(thread: Thread, error: Throwable) {
        val line = format(Log.ERROR, "Crash", "Uncaught exception on ${thread.name}", error)
        val task = writer.submit {
            val out = sink ?: open("-crash")
            if (sink == null) synchronized(recent) { recent.forEach { out?.writeLine(it) } }
            out?.writeLine(line)
            if (sink == null) out?.closeQuietly()
        }
        runCatching { task.get(CRASH_WAIT_SECONDS, TimeUnit.SECONDS) }
    }

    private fun open(suffix: String): OutputStream? = runCatching {
        files.prune(LogFiles.KEEP - 1)
        files.create("${LocalDateTime.now().format(NAME_FORMAT)}$suffix.log")
    }.getOrNull()

    private fun format(priority: Int, tag: String?, message: String, t: Throwable?): String {
        val level = "VDIWEA".getOrElse(priority - Log.VERBOSE) { '?' }
        val trace = t?.let { "\n" + Log.getStackTraceString(it) }.orEmpty()
        return "${LocalDateTime.now().format(LINE_FORMAT)} $level/${tag ?: "App"}: $message$trace"
    }

    private fun OutputStream.writeLine(line: String) {
        runCatching {
            write((line + "\n").toByteArray())
            flush()
        }
    }

    private fun OutputStream.closeQuietly() = runCatching { close() }

    private companion object {
        const val RECENT_LINES = 200
        const val CRASH_WAIT_SECONDS = 3L
        val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        val LINE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
