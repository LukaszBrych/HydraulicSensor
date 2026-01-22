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
    onRangeChange: (Int, String) -> Unit,
    onStartRecording: (Int, Int, String, List<Boolean>, String, Int) -> Unit,
    onStopRecording: () -> Unit,
    onBack: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val customInputs = remember { mutableStateListOf("", "", "", "", "", "") }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hydraulic Sensor", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("Live Sensor Monitoring", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    }
                },
                actions = {
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onStartRecording(4,25,"pos",listOf(true,true,true,true,true,true),"10ms",1000) }) { Text("Start") }
                    OutlinedButton(onClick = { onStopRecording() }) { Text("Stop") }
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(connectionStatus, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                }

                SensorGrid(
                    channelValues = channelValues,
                    ranges = ranges,
                    customInputs = customInputs,
                    onRangeChange = onRangeChange,
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
    customInputs: MutableList<String>,
    onRangeChange: (Int, String) -> Unit,
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
                onRangeChange = { idx, v -> customInputs[idx] = v; onRangeChange(idx,v) }
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
    onRangeChange: (Int,String) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    val (options, unit) = when(id) {
        "P1","P2","P3" -> listOf("10bar","60bar","250bar","400bar","600bar","Custom") to "bar"
        "P4" -> listOf("125°C","500°C","Custom") to "°C"
        "P5" -> listOf("605lpm","27lpm","1000lpm","0lpm","Custom") to "lpm"
        "P6" -> listOf("394lpm","1291lpm","0.00lpm","6000U/m","Custom") to "U/m"
        else -> listOf("Custom") to ""
    }

    val display = rawValue.substringAfter(":").trim()
    val formatted = if(display=="---" || display.contains("brak czujnika")) display
    else val2decimal(display.toDoubleOrNull() ?: 0.0, unit).toString()

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
                Box {
                    IconButton(onClick = { dropdownExpanded = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color(0xFF94A3B8))
                    }
                    DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        options.forEach { option ->
                            if(option=="Custom"){
                                DropdownMenuItem(text = { Text("Custom...") }, onClick = {
                                    dropdownExpanded = false
                                    val customValue = "1000" // domyślne
                                    onRangeChange(index,"$customValue$unit")
                                })
                            } else {
                                DropdownMenuItem(text = { Text(option) }, onClick = {
                                    dropdownExpanded = false
                                    onRangeChange(index,option)
                                })
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatted, style = MaterialTheme.typography.displayMedium, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(range, style = MaterialTheme.typography.titleMedium, color = Color(0xFF94A3B8))
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
