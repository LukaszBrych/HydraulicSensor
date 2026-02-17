//O DZIALA

package com.example.hydraulicsensorapp

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
    onDownloadData: () -> Unit = {},  // Callback do pobierania offline data
    onLiveRecordings: () -> Unit = {},  // Callback do Live Recordings
    onTurbineCalibration: () -> Unit = {},  // Callback do Turbine Calibration
    onLanguageChange: (String) -> Unit = {},  // Callback for language change
    currentLanguage: String = "en",  // Current language code
    turbineNames: Map<String, String> = emptyMap(),  // Turbine names for P5/P6
    showMessage: String? = null  // Message to show in Snackbar
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val customInputs = remember { mutableStateListOf("", "", "", "", "", "") }
    var startCountdown by remember { mutableIntStateOf(0) }  // 0 = brak blokady, 3,2,1 = odliczanie
    val snackbarHostState = remember { SnackbarHostState() }
    var previousConnectionStatus by remember { mutableStateOf(connectionStatus) }
    
    // MIN/MAX tracking dla każdego kanału
    val minValues = remember { mutableStateListOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE) }
    val maxValues = remember { mutableStateListOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE) }
    
    // Reset MIN/MAX gdy zaczyna się pomiar
    LaunchedEffect(isReadingLive) {
        if (isReadingLive) {
            Log.d("SensorBox", "🔄 Resetowanie MIN/MAX wartości")
            // Resetuj wartości MIN/MAX
            for (i in 0..5) {
                minValues[i] = Double.MAX_VALUE
                maxValues[i] = Double.MIN_VALUE
            }
        }
    }
    
    // Aktualizuj MIN/MAX podczas pomiaru - co 250ms, ale zacznij po 1 sekundzie
    LaunchedEffect(isReadingLive) {
        if (isReadingLive) {
            // Czekaj 1 sekundę przed rozpoczęciem śledzenia MIN/MAX
            kotlinx.coroutines.delay(1000)
            Log.d("SensorBox", "✅ Rozpoczynam śledzenie MIN/MAX po 1 sekundzie")
        }
        while (isReadingLive) {
            channelValues.forEachIndexed { index, value ->
                val display = value.substringAfter(":").trim()
                if (display != "---" && !display.contains("brak czujnika")) {
                    val numValue = display.toDoubleOrNull()
                    if (numValue != null) {
                        var updated = false
                        if (minValues[index] == Double.MAX_VALUE || numValue < minValues[index]) {
                            minValues[index] = numValue
                            updated = true
                        }
                        if (maxValues[index] == Double.MIN_VALUE || numValue > maxValues[index]) {
                            maxValues[index] = numValue
                            updated = true
                        }
                        if (updated) {
                            Log.d("SensorBox", "P${index+1}: wartość=$numValue, MIN=${minValues[index]}, MAX=${maxValues[index]}")
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(250) // Sprawdzaj co 250ms
        }
    }
    
    // Monitor connection status and start countdown when connected
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == "Connected to SensorBox" && previousConnectionStatus != "Connected to SensorBox") {
            startCountdown = 3  // Start countdown when connected
        }
        previousConnectionStatus = connectionStatus
    }
    
    // Show message in Snackbar when provided
    LaunchedEffect(showMessage) {
        if (showMessage != null) {
            startCountdown = 3  // Start countdown when returning with message
            snackbarHostState.showSnackbar(
                message = showMessage,
                duration = SnackbarDuration.Short
            )
        }
    }
    
    // Countdown timer
    LaunchedEffect(startCountdown) {
        if (startCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            startCountdown -= 1
        }
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                )
            }
        },
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
                            color = if (connectionStatus.contains("Connected")) Color(0xFF10B981) else Color(0xFF64748B),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        
                        Column {
                            Text(stringResource(R.string.app_bar_title), style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                if (connectionStatus.contains("Connected")) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (connectionStatus.contains("Connected")) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    // Przycisk Połącz/Rozłącz
                    if (!connectionStatus.contains("Connected")) {
                        TextButton(
                            onClick = onConnect,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF60A5FA))
                        ) {
                            Text(stringResource(R.string.button_connect), style = MaterialTheme.typography.labelMedium)
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
                            Text(stringResource(R.string.button_disconnect), style = MaterialTheme.typography.labelMedium)
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
                                    if (startCountdown > 0) startCountdown.toString() else stringResource(R.string.button_start),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onStopMeasurement,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFBBF24))
                            ) {
                                Text(stringResource(R.string.button_stop), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    
                    IconButton(onClick = { menuExpanded = !menuExpanded }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Color(0xFFCBD5E1)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content
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
                        onDialogClose = { startCountdown = 3 },
                        turbineNames = turbineNames,
                        startCountdown = startCountdown,
                        minValues = minValues,
                        maxValues = maxValues,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Side menu modal
            AnimatedVisibility(
                visible = menuExpanded,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                ) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Dark overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { menuExpanded = false }
                    )
                    
                    // Menu panel from right side
                    val configuration = LocalConfiguration.current
                    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    val menuWidth = 0.7f
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(menuWidth)
                            .align(Alignment.CenterEnd),
                        color = Color(0xFF1E293B)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp)
                        ) {
                            // Menu items
                            MenuItemWithDivider(
                                text = stringResource(R.string.menu_item_test_commands),
                                onClick = {
                                    menuExpanded = false
                                    onCommandTest()
                                }
                            )
                            
                            MenuItemWithDivider(
                                text = stringResource(R.string.menu_item_offline_recording_setup),
                                onClick = {
                                    menuExpanded = false
                                    onOfflineConfig()
                                }
                            )
                            
                            MenuItemWithDivider(
                                text = stringResource(R.string.menu_item_download_offline_data),
                                onClick = {
                                    menuExpanded = false
                                    onDownloadData()
                                }
                            )
                            
                            MenuItemWithDivider(
                                text = stringResource(R.string.menu_item_live_recordings),
                                onClick = {
                                    menuExpanded = false
                                    onLiveRecordings()
                                }
                            )
                            
                            MenuItemWithDivider(
                                text = stringResource(R.string.menu_item_turbine_calibration),
                                onClick = {
                                    menuExpanded = false
                                    onTurbineCalibration()
                                }
                            )
                            
                            // Language selector - last item without divider
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.menu_item_language),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // English option
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(color = Color(0xFF3B82F6))
                                        ) {
                                            menuExpanded = false
                                            onLanguageChange("en")
                                        }
                                    ) {
                                        RadioButton(
                                            selected = currentLanguage == "en",
                                            onClick = {
                                                menuExpanded = false
                                                onLanguageChange("en")
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF3B82F6),
                                                unselectedColor = Color(0xFF94A3B8)
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.language_english),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    
                                    // Polish option
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(color = Color(0xFF3B82F6))
                                        ) {
                                            menuExpanded = false
                                            onLanguageChange("pl")
                                        }
                                    ) {
                                        RadioButton(
                                            selected = currentLanguage == "pl",
                                            onClick = {
                                                menuExpanded = false
                                                onLanguageChange("pl")
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF3B82F6),
                                                unselectedColor = Color(0xFF94A3B8)
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.language_polish),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItemWithDivider(text: String, onClick: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color(0xFF3B82F6)),
                    onClick = onClick
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = Color(0xFF475569)
        )
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
    turbineNames: Map<String, String> = emptyMap(),
    startCountdown: Int = 0,
    minValues: List<Double> = emptyList(),
    maxValues: List<Double> = emptyList(),
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
                onDialogClose = onDialogClose,
                turbineNames = turbineNames,
                startCountdown = startCountdown,
                minValue = minValues.getOrNull(index),
                maxValue = maxValues.getOrNull(index)
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
    onDialogClose: () -> Unit,
    turbineNames: Map<String, String> = emptyMap(),
    startCountdown: Int = 0,
    minValue: Double? = null,
    maxValue: Double? = null
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
            
            // Dla P5 (R1-R5) zawsze pokaż nazwę turbiny, dla P6 tylko R1 i R2
            val rangeIndex = currentRanges.indexOf(range).coerceAtLeast(0)
            val shouldShowTurbineName = (id == "P5") || (id == "P6" && rangeIndex < 2)
            
            if (shouldShowTurbineName) {
                val turbineKey = "${id}R${rangeIndex + 1}"  // P5R1, P5R2, ..., P6R1, P6R2
                val turbineName = turbineNames[turbineKey]?.takeIf { it.isNotBlank() } ?: "R${rangeIndex + 1}"
                "$turbineName ${displayUnit.uppercase()}"
            } else if (rangeUnit != displayUnit && originalUnit != displayUnit) {
                // Konwertuj wartość zakresu
                val convertedRangeValue = convertValue(rangeValue, rangeUnit, displayUnit)
                "${convertedRangeValue.toInt()} ${displayUnit.uppercase()}"
            } else {
                "${rangeValue.toInt()} ${displayUnit.uppercase()}"
            }
        } else {
            val rangeIndex = currentRanges.indexOf(range).coerceAtLeast(0)
            val shouldShowTurbineName = (id == "P5") || (id == "P6" && rangeIndex < 2)
            
            // Dla P5 (wszystkie) i P6 (tylko R1-R2) pokaż nazwę turbiny + jednostkę
            if (shouldShowTurbineName) {
                val turbineKey = "${id}R${rangeIndex + 1}"
                val turbineName = turbineNames[turbineKey]?.takeIf { it.isNotBlank() } ?: "R${rangeIndex + 1}"
                "$turbineName ${displayUnit.uppercase()}"
            } else {
                displayUnit.uppercase()
            }
        }
    } else {
        // Dla P5 i P6 (R1) pokaż nazwę turbiny + jednostkę
        val shouldShowTurbineName = id == "P5" || id == "P6"
        if (shouldShowTurbineName) {
            val turbineKey = "${id}R1"  // Domyślnie R1 jeśli brak zakresu
            val turbineName = turbineNames[turbineKey]?.takeIf { it.isNotBlank() } ?: "R1"
            "$turbineName ${displayUnit.uppercase()}"
        } else {
            displayUnit.uppercase()
        }
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
                // Aktualizuj zakresy lokalnie
                currentRanges.clear()
                currentRanges.addAll(newRanges)
                
                // Wywołaj callback z wybranym zakresem (aktywny zakres)
                onRangeChange(index, newRanges[activeRangeIndex])
                
                // Wyślij do SensorBox przez BLE (z informacją o selectedUnit)
                onSendRangeSettings(index, activeRangeIndex, newRanges, selectedUnit)
            },
            turbineNames = turbineNames
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
                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { showRangeDialog = true },
                        enabled = !isReadingLive && startCountdown == 0  // Zablokuj gdy pomiary aktywne lub countdown
                    ) {
                        Icon(
                            Icons.Outlined.Settings, 
                            contentDescription = stringResource(R.string.content_desc_range_settings), 
                            tint = if (!isReadingLive && startCountdown == 0) Color(0xFF94A3B8) else Color(0xFF475569)
                        )
                    }
                    if (startCountdown > 0) {
                        Text(
                            text = startCountdown.toString(),
                            color = Color(0xFFFBBF24),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatted, style = MaterialTheme.typography.displayMedium, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(displayedRange, style = MaterialTheme.typography.titleMedium, color = Color(0xFF94A3B8))
                
                // Wyświetl MIN/MAX gdy pomiar aktywny
                val showMinMax = isReadingLive && 
                                 minValue != null && maxValue != null && 
                                 minValue != Double.MAX_VALUE && 
                                 maxValue != Double.MIN_VALUE &&
                                 formatted != "---" && 
                                 !formatted.contains("brak czujnika")
                
                if (showMinMax) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "MIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF60A5FA)
                            )
                            Text(
                                val2decimal(minValue, displayUnit),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF60A5FA)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "MAX",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF87171)
                            )
                            Text(
                                val2decimal(maxValue, displayUnit),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF87171)
                            )
                        }
                    }
                }
            }

            Text(
                text = if(formatted=="---") stringResource(R.string.status_no_data) else if(formatted.contains("brak czujnika")) stringResource(R.string.status_no_sensor) else "",
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
        "C","F" -> String.format("%.3f", value)
        else -> when {
            value<10 -> String.format("%.3f", value)
            value<1000 -> String.format("%.3f", value)
            else -> String.format("%.3f", value)
        }
    }
}
