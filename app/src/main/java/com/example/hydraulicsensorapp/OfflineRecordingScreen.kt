//O DZIALA

package com.example.hydraulicsensorapp

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineRecordingScreen(
    channelValues: List<String>,
    ranges: List<String>,
    connectionStatus: String,
    isReadingLive: Boolean,
    endValues: List<String>,
    originalUnits: List<String>,
    displayUnits: List<String>,
    onQueryEndValues: () -> Unit,
    onRangeChange: (Int, String) -> Unit,
    onSendRangeSettings: (Int, Int, List<String>, String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStartMeasurement: () -> Unit,
    onStopMeasurement: () -> Unit,
    onBack: () -> Unit,
    onCommandTest: () -> Unit = {},  // Callback do ekranu testowego
    onOfflineConfig: () -> Unit = {},  // Callback do Offline Recording Config
    onDownloadData: () -> Unit = {}  // Callback do pobierania offline data
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val customInputs = remember { mutableStateListOf("", "", "", "", "", "") }
    var startCountdown by remember { mutableIntStateOf(0) }  // 0 = brak blokady, 3,2,1 = odliczanie
    
    // Countdown timer
    LaunchedEffect(startCountdown) {
        if (startCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            startCountdown -= 1
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Kompaktowy status połączenia
                        Surface(
                            shape = CircleShape,
                            color = if (connectionStatus.contains("Połączono")) Color(0xFF10B981) else Color(0xFF64748B),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        
                        Column {
                            Text("Hydraulic Sensor", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                if (connectionStatus.contains("Połączono")) "Połączono" else "Rozłączono",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (connectionStatus.contains("Połączono")) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    // Przycisk Połącz/Rozłącz
                    if (!connectionStatus.contains("Połączono")) {
                        TextButton(
                            onClick = onConnect,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF60A5FA))
                        ) {
                            Text("Połącz", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        // Gdy połączony - pokaż przycisk Rozłącz oraz Start/Stop pomiarów
                        TextButton(
                            onClick = onDisconnect,
                            enabled = !isReadingLive,  // Zablokuj gdy pomiary aktywne
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (!isReadingLive) Color(0xFFEF4444) else Color(0xFF64748B),
                                disabledContentColor = Color(0xFF475569)
                            )
                        ) {
                            Text("Rozłącz", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Przycisk Start/Stop pomiarów
                        if (!isReadingLive) {
                            TextButton(
                                onClick = onStartMeasurement,
                                enabled = startCountdown == 0,  // Zablokuj podczas countdown
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (startCountdown == 0) Color(0xFF10B981) else Color(0xFF64748B),
                                    disabledContentColor = Color(0xFF64748B)
                                )
                            ) {
                                Text(
                                    if (startCountdown > 0) startCountdown.toString() else "Start",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onStopMeasurement,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFBBF24))
                            ) {
                                Text("Stop", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    
                    // Przycisk Komendy
                    IconButton(onClick = onCommandTest) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Komendy", tint = Color(0xFFCBD5E1))
                    }
                    
                    // Przycisk Offline Recording Config
                    IconButton(onClick = onOfflineConfig) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Offline Recording", tint = Color(0xFFCBD5E1))
                    }
                    
                    // Przycisk Download Offline Data
                    TextButton(onClick = onDownloadData) {
                        Text("📥", color = Color(0xFFCBD5E1))
                    }
                    
                    IconButton(onClick = { isMenuOpen = !isMenuOpen }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = Color(0xFFCBD5E1))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                SensorGrid(
                    channelValues = channelValues,
                    ranges = ranges,
                    endValues = endValues,
                    originalUnits = originalUnits,
                    displayUnits = displayUnits,
                    onQueryEndValues = onQueryEndValues,
                    isReadingLive = isReadingLive,
                    customInputs = customInputs,
                    onRangeChange = onRangeChange,
                    onSendRangeSettings = onSendRangeSettings,
                    onDialogClose = { startCountdown = 3 },  // Uruchom countdown po zamknięciu dialogu
                    modifier = Modifier.weight(1f)
                )
            }

            if (isMenuOpen) {
                // Menu identyczne jak wcześniej
            }
        }
    }
}

@Composable
fun SensorGrid(
    channelValues: List<String>,
    ranges: List<String>,
    endValues: List<String>,
    originalUnits: List<String>,
    displayUnits: List<String>,
    onQueryEndValues: () -> Unit,
    isReadingLive: Boolean,
    customInputs: MutableList<String>,
    onRangeChange: (Int, String) -> Unit,
    onSendRangeSettings: (Int, Int, List<String>, String) -> Unit,
    onDialogClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(channelValues) { index, value ->
            SensorTile(
                id = "P${index + 1}",
                rawValue = value,
                range = ranges.getOrElse(index){""},
                index = index,
                customInput = customInputs[index],
                endValues = endValues,
                originalUnit = originalUnits.getOrElse(index) { "bar" },
                displayUnit = displayUnits.getOrElse(index) { "bar" },
                onQueryEndValues = onQueryEndValues,
                isReadingLive = isReadingLive,
                onRangeChange = { idx, v -> customInputs[idx] = v; onRangeChange(idx,v) },
                onSendRangeSettings = onSendRangeSettings,
                onDialogClose = onDialogClose
            )
        }
    }
}

@Composable
fun SensorTile(
    id: String,
    rawValue: String,
    range: String,
    index: Int,
    customInput: String,
    endValues: List<String>,
    originalUnit: String,
    displayUnit: String,
    onQueryEndValues: () -> Unit,
    isReadingLive: Boolean,
    onRangeChange: (Int,String) -> Unit,
    onSendRangeSettings: (Int, Int, List<String>, String) -> Unit,
    onDialogClose: () -> Unit
) {
    var showRangeDialog by remember { mutableStateOf(false) }
    
    // Funkcja konwersji jednostek
    fun convertValue(value: Float, fromUnit: String, toUnit: String): Float {
        return when {
            fromUnit == "bar" && toUnit == "psi" -> value * 14.5038f
            fromUnit == "psi" && toUnit == "bar" -> value / 14.5038f
            fromUnit == "bar" && toUnit == "MPa" -> value * 0.1f
            fromUnit == "MPa" && toUnit == "bar" -> value * 10f
            fromUnit == "psi" && toUnit == "MPa" -> value / 145.038f
            fromUnit == "MPa" && toUnit == "psi" -> value * 145.038f
            fromUnit == "C" && toUnit == "F" -> value * 1.8f + 32f
            fromUnit == "F" && toUnit == "C" -> (value - 32f) / 1.8f
            fromUnit == "lpm" && toUnit == "gpm" -> value * 0.264172f
            fromUnit == "gpm" && toUnit == "lpm" -> value / 0.264172f
            else -> value
        }
    }
    
    // Przechowuj 5 zakresów dla każdego sensora
    val currentRanges = remember { 
        mutableStateListOf(
            when(id) {
                "P1", "P2" -> "60 bar"
                "P3" -> "P1-P2"
                "P4" -> "125 C"
                "P5" -> "50 lpm"
                "P6" -> "50 lpm"
                else -> "100 bar"
            },
            when(id) {
                "P1", "P2", "P3" -> "250 bar"
                "P4" -> "150 C"
                "P5" -> "150 lpm"
                "P6" -> "150 lpm"
                else -> "200 bar"
            },
            when(id) {
                "P1", "P2", "P3" -> "400 bar"
                "P4" -> "200 C"
                "P5" -> "300 lpm"
                "P6" -> "P1*P5"
                else -> "300 bar"
            },
            when(id) {
                "P1", "P2", "P3" -> "600 bar"
                "P4" -> "250 C"
                "P5" -> "600 lpm"
                "P6" -> "5000 rpm"
                else -> "400 bar"
            },
            when(id) {
                "P1", "P2", "P3" -> "1000 bar"
                "P4" -> "300 C"
                "P5" -> "1200 lpm"
                "P6" -> "10000 rpm"
                else -> "500 bar"
            }
        )
    }

    val unit = range.split(" ").lastOrNull() ?: "bar"

    val display = rawValue.substringAfter(":").trim()
    
    // Konwertuj zakres (range) z originalUnit na displayUnit
    val displayedRange = if (range.isNotBlank()) {
        val parts = range.split(" ")
        if (parts.size >= 2) {
            val rangeValue = parts[0].toFloatOrNull() ?: 0f
            val rangeUnit = parts[1]
            
            if (rangeUnit != displayUnit && originalUnit != displayUnit) {
                // Konwertuj wartość zakresu
                val convertedRangeValue = convertValue(rangeValue, rangeUnit, displayUnit)
                "${convertedRangeValue.toInt()} ${displayUnit.uppercase()}"
            } else {
                "${rangeValue.toInt()} ${displayUnit.uppercase()}"
            }
        } else {
            displayUnit.uppercase()
        }
    } else {
        displayUnit.uppercase()
    }
    
    // Konwertuj wartość z originalUnit na displayUnit software'owo
    val formatted = if(display=="---" || display.contains("brak czujnika")) display
    else {
        val originalValue = display.toDoubleOrNull() ?: 0.0
        val convertedValue = if (originalUnit != displayUnit) {
            val result = convertValue(originalValue.toFloat(), originalUnit, displayUnit).toDouble()
            Log.d("SensorBox", "$id: Konwersja $originalValue $originalUnit → $result $displayUnit")
            result
        } else {
            originalValue
        }
        val decimals = val2decimal(convertedValue, displayUnit)
        decimals.toString()
    }

    // Dialog ustawień
    if (showRangeDialog) {
        RangeSettingsDialog(
            sensorId = id,
            currentRanges = currentRanges,
            currentEndValue = endValues.getOrNull(index) ?: "---",
            onQueryEndValues = onQueryEndValues,
            onDismiss = { 
                showRangeDialog = false
                onDialogClose()  // Uruchom countdown po zamknięciu
            },
            onSave = { activeRangeIndex, newRanges, selectedUnit ->
                // Aktualizuj zakresy lokalnie
                currentRanges.clear()
                currentRanges.addAll(newRanges)
                
                // Wywołaj callback z wybranym zakresem (aktywny zakres)
                onRangeChange(index, newRanges[activeRangeIndex])
                
                // Wyślij do SensorBox przez BLE (z informacją o selectedUnit)
                onSendRangeSettings(index, activeRangeIndex, newRanges, selectedUnit)
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(id, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                IconButton(
                    onClick = { showRangeDialog = true },
                    enabled = !isReadingLive  // Zablokuj gdy pomiary aktywne
                ) {
                    Icon(
                        Icons.Outlined.Settings, 
                        contentDescription = "Range Settings", 
                        tint = if (!isReadingLive) Color(0xFF94A3B8) else Color(0xFF475569)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatted, style = MaterialTheme.typography.displayMedium, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(displayedRange, style = MaterialTheme.typography.titleMedium, color = Color(0xFF94A3B8))
            }

            Text(
                text = if(formatted=="---") "No data" else if(formatted.contains("brak czujnika")) "No sensor" else "",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

fun val2decimal(value: Double, unit: String): String {
    return when(unit){
        "U/m","rpm","psi" -> value.toInt().toString()
        "C","F" -> String.format("%.1f", value)
        else -> when {
            value<10 -> String.format("%.2f", value)
            value<1000 -> String.format("%.1f", value)
            else -> value.toInt().toString()
        }
    }
}
