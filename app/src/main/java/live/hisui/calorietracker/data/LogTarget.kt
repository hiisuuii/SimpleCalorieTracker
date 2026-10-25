package live.hisui.calorietracker.data

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// The day new entries are logged to when the user started logging from a past day; null means "now".
// Held outside the screens because logging spans several of them (search, amount, barcode, label...).
@Singleton
class LogTarget @Inject constructor() {
    @Volatile
    var date: LocalDate? = null
}
