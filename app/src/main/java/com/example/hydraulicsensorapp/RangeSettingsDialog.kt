package com.example.hydraulicsensorapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog dla ustawień zakresów czujnika - zgodny z Setpage.py z Pythona
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSettingsDialog(
    sensorId: String,
    currentRanges: List<String>,
    currentEndValue: String,
    onQueryEndValues: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (activeRangeIndex: Int, allRanges: List<String>, selectedUnit: String) -> Unit
) {
    // Zapytaj o wartości końcowe po otwarciu dialogu
    LaunchedEffect(Unit) {
        onQueryEndValues()
    }
    // Określ typ czujnika i dostępne jednostki
    val sensorConfig = when(sensorId) {
        "P1", "P2" -> SensorConfig(
            type = "Pressure (4-20mA)",
            units = listOf("bar", "psi", "MPa"),
            defaults = listOf(10f, 60f, 100f, 250f, 600f),
            limits = listOf(10f, 60f, 100f, 250f, 600f),
            labels = listOf("0-10", "0-60", "0-100", "0-250", "0-600")
        )
        "P3" -> SensorConfig(
            type = "Differential / Pressure",
            units = listOf("bar", "psi", "MPa"),
            defaults = listOf(0f, 60f, 100f, 250f, 600f),  // R1 = P1-P2
            limits = listOf(0f, 60f, 100f, 250f, 600f),
            labels = listOf("P1-P2", "0-60", "0-100", "0-250", "0-600")
        )
        "P4" -> SensorConfig(
            type = "Temperature",
            units = listOf("C", "F"),
            defaults = listOf(125f, 500f, 200f, 300f, 400f),
            limits = listOf(125f, 500f, 200f, 300f, 400f),
            labels = listOf("0-125", "0-500", "0-200", "0-300", "0-400")
        )
        "P5" -> SensorConfig(
            type = "Flow (Turbine)",
            units = listOf("lpm", "gpm"),
            defaults = listOf(60f, 100f, 250f, 600f, 1000f),
            limits = listOf(60f, 100f, 250f, 600f, 1000f),
            labels = listOf("R1", "R2", "R3", "R4", "R5")
        )
        "P6" -> SensorConfig(
            type = "Multi-function",
            units = listOf("lpm", "gpm", "rpm", "U/m"),
            defaults = listOf(60f, 100f, 0f, 5000f, 10000f),  // R3 = P1×Q1
            limits = listOf(60f, 100f, 0f, 5000f, 10000f),
            labels = listOf("Flow R1", "Flow R2", "P1×Q1", "RPM-1", "RPM-2")
        )
        else -> SensorConfig(
            type = "Unknown",
            units = listOf("bar"),
            defaults = listOf(100f, 200f, 300f, 400f, 500f),
            limits = listOf(100f, 200f, 300f, 400f, 500f),
            labels = listOf("R1", "R2", "R3", "R4", "R5")
        )
    }

    // Stan jednostki (domyślnie z currentRanges lub pierwsza dostępna)
    var currentUnit by remember { 
        mutableStateOf(
            if (currentRanges.isNotEmpty() && currentRanges[0].isNotBlank()) {
                val parts = currentRanges[0].split(" ")
                if (parts.size >= 2) parts[1] else sensorConfig.units[0]
            } else {
                sensorConfig.units[0]
            }
        )
    }

    // Stan wartości dla każdego zakresu (w currentUnit!)
    val rangeValues = remember {
        mutableStateListOf<String>().apply {
            repeat(5) { index ->
                val current = currentRanges.getOrNull(index) ?: ""
                if (current.isNotBlank() && !current.contains("P1-P2") && !current.contains("P1*P5")) {
                    val parts = current.split(" ")
                    val value = parts.getOrNull(0) ?: sensorConfig.defaults[index].toInt().toString()
                    add(value)
                } else {
                    add(sensorConfig.defaults[index].toInt().toString())
                }
            }
        }
    }

    // Walidacja i błędy
    val errors = remember { mutableStateListOf<String?>(null, null, null, null, null) }

    // Aktywny zakres (R1-R5) - domyślnie R1
    var activeRange by remember { mutableIntStateOf(0) }  // 0 = R1, 1 = R2, etc.
    
    // Funkcja konwersji jednostek
    fun convertValue(value: Float, fromUnit: String, toUnit: String): Float {
        return when {
            // bar ↔ psi ↔ MPa
            fromUnit == "bar" && toUnit == "psi" -> value * 14.5038f
            fromUnit == "psi" && toUnit == "bar" -> value / 14.5038f
            fromUnit == "bar" && toUnit == "MPa" -> value * 0.1f
            fromUnit == "MPa" && toUnit == "bar" -> value * 10f
            fromUnit == "psi" && toUnit == "MPa" -> value / 145.038f
            fromUnit == "MPa" && toUnit == "psi" -> value * 145.038f
            
            // °C ↔ °F
            fromUnit == "C" && toUnit == "F" -> value * 1.8f + 32f
            fromUnit == "F" && toUnit == "C" -> (value - 32f) / 1.8f
            
            // lpm ↔ gpm
            fromUnit == "lpm" && toUnit == "gpm" -> value * 0.264172f
            fromUnit == "gpm" && toUnit == "lpm" -> value / 0.264172f
            
            else -> value
        }
    }
    
    // Przelicz wartość końcową na aktualną jednostkę
    val displayedEndValue = remember(currentEndValue, currentUnit) {
        val baseValue = currentEndValue.toFloatOrNull() ?: return@remember currentEndValue
        val baseUnit = sensorConfig.units[0]  // Wartość z sensora jest w pierwszej jednostce
        
        if (currentUnit == baseUnit) {
            currentEndValue
        } else {
            val converted = convertValue(baseValue, baseUnit, currentUnit)
            String.format("%.2f", converted)
        }
    }

    // Funkcja walidacji
    fun validateRange(index: Int, value: String): String? {
        if (sensorId == "P3" && index == 0) return null  // P1-P2 nie waliduj
        if (sensorId == "P6" && index == 2) return null  // P1×P5 nie waliduj
        
        val numValue = value.toFloatOrNull() ?: return "Nieprawidłowa wartość"
        
        if (numValue < 0) return "Wartość nie może być ujemna"
        
        // Brak limitu hardware - SensorBox przyjmuje dowolną wartość
        return null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "$sensorId Range Settings",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                sensorConfig.type,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Unit Switcher
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Unit / Jednostka",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sensorConfig.units.forEach { unit ->
                                    FilterChip(
                                        selected = currentUnit == unit,
                                        onClick = {
                                            val oldUnit = currentUnit
                                            currentUnit = unit
                                            
                                            // Przelicz wszystkie wartości
                                            rangeValues.forEachIndexed { index, value ->
                                                if (sensorId == "P3" && index == 0) return@forEachIndexed
                                                if (sensorId == "P6" && index == 2) return@forEachIndexed
                                                
                                                val numValue = value.toFloatOrNull()
                                                if (numValue != null) {
                                                    val converted = convertValue(numValue, oldUnit, unit)
                                                    // Zaokrąglij do liczby całkowitej (bez przecinka)
                                                    rangeValues[index] = converted.toInt().toString()
                                                    errors[index] = validateRange(index, rangeValues[index])
                                                }
                                            }
                                        },
                                        label = { Text(unit) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0xFF334155),
                                            labelColor = Color(0xFF94A3B8)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Current End Value (W) - Read Only
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Current End Value (W) / Aktualna wartość końcowa",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = displayedEndValue,
                                    onValueChange = {},
                                    enabled = false,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.titleLarge,
                                    suffix = { Text(currentUnit, color = Color(0xFF94A3B8)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = Color(0xFFE0E0E0),
                                        disabledBorderColor = Color(0xFF475569),
                                        disabledContainerColor = Color(0xFF1E293B)
                                    ),
                                    singleLine = true
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Active Range Selector
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Active Range / Aktywny zakres",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(5) { index ->
                                    val isSpecial = (sensorId == "P3" && index == 0) || (sensorId == "P6" && index == 2)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RadioButton(
                                            selected = activeRange == index,
                                            onClick = { activeRange = index },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = MaterialTheme.colorScheme.primary,
                                                unselectedColor = Color(0xFF64748B)
                                            )
                                        )
                                        Text(
                                            "R${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (activeRange == index) Color.White else Color(0xFF64748B),
                                            fontWeight = if (activeRange == index) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            sensorConfig.labels[index],
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Range TextFields
                    Text(
                        "Range Values / Wartości zakresów",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    repeat(5) { index ->
                        val isSpecial = (sensorId == "P3" && index == 0) || (sensorId == "P6" && index == 2)
                        val isActive = activeRange == index
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSpecial -> Color(0xFF334155)
                                    isActive -> Color(0xFF1E40AF)  // Highlight aktywnego zakresu
                                    else -> Color(0xFF0F172A)
                                }
                            ),
                            border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Label
                                Column(modifier = Modifier.width(100.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "R${index + 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isActive) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "●",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                    }
                                    Text(
                                        sensorConfig.labels[index],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isActive) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    )
                                }

                                // TextField
                                if (isSpecial) {
                                    OutlinedTextField(
                                        value = if (sensorId == "P3" && index == 0) "P1-P2" else "P1×P5",
                                        onValueChange = {},
                                        enabled = false,
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = Color(0xFF94A3B8),
                                            disabledBorderColor = Color(0xFF475569),
                                            disabledContainerColor = Color(0xFF1E293B)
                                        ),
                                        singleLine = true
                                    )
                                } else {
                                    Column(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = rangeValues[index],
                                            onValueChange = { newValue ->
                                                rangeValues[index] = newValue
                                                errors[index] = validateRange(index, newValue)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyLarge,
                                            suffix = { Text(currentUnit, color = Color(0xFF94A3B8)) },
                                            isError = errors[index] != null,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color(0xFFCBD5E1),
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = Color(0xFF475569),
                                                errorBorderColor = MaterialTheme.colorScheme.error,
                                                errorTextColor = Color.White,
                                                focusedContainerColor = Color(0xFF0F172A),
                                                unfocusedContainerColor = Color(0xFF0F172A),
                                                errorContainerColor = Color(0xFF0F172A)
                                            ),
                                            singleLine = true
                                        )
                                        
                                        if (errors[index] != null) {
                                            Text(
                                                errors[index]!!,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Info Card o aktywnym zakresie
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Column {
                                Text(
                                    "Aktywny zakres: R${activeRange + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Wartość: ${rangeValues[activeRange]} $currentUnit (${sensorConfig.labels[activeRange]})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Footer Buttons
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                // Sprawdź czy są błędy
                                val hasErrors = errors.any { it != null }
                                if (!hasErrors) {
                                    val result = rangeValues.mapIndexed { index, value ->
                                        when {
                                            sensorId == "P3" && index == 0 -> "P1-P2"
                                            sensorId == "P6" && index == 2 -> "P1*P5"
                                            else -> "$value $currentUnit"
                                        }
                                    }
                                    onSave(activeRange, result, currentUnit)  // Przekazuję currentUnit!
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = errors.all { it == null }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

data class SensorConfig(
    val type: String,
    val units: List<String>,
    val defaults: List<Float>,
    val limits: List<Float>,
    val labels: List<String>
)
