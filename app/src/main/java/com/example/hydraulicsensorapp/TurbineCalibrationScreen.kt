package com.example.hydraulicsensorapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurbineCalibrationScreen(
    onBackClick: () -> Unit,
    onSendCommand: (String) -> Unit,
    initialCalibrationData: Map<String, List<String>> = emptyMap(),
    onLoadData: () -> Unit,
    turbineNames: Map<String, String> = emptyMap(),
    onSaveTurbineName: (String, String) -> Unit = { _, _ -> },
    onSaveComplete: () -> Unit = {}  // Callback po zapisaniu kalibracji
) {
    // State for P5 R1-R5 and P6 R1-R2
    val p5r1 = remember { mutableStateListOf("", "", "", "", "", "") }
    val p5r2 = remember { mutableStateListOf("", "", "", "", "", "") }
    val p5r3 = remember { mutableStateListOf("", "", "", "", "", "") }
    val p5r4 = remember { mutableStateListOf("", "", "", "", "", "") }
    val p5r5 = remember { mutableStateListOf("", "", "", "", "", "") }
    val p6r1 = remember { mutableStateListOf("", "", "", "", "", "") }
    val p6r2 = remember { mutableStateListOf("", "", "", "", "", "") }
    
    // Turbine names
    var p5r1Name by remember { mutableStateOf(turbineNames["P5R1"] ?: "") }
    var p5r2Name by remember { mutableStateOf(turbineNames["P5R2"] ?: "") }
    var p5r3Name by remember { mutableStateOf(turbineNames["P5R3"] ?: "") }
    var p5r4Name by remember { mutableStateOf(turbineNames["P5R4"] ?: "") }
    var p5r5Name by remember { mutableStateOf(turbineNames["P5R5"] ?: "") }
    var p6r1Name by remember { mutableStateOf(turbineNames["P6R1"] ?: "") }
    var p6r2Name by remember { mutableStateOf(turbineNames["P6R2"] ?: "") }
    
    // Load initial data when screen opens
    LaunchedEffect(Unit) {
        onLoadData()
    }
    
    // Update fields when data arrives - observe the map itself
    LaunchedEffect(initialCalibrationData.hashCode()) {
        android.util.Log.d("TurbineCalibration", "Data received: $initialCalibrationData")
        initialCalibrationData["K51"]?.let { values ->
            if (values.size >= 6) {
                values.forEachIndexed { index, value -> p5r1[index] = value }
            }
        }
        initialCalibrationData["K52"]?.let { values ->
            if (values.size >= 6) {
                values.forEachIndexed { index, value -> p5r2[index] = value }
            }
        }
        initialCalibrationData["K53"]?.let { values ->
            if (values.size >= 6) {
                values.forEachIndexed { index, value -> p5r3[index] = value }
            }
        }
        initialCalibrationData["K54"]?.let { values ->
            if (values.size >= 6) {
                values.forEachIndexed { index, value -> p5r4[index] = value }
            }
        }
        initialCalibrationData["K55"]?.let { values ->
            if (values.size >= 6) {
                values.forEachIndexed { index, value -> p5r5[index] = value }
            }
        }
        initialCalibrationData["K61"]?.let { values ->
            if (values.size >= 6) {
                values.forEachIndexed { index, value -> p6r1[index] = value }
            }
        }
        initialCalibrationData["K62"]?.let { values ->
            if (values.size >= 6) {
                values.forEachIndexed { index, value -> p6r2[index] = value }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_turbine_calibration)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // P5 R1
            TurbineRangeCard(stringResource(R.string.turbine_range_p5_r1), p5r1, p5r1Name) { p5r1Name = it }
            
            // P5 R2
            TurbineRangeCard(stringResource(R.string.turbine_range_p5_r2), p5r2, p5r2Name) { p5r2Name = it }
            
            // P5 R3
            TurbineRangeCard(stringResource(R.string.turbine_range_p5_r3), p5r3, p5r3Name) { p5r3Name = it }
            
            // P5 R4
            TurbineRangeCard(stringResource(R.string.turbine_range_p5_r4), p5r4, p5r4Name) { p5r4Name = it }
            
            // P5 R5
            TurbineRangeCard(stringResource(R.string.turbine_range_p5_r5), p5r5, p5r5Name) { p5r5Name = it }
            
            // P6 R1
            TurbineRangeCard(stringResource(R.string.turbine_range_p6_r1), p6r1, p6r1Name) { p6r1Name = it }
            
            // P6 R2
            TurbineRangeCard(stringResource(R.string.turbine_range_p6_r2), p6r2, p6r2Name) { p6r2Name = it }
            
            // Save Button
            Button(
                onClick = {
                    // Save turbine names
                    onSaveTurbineName("P5R1", p5r1Name)
                    onSaveTurbineName("P5R2", p5r2Name)
                    onSaveTurbineName("P5R3", p5r3Name)
                    onSaveTurbineName("P5R4", p5r4Name)
                    onSaveTurbineName("P5R5", p5r5Name)
                    onSaveTurbineName("P6R1", p6r1Name)
                    onSaveTurbineName("P6R2", p6r2Name)
                    
                    // Send commands for each range that has values
                    if (p5r1.any { it.isNotEmpty() }) {
                        val cmd = "K51 ${p5r1.joinToString(" ")}"
                        onSendCommand(cmd)
                    }
                    if (p5r2.any { it.isNotEmpty() }) {
                        val cmd = "K52 ${p5r2.joinToString(" ")}"
                        onSendCommand(cmd)
                    }
                    if (p5r3.any { it.isNotEmpty() }) {
                        val cmd = "K53 ${p5r3.joinToString(" ")}"
                        onSendCommand(cmd)
                    }
                    if (p5r4.any { it.isNotEmpty() }) {
                        val cmd = "K54 ${p5r4.joinToString(" ")}"
                        onSendCommand(cmd)
                    }
                    if (p5r5.any { it.isNotEmpty() }) {
                        val cmd = "K55 ${p5r5.joinToString(" ")}"
                        onSendCommand(cmd)
                    }
                    if (p6r1.any { it.isNotEmpty() }) {
                        val cmd = "K61 ${p6r1.joinToString(" ")}"
                        onSendCommand(cmd)
                    }
                    if (p6r2.any { it.isNotEmpty() }) {
                        val cmd = "K62 ${p6r2.joinToString(" ")}"
                        onSendCommand(cmd)
                    }
                    
                    // Call onSaveComplete to return to main screen with message
                    onSaveComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                )
            ) {
                Text(stringResource(R.string.button_save_calibration), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun TurbineRangeCard(
    title: String, 
    values: MutableList<String>,
    turbineName: String,
    onNameChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            // Turbine Name field
            Column {
                Text(
                    stringResource(R.string.label_turbine_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = turbineName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.placeholder_turbine_name), color = Color(0xFF64748B)) }
                )
            }
            
            // Grid: 2 columns x 3 rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Row 1
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TurbineTextField(stringResource(R.string.label_hz), values[0], modifier = Modifier.weight(1f)) { values[0] = it }
                    TurbineTextField(stringResource(R.string.label_lpm), values[1], modifier = Modifier.weight(1f)) { values[1] = it }
                }
                
                // Row 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TurbineTextField(stringResource(R.string.label_hz), values[2], modifier = Modifier.weight(1f)) { values[2] = it }
                    TurbineTextField(stringResource(R.string.label_lpm), values[3], modifier = Modifier.weight(1f)) { values[3] = it }
                }
                
                // Row 3
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TurbineTextField(stringResource(R.string.label_hz), values[4], modifier = Modifier.weight(1f)) { values[4] = it }
                    TurbineTextField(stringResource(R.string.label_lpm), values[5], modifier = Modifier.weight(1f)) { values[5] = it }
                }
            }
        }
    }
}

@Composable
fun TurbineTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = { newValue -> onValueChange(newValue.replace(',', '.')) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF475569)
            ),
            singleLine = true
        )
    }
}
