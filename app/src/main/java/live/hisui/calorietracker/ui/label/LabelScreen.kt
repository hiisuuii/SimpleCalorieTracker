package live.hisui.calorietracker.ui.label

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.guava.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, viewModel: LabelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan label") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.stage) {
                LabelStage.CAPTURE -> CaptureStage(state, viewModel)
                LabelStage.PROCESSING -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                LabelStage.REVIEW -> ReviewStage(state, viewModel, onSaved)
            }
        }
    }
}

@Composable
private fun CaptureStage(state: LabelUiState, viewModel: LabelViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::onPhoto) }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (granted) {
            CameraCapture(
                onCaptured = viewModel::onPhoto,
                onFailed = viewModel::onCaptureFailed,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The camera is only used to photograph a nutrition label. The photo is read on this phone and deleted after you save.")
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }
        OutlinedButton(onClick = pickPhoto, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Choose a photo instead") }
    }
}

@Composable
private fun CameraCapture(onCaptured: (Uri) -> Unit, onFailed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Button(
            onClick = {
                val file = File(context.cacheDir, "label-${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) = onCaptured(Uri.fromFile(file))
                        override fun onError(exception: ImageCaptureException) = onFailed()
                    },
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        ) { Text("Take photo") }
    }
}

@Composable
private fun ReviewStage(state: LabelUiState, viewModel: LabelViewModel, onSaved: (Long) -> Unit) {
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = state.photo,
            contentDescription = "Label photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Text("Check every number against the photo before saving.", style = MaterialTheme.typography.bodyMedium)
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.warning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        OutlinedTextField(
            state.name,
            { v -> viewModel.edit { copy(name = v) } },
            label = { Text("Food name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state.servingText,
                { v -> viewModel.edit { copy(servingText = v) } },
                label = { Text("Serving (2/3 cup)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state.servingGrams,
                { v -> viewModel.edit { copy(servingGrams = v) } },
                label = { Text("Serving grams") },
                keyboardOptions = numeric,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            state.kcal,
            { v -> viewModel.edit { copy(kcal = v) } },
            label = { Text("Calories per serving") },
            keyboardOptions = numeric,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state.protein,
                { v -> viewModel.edit { copy(protein = v) } },
                label = { Text("Protein g") },
                keyboardOptions = numeric,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state.carbs,
                { v -> viewModel.edit { copy(carbs = v) } },
                label = { Text("Carbs g") },
                keyboardOptions = numeric,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state.fat,
                { v -> viewModel.edit { copy(fat = v) } },
                label = { Text("Fat g") },
                keyboardOptions = numeric,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Button(onClick = { viewModel.save(onSaved) }, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) { Text("Save food") }
        TextButton(onClick = viewModel::retake, modifier = Modifier.fillMaxWidth()) { Text("Retake photo") }
    }
}
