package com.example.hydraulicsensorapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Ekran pobierania danych Offline Recording z SensorBox
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOfflineDataScreen(
    onBackClick: () -> Unit,
    onCheckMode: (callback: (Char?) -> Unit) -> Unit,
    onGetHeader: (callback: (Map<String, String>?) -> Unit) -> Unit,
    onDownloadChannel: (channel: Int, endValue: Float, callback: (List<Float>?) -> Unit) -> Unit,
    onSaveCSV: (header: Map<String, String>, data: Map<Int, List<Float>>, filename: String, callback: (Boolean, String?) -> Unit) -> Unit,
    onStopRecording: () -> Unit,
    onClearMemory: (callback: (Boolean) -> Unit) -> Unit,
    isRecording: Boolean = false,
    timeRemaining: Int = 0,
    totalTime: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf<Char?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }
    var headerData by remember { mutableStateOf<Map<String, String>?>(null) }
    var downloadedData by remember { mutableStateOf<Map<Int, List<Float>>>(emptyMap()) }
    var statusMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_download_offline_data)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF0F172A),
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color(0xFF1E293B)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recording Timer (when recording is in progress)
            if (isRecording && timeRemaining > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.message_recording_in_progress),
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF3B82F6),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.message_time_remaining, timeRemaining),
                            style = MaterialTheme.typography.headlineLarge,
                            color = androidx.compose.ui.graphics.Color(0xFF10B981),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = if (totalTime > 0) (totalTime - timeRemaining).toFloat() / totalTime else 0f,
                            modifier = Modifier.fillMaxWidth(),
                            color = androidx.compose.ui.graphics.Color(0xFF10B981),
                            trackColor = androidx.compose.ui.graphics.Color(0xFF0F172A)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.warning_incomplete_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFFFBBF24),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF0F172A)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, stringResource(R.string.content_desc_status_info), tint = androidx.compose.ui.graphics.Color.White)
                        Text(
                            stringResource(R.string.card_title_status_sensorbox),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    
                    if (isChecking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = androidx.compose.ui.graphics.Color(0xFF10B981)
                            )
                            Text(
                                stringResource(R.string.status_checking),
                                style = MaterialTheme.typography.bodyLarge,
                                color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                            )
                        }
                    } else if (currentMode != null) {
                        Text(
                            when (currentMode) {
                                'N' -> stringResource(R.string.status_ready_for_recording)
                                'R' -> stringResource(R.string.status_download_completed)
                                else -> stringResource(R.string.status_unknown_mode, currentMode!!)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    
                    val errorCheckStatusText = stringResource(R.string.error_status_check_failed)
                    Button(
                        onClick = {
                            isChecking = true
                            errorMessage = ""
                            onCheckMode { mode ->
                                currentMode = mode
                                isChecking = false
                                if (mode == null) {
                                    errorMessage = errorCheckStatusText
                                }
                            }
                        },
                        enabled = !isChecking && !isDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF10B981)
                        )
                    ) {
                        Text(stringResource(R.string.button_check_status))
                    }
                    
                    // Note for Status R - power cycle required
                    if (currentMode == 'R') {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.note_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = androidx.compose.ui.graphics.Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.note_power_cycle_required),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            // Header Info Card
            if (headerData != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF0F172A)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.card_title_recording_information),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        
                        val timestamp = headerData!!["ts"]?.toLongOrNull() ?: 0L
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(timestamp * 1000))
                        
                        // Calculate samples and duration
                        // du = duration units (każda jednostka = 500 próbek)
                        // Jeśli mamy już pobrane dane, użyj faktycznej liczby
                        val du = headerData!!["du"]?.toIntOrNull() ?: 0
                        val samples = if (downloadedData.isNotEmpty()) {
                            downloadedData.values.firstOrNull()?.size ?: (du * 500)
                        } else {
                            du * 500
                        }
                        val timeBaseMs = headerData!!["tb"]?.toIntOrNull() ?: 1
                        val durationSec = (samples * timeBaseMs) / 1000.0
                        
                        InfoRow(stringResource(R.string.label_data), date, androidx.compose.ui.graphics.Color.White)
                        InfoRow(stringResource(R.string.label_channels), headerData!!["rc"] ?: "?", androidx.compose.ui.graphics.Color.White)
                        InfoRow(stringResource(R.string.label_trigger), "P${headerData!!["tc"]} @ ${headerData!!["th"]}%", androidx.compose.ui.graphics.Color.White)
                        InfoRow(stringResource(R.string.label_samples), "$samples", androidx.compose.ui.graphics.Color.White)
                        InfoRow(stringResource(R.string.label_time_base_colon), "${timeBaseMs}ms", androidx.compose.ui.graphics.Color.White)
                        InfoRow(stringResource(R.string.label_duration), "%.1fs".format(durationSec), androidx.compose.ui.graphics.Color.White)
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(stringResource(R.string.label_end_values), fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                        (1..4).forEach { ch ->
                            InfoRow(
                                "P$ch:",
                                "${headerData!!["e$ch"]} ${headerData!!["u$ch"]}",
                                androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }
            }

            // Download Button - only show when mode is 'R'
            if (currentMode == 'R') {
                Button(
                onClick = {
                    isDownloading = true
                    errorMessage = ""
                    statusMessage = ""
                    downloadProgress = context.getString(R.string.status_downloading_header)
                    
                    // Krok 1: Pobierz nagłówek
                    onGetHeader { header ->
                        if (header == null) {
                            errorMessage = context.getString(R.string.error_header_download_failed)
                            isDownloading = false
                            return@onGetHeader
                        }
                        
                        headerData = header
                        downloadProgress = context.getString(R.string.status_header_downloaded)
                        
                        // Krok 2: Pobierz dane z kanałów
                        // rc to string "1111" gdzie każdy znak to bit dla P1-P4
                        val rcString = header["rc"] ?: ""
                        val channels = mutableListOf<Int>()
                        rcString.forEachIndexed { index, char ->
                            if (char == '1' && index < 4) {
                                channels.add(index + 1)  // P1-P4
                            }
                        }
                        
                        if (channels.isEmpty()) {
                            errorMessage = context.getString(R.string.error_no_channels_to_download)
                            isDownloading = false
                            return@onGetHeader
                        }
                        
                        val allData = mutableMapOf<Int, List<Float>>()
                        var currentChannelIndex = 0
                        
                        fun downloadNextChannel() {
                            if (currentChannelIndex >= channels.size) {
                                // Wszystkie kanały pobrane
                                downloadProgress = context.getString(R.string.status_saving_csv)
                                downloadedData = allData
                                
                                // Krok 3: Zapisz do CSV
                                val timestamp = header["ts"] ?: "unknown"
                                val filename = "sensorbox_${timestamp}.csv"
                                
                                onSaveCSV(header, allData, filename) { success, message ->
                                    isDownloading = false
                                    if (success) {
                                        statusMessage = context.getString(R.string.success_data_saved, filename)
                                        downloadProgress = ""
                                    } else {
                                        errorMessage = context.getString(R.string.error_save_failed, message ?: "")
                                        downloadProgress = ""
                                    }
                                }
                                return
                            }
                            
                            val channel = channels[currentChannelIndex]
                            downloadProgress = context.getString(R.string.status_downloading_channel_progress, channel, currentChannelIndex + 1, channels.size)
                            
                            val endValue = header["e$channel"]?.toFloatOrNull() ?: 100f
                            
                            onDownloadChannel(channel, endValue) { data ->
                                if (data != null) {
                                    allData[channel] = data
                                    currentChannelIndex++
                                    // Odczekaj 300ms przed kolejnym kanałem (daj SensorBox czas)
                                    coroutineScope.launch {
                                        delay(300)
                                        downloadNextChannel()
                                    }
                                } else {
                                    errorMessage = context.getString(R.string.error_channel_download_failed, channel)
                                    isDownloading = false
                                }
                            }
                        }
                        
                        downloadNextChannel()
                    }
                },
                enabled = !isDownloading && currentMode == 'R',
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF10B981)
                )
            ) {
                Text(stringResource(R.string.button_download_offline_data))
            }
            }

            // Progress
            if (isDownloading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(downloadProgress)
                    }
                }
            }

            // Status Message
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        statusMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Error Message
            if (errorMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        errorMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Medium, color = color)
        Text(value, color = color)
    }
}
