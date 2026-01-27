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
                title = { Text("Pobieranie Offline Data") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
                    }
                }
            )
        }
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
                    containerColor = when (currentMode) {
                        'N' -> MaterialTheme.colorScheme.surfaceVariant
                        'R' -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
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
                        Icon(Icons.Default.Info, "Status")
                        Text(
                            "Status SensorBox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        when (currentMode) {
                            'N' -> "✅ Normal Mode - gotowy do pobrania"
                            'R' -> "⏺️ Recording Mode - trwa zapis"
                            null -> "❓ Nieznany - sprawdź status"
                            else -> "⚠️ Status: $currentMode"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Sprawdź Status")
                    }
                    
                    // Przycisk Stop Recording (tylko gdy mode = 'R')
                    if (currentMode == 'R') {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onStopRecording()
                                    statusMessage = "Wysłano komendę STOP..."
                                    // Sprawdź status po 2 sekundach
                                    coroutineScope.launch {
                                        delay(2000)
                                        onCheckMode { mode ->
                                            currentMode = mode
                                        }
                                    }
                                },
                                enabled = !isChecking && !isDownloading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("⏹️ Zatrzymaj Recording")
                            }
                            
                            Text(
                                "⚠️ Zatrzymanie nie czyści pamięci - użyj przycisku poniżej aby wyczyścić.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Przycisk Clear Memory (zawsze widoczny gdy nie trwa pobieranie)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Button(
                        onClick = {
                            statusMessage = "Wysyłam komendy czyszczenia pamięci..."
                            errorMessage = ""
                            coroutineScope.launch {
                                onClearMemory { success ->
                                    if (success) {
                                        statusMessage = "✅ Wysłano komendy czyszczenia - sprawdź status"
                                        // Sprawdź status po 2s
                                        coroutineScope.launch {
                                            delay(2000)
                                            onCheckMode { mode ->
                                                currentMode = mode
                                                if (mode == 'N') {
                                                    statusMessage = "✅ Pamięć wyczyszczona - SensorBox w trybie Normal"
                                                } else {
                                                    statusMessage = "⚠️ Status: $mode - jeśli nadal R, rozłącz zasilanie na 10s"
                                                }
                                            }
                                        }
                                    } else {
                                        errorMessage = "❌ Nie udało się wysłać komend czyszczenia"
                                    }
                                }
                            }
                        },
                        enabled = !isChecking && !isDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("🗑️ Wyczyść Pamięć (Clear Memory)")
                    }
                    
                    Text(
                        "Usuwa stare nagranie z pamięci SensorBox. Użyj gdy nowe nagrania są odrzucane (status=4).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Header Info Card
            if (headerData != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Informacje o nagraniu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val timestamp = headerData!!["ts"]?.toLongOrNull() ?: 0L
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(timestamp * 1000))
                        
                        InfoRow("Data:", date)
                        InfoRow("Kanały:", headerData!!["rc"] ?: "?")
                        InfoRow("Trigger:", "P${headerData!!["tc"]} @ ${headerData!!["th"]}%")
                        InfoRow("Próbek:", headerData!!["end"] ?: "?")
                        InfoRow("Time Base:", "${headerData!!["tb"]}ms")
                        InfoRow("Duration:", "${headerData!!["du"]}s")
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text("End Values:", fontWeight = FontWeight.Bold)
                        (1..4).forEach { ch ->
                            InfoRow(
                                "P$ch:",
                                "${headerData!!["e$ch"]} ${headerData!!["u$ch"]}"
                            )
                        }
                    }
                }
            }

            // Download Button
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
                                        statusMessage = "✅ Dane zapisane: $filename"
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
                enabled = !isDownloading && (currentMode == 'N' || currentMode == 'R'),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pobierz dane z SensorBox")
            }
            
            // Info o pobieraniu w trybie R
            if (currentMode == 'R') {
                Text(
                    "⚠️ Uwaga: SensorBox jest w trybie Recording. Pobieranie może nie działać lub zwrócić częściowe dane.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
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

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Instrukcja:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("1. Sprawdź status SensorBox", style = MaterialTheme.typography.bodyMedium)
                    Text("2. Jeśli status = 'R':", style = MaterialTheme.typography.bodyMedium)
                    Text("   • SensorBox czeka na trigger - spełnij warunek", style = MaterialTheme.typography.bodySmall)
                    Text("   • Lub rozłącz i połącz ponownie", style = MaterialTheme.typography.bodySmall)
                    Text("3. Jeśli status = 'N', kliknij 'Pobierz dane'", style = MaterialTheme.typography.bodyMedium)
                    Text("4. Poczekaj na zakończenie pobierania", style = MaterialTheme.typography.bodyMedium)
                    Text("5. Plik CSV zostanie zapisany w Downloads/HydraulicSensorApp/", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(value)
    }
}
