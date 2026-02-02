package com.example.hydraulicsensorapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@ExperimentalMaterial3Api
@Composable
fun OfflineRecordingConfigScreen(
    onBack: () -> Unit,
    onStartRecording: (
        recordingChannels: String,    // "110100" - które kanały P1-P6
        triggerChannel: Int,           // 1-6
        triggerThreshold: Int,         // 0-100%
        triggerEdge: Int,              // 0=rising, 1=falling
        nrOfSamples: Int,              // liczba próbek
        timeBaseFactor: Int            // 1=1ms, 10=10ms, 100=100ms, 1000=1s, 10000=10s
    ) -> Unit,
    onStopRecording: () -> Unit
) {
    // Trigger settings
    var triggerChannel by remember { mutableIntStateOf(1) }
    var triggerThreshold by remember { mutableIntStateOf(50) }
    var triggerEdge by remember { mutableStateOf("Rising") }
    
    // Recording channels (P1-P6)
    var ch1 by remember { mutableStateOf(true) }
    var ch2 by remember { mutableStateOf(true) }
    var ch3 by remember { mutableStateOf(true) }
    var ch4 by remember { mutableStateOf(true) }
    var ch5 by remember { mutableStateOf(false) }
    var ch6 by remember { mutableStateOf(false) }
    
    // Timing
    var nrOfSamples by remember { mutableIntStateOf(60000) }
    var timeBase by remember { mutableStateOf("1ms") }
    
    // Calculate max samples based on active channels (tylko P1-P4)
    val activeChannels = listOf(ch1, ch2, ch3, ch4).count { it }
    val maxSamples = when (activeChannels) {
        1 -> 241000
        2 -> 121000
        3 -> 61000
        4 -> 61000
        else -> 0
    }
    
    // Calculate duration
    val timeBaseFactor = when(timeBase) {
        "1ms" -> 1
        "10ms" -> 10
        "100ms" -> 100
        "1s" -> 1000
        "10s" -> 10000
        else -> 1
    }
    val durationSeconds = (nrOfSamples * timeBaseFactor) / 1000
    
    // Adjust samples if exceeds max
    if (nrOfSamples > maxSamples - 1000) {
        nrOfSamples = maxSamples - 1000
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_offline_recording_setup)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.content_desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1E293B)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Trigger Conditions Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.section_trigger_conditions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Trigger Channel
                    Text(stringResource(R.string.label_trigger_channel_p1_p4), color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { ch ->
                            FilterChip(
                                selected = triggerChannel == ch,
                                onClick = { triggerChannel = ch },
                                label = { Text("P$ch") }
                            )
                        }
                        // P5 i P6 wyłączone
                        (5..6).forEach { ch ->
                            FilterChip(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                label = { Text("P$ch") }
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Threshold
                    Text(stringResource(R.string.label_threshold_percent, triggerThreshold), color = Color(0xFF94A3B8))
                    Slider(
                        value = triggerThreshold.toFloat(),
                        onValueChange = { triggerThreshold = it.toInt() },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Edge
                    Text(stringResource(R.string.label_edge), color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = triggerEdge == "Rising",
                            onClick = { triggerEdge = "Rising" },
                            label = { Text(stringResource(R.string.option_rising_edge)) }
                        )
                        FilterChip(
                            selected = triggerEdge == "Falling",
                            onClick = { triggerEdge = "Falling" },
                            label = { Text(stringResource(R.string.option_falling_edge)) }
                        )
                    }
                }
            }
            
            // Recording Channels Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.section_recording_channels),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        stringResource(R.string.info_active_channels_max_samples, activeChannels, maxSamples/1000),
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Checkboxy jeden pod drugim
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = ch1, onCheckedChange = { ch1 = it })
                        Text("P1", color = Color.White)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = ch2, onCheckedChange = { ch2 = it })
                        Text("P2", color = Color.White)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = ch3, onCheckedChange = { ch3 = it })
                        Text("P3", color = Color.White)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = ch4, onCheckedChange = { ch4 = it })
                        Text("P4", color = Color.White)
                    }
                }
            }
            
            // Timing Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.section_timing),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Number of Samples
                    Text(stringResource(R.string.label_nr_of_samples, nrOfSamples), color = Color(0xFF94A3B8))
                    Slider(
                        value = nrOfSamples.toFloat(),
                        onValueChange = { nrOfSamples = (it / 1000).toInt() * 1000 },
                        valueRange = 2000f..maxSamples.toFloat(),
                        steps = (maxSamples - 2000) / 1000,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Time Base
                    Text(stringResource(R.string.label_time_base), color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1ms", "10ms", "100ms", "1s", "10s").forEach { tb ->
                            FilterChip(
                                selected = timeBase == tb,
                                onClick = { timeBase = tb },
                                label = { Text(tb) }
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Duration (calculated)
                    Text(
                        stringResource(R.string.label_duration_seconds, durationSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        val rc = buildString {
                            append(if (ch1) "1" else "0")
                            append(if (ch2) "1" else "0")
                            append(if (ch3) "1" else "0")
                            append(if (ch4) "1" else "0")
                            // Tylko P1-P4, P5-P6 nie są obsługiwane przez offline recording
                        }
                        val edge = if (triggerEdge == "Rising") 0 else 1
                        onStartRecording(rc, triggerChannel, triggerThreshold, edge, nrOfSamples, timeBaseFactor)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text(stringResource(R.string.button_start_recording))
                }
                
                Button(
                    onClick = onStopRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(stringResource(R.string.button_stop_recording))
                }
            }
            
            // Info text
            Text(
                stringResource(R.string.info_trigger_wait_message),
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
