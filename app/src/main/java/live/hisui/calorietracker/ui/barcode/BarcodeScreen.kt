package live.hisui.calorietracker.ui.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.BarcodeRepository
import live.hisui.calorietracker.data.BarcodeResult
import live.hisui.calorietracker.platform.BarcodeScanner
import live.hisui.calorietracker.platform.ScanOutcome

sealed interface BarcodeUiState {
    data object Idle : BarcodeUiState
    data object Looking : BarcodeUiState
    data class NotFound(val barcode: String) : BarcodeUiState
    data class Error(val message: String) : BarcodeUiState
}

@HiltViewModel
class BarcodeViewModel @Inject constructor(private val repository: BarcodeRepository) : ViewModel() {
    private val _state = MutableStateFlow<BarcodeUiState>(BarcodeUiState.Idle)
    val state: StateFlow<BarcodeUiState> = _state.asStateFlow()

    fun lookup(raw: String, onFound: (Long) -> Unit) {
        _state.value = BarcodeUiState.Looking
        viewModelScope.launch {
            when (val result = repository.lookup(raw)) {
                is BarcodeResult.Found -> onFound(result.food.id)
                is BarcodeResult.NotFound -> _state.value = BarcodeUiState.NotFound(result.barcode)
                is BarcodeResult.Failed -> _state.value = BarcodeUiState.Error(result.message)
                BarcodeResult.Invalid -> _state.value = BarcodeUiState.Error("That isn't a valid barcode. Check the digits.")
            }
        }
    }

    fun scannerUnavailable() {
        _state.value = BarcodeUiState.Error("The scanner isn't available on this device. Enter the barcode below.")
    }

    fun reset() {
        _state.value = BarcodeUiState.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    onBack: () -> Unit,
    onFound: (Long) -> Unit,
    onCreateFood: (barcode: String) -> Unit,
    onScanLabel: (barcode: String) -> Unit,
    viewModel: BarcodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manual by remember { mutableStateOf("") }

    suspend fun scan() {
        when (val outcome = BarcodeScanner.scan(context)) {
            is ScanOutcome.Code -> viewModel.lookup(outcome.raw, onFound)
            ScanOutcome.Cancelled -> viewModel.reset()
            ScanOutcome.Unavailable -> viewModel.scannerUnavailable()
        }
    }
    LaunchedEffect(Unit) { scan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan barcode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                BarcodeUiState.Looking -> CircularProgressIndicator()
                is BarcodeUiState.NotFound -> {
                    Text("No product found for ${s.barcode}.", style = MaterialTheme.typography.titleMedium)
                    Text("Add it yourself and the next scan will find it instantly.")
                    Button(onClick = { onScanLabel(s.barcode) }, modifier = Modifier.fillMaxWidth()) { Text("Scan the nutrition label") }
                    OutlinedButton(onClick = { onCreateFood(s.barcode) }, modifier = Modifier.fillMaxWidth()) { Text("Enter nutrition manually") }
                }
                is BarcodeUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                BarcodeUiState.Idle -> Unit
            }
            Button(onClick = { scope.launch { scan() } }, modifier = Modifier.fillMaxWidth()) { Text("Scan") }
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("Or type the barcode digits") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { viewModel.lookup(manual, onFound) },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Look up") }
        }
    }
}
