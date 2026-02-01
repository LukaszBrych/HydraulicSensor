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
    onClearMemory: (callback: (Boolean) -> Unit) -> Unit
) {
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
                title = { Text("Download Offline Data") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
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
                        Icon(Icons.Default.Info, "Status", tint = androidx.compose.ui.graphics.Color.White)
                        Text(
                            "Status SensorBox",
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
                                "Checking status...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                            )
                        }
                    } else if (currentMode != null) {
                        Text(
                            when (currentMode) {
                                'N' -> "✅ Ready for Offline Recording"
                                'R' -> "✅ Offline Data Download has been completed"
                                else -> "⚠️ Status: $currentMode"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    
                    Button(
                        onClick = {
                            isChecking = true
                            errorMessage = ""
                            onCheckMode { mode ->
                                currentMode = mode
                                isChecking = false
                                if (mode == null) {
                                    errorMessage = "Nie udało się sprawdzić statusu"
                                }
                            }
                        },
                        enabled = !isChecking && !isDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF10B981)
                        )
                    ) {
                        Text("Check Status")
                    }
                    
                    // Note for Status R - power cycle required
                    if (currentMode == 'R') {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "ℹ️ Note",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = androidx.compose.ui.graphics.Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Before performing a new Offline Recording, you must physically power cycle the SensorBox (turn it off and on).",
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
                            "Recording Information",
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
                        
                        InfoRow("Data:", date, androidx.compose.ui.graphics.Color.White)
                        InfoRow("Channels:", headerData!!["rc"] ?: "?", androidx.compose.ui.graphics.Color.White)
                        InfoRow("Trigger:", "P${headerData!!["tc"]} @ ${headerData!!["th"]}%", androidx.compose.ui.graphics.Color.White)
                        InfoRow("Samples:", "$samples", androidx.compose.ui.graphics.Color.White)
                        InfoRow("Time Base:", "${timeBaseMs}ms", androidx.compose.ui.graphics.Color.White)
                        InfoRow("Duration:", "%.1fs".format(durationSec), androidx.compose.ui.graphics.Color.White)
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text("End Values:", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
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
                    downloadProgress = "Pobieranie nagłówka..."
                    
                    // Krok 1: Pobierz nagłówek
                    onGetHeader { header ->
                        if (header == null) {
                            errorMessage = "Nie udało się pobrać nagłówka"
                            isDownloading = false
                            return@onGetHeader
                        }
                        
                        headerData = header
                        downloadProgress = "Nagłówek pobrany"
                        
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
                            errorMessage = "Brak kanałów do pobrania"
                            isDownloading = false
                            return@onGetHeader
                        }
                        
                        val allData = mutableMapOf<Int, List<Float>>()
                        var currentChannelIndex = 0
                        
                        fun downloadNextChannel() {
                            if (currentChannelIndex >= channels.size) {
                                // Wszystkie kanały pobrane
                                downloadProgress = "Zapisywanie CSV..."
                                downloadedData = allData
                                
                                // Krok 3: Zapisz do CSV
                                val timestamp = header["ts"] ?: "unknown"
                                val filename = "sensorbox_${timestamp}.csv"
                                
                                onSaveCSV(header, allData, filename) { success, message ->
                                    isDownloading = false
                                    if (success) {
                                        statusMessage = "✅ Data saved to: $filename"
                                        downloadProgress = ""
                                    } else {
                                        errorMessage = "❌ Błąd zapisu: $message"
                                        downloadProgress = ""
                                    }
                                }
                                return
                            }
                            
                            val channel = channels[currentChannelIndex]
                            downloadProgress = "Pobieranie P$channel... (${currentChannelIndex + 1}/${channels.size})"
                            
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
                                    errorMessage = "Nie udało się pobrać danych z P$channel"
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
                Text("Download Offline Recording Data")
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
