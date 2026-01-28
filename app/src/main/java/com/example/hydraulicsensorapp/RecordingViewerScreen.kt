package com.example.hydraulicsensorapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingViewerScreen(
    file: File,
    onBack: () -> Unit
) {
    var viewMode by remember { mutableStateOf("chart") } // "chart" or "table"
    var csvData by remember { mutableStateOf<CSVData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load CSV data
    LaunchedEffect(Unit) {
        csvData = parseCSVFile(file)
        isLoading = false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${csvData?.samples?.size ?: 0} samples",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Toggle view mode
                    TextButton(
                        onClick = { viewMode = if (viewMode == "chart") "table" else "chart" }
                    ) {
                        Text(
                            if (viewMode == "chart") "📊 Table" else "📈 Chart",
                            color = Color(0xFF3B82F6)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else {
            csvData?.let { data ->
                when (viewMode) {
                    "chart" -> ChartView(data, Modifier.padding(padding))
                    "table" -> TableView(data, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun ChartView(data: CSVData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Legend - pokazuje które kanały są aktywne
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                data.channels.forEach { channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(getChannelColor(channel.index), shape = MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "P${channel.index}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Chart for each channel
        data.channels.forEach { channel ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "P${channel.index} (${channel.unit})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = getChannelColor(channel.index)
                    )
                    
                    Text(
                        "Min: ${channel.min?.let { "%.2f".format(it) } ?: "N/A"} | " +
                        "Max: ${channel.max?.let { "%.2f".format(it) } ?: "N/A"} | " +
                        "Avg: ${channel.avg?.let { "%.2f".format(it) } ?: "N/A"}",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Draw chart
                    LineChart(
                        data = data.samples.mapNotNull { it.values[channel.index] },
                        color = getChannelColor(channel.index),
                        minValue = channel.min ?: 0f,
                        maxValue = channel.max ?: 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun LineChart(
    data: List<Float>,
    color: Color,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(Color(0xFF0F172A))) {
        if (data.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val padding = 40f
        
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        
        // Calculate range with some padding
        val range = maxValue - minValue
        val paddedMin = minValue - range * 0.1f
        val paddedMax = maxValue + range * 0.1f
        val paddedRange = paddedMax - paddedMin
        
        // Draw grid lines
        val gridColor = androidx.compose.ui.graphics.Color(0xFF334155)
        for (i in 0..4) {
            val y = padding + (chartHeight / 4) * i
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }
        
        // Draw axes
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFF64748B),
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = 2f
        )
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFF64748B),
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = 2f
        )
        
        // Draw data line
        if (data.size > 1) {
            val path = Path()
            
            data.forEachIndexed { index, value ->
                val x = padding + (chartWidth / (data.size - 1)) * index
                val normalizedValue = (value - paddedMin) / paddedRange
                val y = height - padding - (normalizedValue * chartHeight)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3f)
            )
            
            // Draw points
            data.forEachIndexed { index, value ->
                val x = padding + (chartWidth / (data.size - 1)) * index
                val normalizedValue = (value - paddedMin) / paddedRange
                val y = height - padding - (normalizedValue * chartHeight)
                
                drawCircle(
                    color = color,
                    radius = 4f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun TableView(data: CSVData, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                // Sample number column
                Text(
                    "Sample",
                    modifier = Modifier.width(70.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                
                // Time column
                Text(
                    "Time (s)",
                    modifier = Modifier.width(80.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                
                // Channel columns
                data.channels.forEach { channel ->
                    Text(
                        "P${channel.index}\n${channel.unit}",
                        modifier = Modifier.width(80.dp),
                        color = getChannelColor(channel.index),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        Divider(color = Color(0xFF334155))
        
        // Data rows
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(data.samples) { index, sample ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Color(0xFF0F172A) else Color(0xFF1E293B))
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    // Sample number
                    Text(
                        "$index",
                        modifier = Modifier.width(70.dp),
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Time
                    Text(
                        "%.3f".format(sample.elapsed),
                        modifier = Modifier.width(80.dp),
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Channel values
                    data.channels.forEach { channel ->
                        val value = sample.values[channel.index]
                        Text(
                            value?.let { "%.3f".format(it) } ?: "-",
                            modifier = Modifier.width(80.dp),
                            color = if (value != null) Color.White else Color(0xFF64748B),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                if (index < data.samples.size - 1) {
                    Divider(color = Color(0xFF334155), thickness = 0.5.dp)
                }
            }
        }
    }
}

fun getChannelColor(channelIndex: Int): Color {
    return when (channelIndex) {
        1 -> Color(0xFFEF4444) // Red
        2 -> Color(0xFF3B82F6) // Blue
        3 -> Color(0xFF10B981) // Green
        4 -> Color(0xFFF59E0B) // Orange
        5 -> Color(0xFF8B5CF6) // Purple
        6 -> Color(0xFFEC4899) // Pink
        else -> Color.White
    }
}

// Data classes
data class CSVData(
    val channels: List<ChannelInfo>,
    val samples: List<SampleData>
)

data class ChannelInfo(
    val index: Int,
    val unit: String,
    val min: Float?,
    val max: Float?,
    val avg: Float?
)

data class SampleData(
    val elapsed: Float,
    val values: Map<Int, Float> // channel index -> value
)

fun parseCSVFile(file: File): CSVData {
    android.util.Log.d("RecordingViewer", "📄 Parsing file: ${file.name}")
    
    val lines = file.readLines()
    android.util.Log.d("RecordingViewer", "📊 Total lines: ${lines.size}")
    
    if (lines.isEmpty()) return CSVData(emptyList(), emptyList())
    
    // Parse header
    val header = lines[0].split(";")
    android.util.Log.d("RecordingViewer", "📋 Header: ${header.joinToString(" | ")}")
    
    val channels = mutableListOf<ChannelInfo>()
    
    // Find channel columns (skip Timestamp and Elapsed_s)
    header.drop(2).forEachIndexed { index, columnName ->
        android.util.Log.d("RecordingViewer", "  Column $index: '$columnName'")
        if (columnName.startsWith("P")) {
            val parts = columnName.split("_")
            val channelIndex = parts[0].removePrefix("P").toIntOrNull()
            val unit = parts.getOrNull(1) ?: ""
            android.util.Log.d("RecordingViewer", "    → P$channelIndex unit=$unit")
            if (channelIndex != null) {
                channels.add(ChannelInfo(channelIndex, unit, null, null, null))
            }
        }
    }
    
    android.util.Log.d("RecordingViewer", "✅ Found ${channels.size} channels: ${channels.map { "P${it.index}" }}")
    
    // Parse data
    val samples = lines.drop(1).mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        
        val parts = line.split(";")
        if (parts.size < 2) {
            android.util.Log.w("RecordingViewer", "⚠️ Line too short: ${parts.size} parts")
            return@mapNotNull null
        }
        
        // Replace comma with dot for parsing (Polish locale uses comma)
        val elapsed = parts[1].replace(",", ".").toFloatOrNull()
        if (elapsed == null) {
            android.util.Log.w("RecordingViewer", "⚠️ Cannot parse elapsed: '${parts[1]}'")
            return@mapNotNull null
        }
        
        val values = mutableMapOf<Int, Float>()
        
        channels.forEachIndexed { index, channel ->
            val valueStr = parts.getOrNull(index + 2) ?: return@forEachIndexed
            // Replace comma with dot for parsing
            val value = valueStr.replace(",", ".").toFloatOrNull() ?: return@forEachIndexed
            values[channel.index] = value
        }
        
        SampleData(elapsed, values)
    }
    
    android.util.Log.d("RecordingViewer", "✅ Parsed ${samples.size} samples")
    
    // Calculate statistics
    val channelsWithStats = channels.map { channel ->
        val channelValues = samples.mapNotNull { it.values[channel.index] }
        android.util.Log.d("RecordingViewer", "  P${channel.index}: ${channelValues.size} values, min=${channelValues.minOrNull()}, max=${channelValues.maxOrNull()}")
        channel.copy(
            min = channelValues.minOrNull(),
            max = channelValues.maxOrNull(),
            avg = if (channelValues.isNotEmpty()) channelValues.average().toFloat() else null
        )
    }
    
    return CSVData(channelsWithStats, samples)
}
