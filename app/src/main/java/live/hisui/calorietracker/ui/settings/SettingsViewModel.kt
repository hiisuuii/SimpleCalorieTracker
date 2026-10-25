package live.hisui.calorietracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import live.hisui.calorietracker.data.BackupFormatException
import live.hisui.calorietracker.data.BackupRepository
import live.hisui.calorietracker.data.SettingsRepository
import live.hisui.calorietracker.domain.Settings
import live.hisui.calorietracker.platform.ReminderScheduler

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val backup: BackupRepository,
) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun update(change: Settings.() -> Settings) {
        viewModelScope.launch {
            val updated = repo.current().change()
            repo.update(updated)
            scheduler.scheduleAll(updated)
        }
    }

    fun removeReminder(id: Long) {
        update { copy(reminders = reminders.filter { it.id != id }) }
        scheduler.cancel(id)
    }

    fun exportJson(uri: Uri) = export(uri, "Backup saved") { backup.exportJson(it) }

    fun exportCsv(uri: Uri) = export(uri, "Log exported") { backup.exportCsv(it) }

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val count = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open that file.")
                    input.use { backup.importJson(it) }
                }
                "Restored $count log entries"
            } catch (e: BackupFormatException) {
                e.message.orEmpty()
            } catch (e: IOException) {
                "Couldn't read that file."
            }
            messageChannel.send(message)
        }
    }

    private fun export(uri: Uri, success: String, write: suspend (OutputStream) -> Unit) {
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Can't open that file.")
                    out.use { write(it) }
                }
                success
            } catch (e: IOException) {
                "Couldn't write the file."
            }
            messageChannel.send(message)
        }
    }
}
