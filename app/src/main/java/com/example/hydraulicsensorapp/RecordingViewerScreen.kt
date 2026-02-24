package com.example.hydraulicsensorapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
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
                            stringResource(R.string.label_sample_count, csvData?.samples?.size ?: 0),
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.content_desc_back), tint = Color.White)
                    }
                },
                actions = {
                    // Toggle view mode
                    TextButton(
                        onClick = { viewMode = if (viewMode == "chart") "table" else "chart" }
                    ) {
                        Text(
                            if (viewMode == "chart") "📊 " + stringResource(R.string.button_view_table) else "📈 " + stringResource(R.string.button_view_chart),
                            color = Color(0xFF3B82F6),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
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
                    var xRange by remember { mutableStateOf(0f..(data.samples.size.toFloat())) }
                    val rawMin = channel.min ?: 0f
                    val rawMax = channel.max ?: 100f
                    // Zapewnij że zakres Y nie jest zerowy (min != max)
                    val safeYMin = if (rawMin == rawMax) rawMin - 1f else rawMin
                    val safeYMax = if (rawMin == rawMax) rawMax + 1f else rawMax
                    var yRange by remember { mutableStateOf(safeYMin..safeYMax) }
                    
                    Text(
                        "P${channel.index} (${channel.unit})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = getChannelColor(channel.index)
                    )
                    
                    Text(
                        stringResource(R.string.label_stats_min_max_avg, 
                            channel.min?.let { "%.2f".format(it) } ?: "N/A",
                            channel.max?.let { "%.2f".format(it) } ?: "N/A",
                            channel.avg?.let { "%.2f".format(it) } ?: "N/A"
                        ),
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // X-axis range control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Oś X: ${xRange.start.toInt()} - ${xRange.endInclusive.toInt()}",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = { 
                                xRange = 0f..(data.samples.size.toFloat())
                                yRange = safeYMin..safeYMax
                            }
                        ) {
                            Text("Reset", color = Color(0xFF3B82F6), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    RangeSlider(
                        value = xRange,
                        onValueChange = { xRange = it },
                        valueRange = 0f..(data.samples.size.toFloat()),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF3B82F6),
                            activeTrackColor = Color(0xFF3B82F6),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Y-axis range control
                    Text(
                        "Oś Y: %.2f - %.2f %s".format(yRange.start, yRange.endInclusive, channel.unit),
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    RangeSlider(
                        value = yRange,
                        onValueChange = { yRange = it },
                        valueRange = safeYMin..safeYMax,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Filter data to selected X range
                    val allData = data.samples.mapNotNull { it.values[channel.index] }
                    val startIdx = xRange.start.toInt().coerceIn(0, allData.size)
                    val endIdx = xRange.endInclusive.toInt().coerceIn(0, allData.size)
                    val filteredData = if (startIdx < endIdx) allData.subList(startIdx, endIdx) else allData
                    
                    // Draw chart with custom Y range
                    LineChart(
                        data = filteredData,
                        color = getChannelColor(channel.index),
                        minValue = yRange.start,
                        maxValue = yRange.endInclusive,
                        unit = channel.unit,
                        sampleCount = filteredData.size,
                        startSampleOffset = startIdx,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
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
    unit: String,
    sampleCount: Int,
    startSampleOffset: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedX by remember { mutableFloatStateOf(-1f) }
    var selectedValue by remember { mutableFloatStateOf(0f) }
    var selectedSample by remember { mutableIntStateOf(0) }
    
    Canvas(modifier = modifier
        .background(Color(0xFF0F172A))
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                selectedX = offset.x
                // Oblicz który sample został kliknięty
                val leftPadding = 170f
                val padding = 60f
                val chartWidth = size.width - leftPadding - padding
                if (offset.x >= leftPadding && offset.x <= size.width - padding) {
                    val relativeX = offset.x - leftPadding
                    val sampleIndex = ((relativeX / chartWidth) * (data.size - 1)).toInt()
                        .coerceIn(0, data.size - 1)
                    selectedSample = sampleIndex + startSampleOffset
                    selectedValue = data[sampleIndex]
                }
            }
        }
    ) {
        if (data.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val padding = 60f
        val leftPadding = 170f
        val bottomPadding = 70f
        
        val chartWidth = width - leftPadding - padding
        val chartHeight = height - padding - bottomPadding
        
        // Calculate range with some padding
        val range = maxValue - minValue
        val effectiveRange = if (range == 0f) 1f else range
        val paddedMin = minValue - effectiveRange * 0.1f
        val paddedMax = maxValue + effectiveRange * 0.1f
        val paddedRange = paddedMax - paddedMin
        
        // Draw grid lines
        val gridColor = androidx.compose.ui.graphics.Color(0xFF334155)
        for (i in 0..4) {
            val y = padding + (chartHeight / 4) * i
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }
        
        // Draw Y-axis labels
        val paintYLabels = android.graphics.Paint().apply {
            textSize = 32f
            setColor(android.graphics.Color.parseColor("#94A3B8"))
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        for (i in 0..4) {
            val y = padding + (chartHeight / 4) * i
            val value = paddedMax - (paddedMax - paddedMin) * (i / 4f)
            drawContext.canvas.nativeCanvas.drawText(
                "%.2f".format(value),
                leftPadding - 20f,
                y + 10f,
                paintYLabels
            )
        }
        
        // Draw Y-axis label (unit)
        val paintYAxisLabel = android.graphics.Paint().apply {
            textSize = 34f
            setColor(android.graphics.Color.parseColor("#E2E8F0"))
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.rotate(-90f, 18f, height / 2)
        drawContext.canvas.nativeCanvas.drawText(
            unit,
            18f,
            height / 2 + 12f,
            paintYAxisLabel
        )
        drawContext.canvas.nativeCanvas.restore()
        
        // Draw axes
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFF64748B),
            start = Offset(leftPadding, padding),
            end = Offset(leftPadding, height - bottomPadding),
            strokeWidth = 2f
        )
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFF64748B),
            start = Offset(leftPadding, height - bottomPadding),
            end = Offset(width - padding, height - bottomPadding),
            strokeWidth = 2f
        )
        
        // Draw X-axis label (Sample number)
        val paintXAxisLabel = android.graphics.Paint().apply {
            textSize = 38f
            setColor(android.graphics.Color.parseColor("#E2E8F0"))
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            context.getString(R.string.label_chart_x_axis),
            (leftPadding + width - padding) / 2,
            height - 5f,
            paintXAxisLabel
        )
        
        // Draw X-axis tick labels
        val paintXTicks = android.graphics.Paint().apply {
            textSize = 30f
            setColor(android.graphics.Color.parseColor("#94A3B8"))
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (i in 0..4) {
            val x = leftPadding + (chartWidth / 4) * i
            val sampleNum = ((sampleCount / 4f * i).toInt() + startSampleOffset)
            drawContext.canvas.nativeCanvas.drawText(
                sampleNum.toString(),
                x,
                height - bottomPadding + 35f,
                paintXTicks
            )
        }
        
        // Draw data line
        if (data.size > 1) {
            val path = Path()
            
            data.forEachIndexed { index, value ->
                val x = leftPadding + (chartWidth / (data.size - 1)) * index
                val normalizedValue = (value - paddedMin) / paddedRange
                val y = height - bottomPadding - (normalizedValue * chartHeight)
                
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
                val x = leftPadding + (chartWidth / (data.size - 1)) * index
                val normalizedValue = (value - paddedMin) / paddedRange
                val y = height - bottomPadding - (normalizedValue * chartHeight)
                
                drawCircle(
                    color = color,
                    radius = 4f,
                    center = Offset(x, y)
                )
            }
        }
        
        // Draw selection indicator (vertical line + value label)
        if (selectedX >= leftPadding && selectedX <= width - padding) {
            // Vertical line
            drawLine(
                color = Color(0xFFFBBF24),
                start = Offset(selectedX, padding),
                end = Offset(selectedX, height - bottomPadding),
                strokeWidth = 3f
            )
            
            // Value label with background
            val labelPaint = android.graphics.Paint().apply {
                textSize = 40f
                setColor(android.graphics.Color.parseColor("#FFFFFF"))
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            
            val labelText = "%.2f %s".format(selectedValue, unit)
            val labelWidth = labelPaint.measureText(labelText)
            val labelHeight = 50f
            val labelPadding = 10f
            
            // Position label above the line
            var labelX = selectedX
            var labelY = padding - 20f
            
            // Keep label within bounds
            if (labelX - labelWidth / 2 - labelPadding < 0) {
                labelX = labelWidth / 2 + labelPadding
            }
            if (labelX + labelWidth / 2 + labelPadding > width) {
                labelX = width - labelWidth / 2 - labelPadding
            }
            
            // Draw background rectangle
            drawRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(labelX - labelWidth / 2 - labelPadding, labelY - labelHeight),
                size = androidx.compose.ui.geometry.Size(labelWidth + labelPadding * 2, labelHeight)
            )
            
            // Draw border
            drawRect(
                color = Color(0xFFFBBF24),
                topLeft = Offset(labelX - labelWidth / 2 - labelPadding, labelY - labelHeight),
                size = androidx.compose.ui.geometry.Size(labelWidth + labelPadding * 2, labelHeight),
                style = Stroke(width = 2f)
            )
            
            // Draw text
            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                labelX,
                labelY - labelHeight / 2 + 14f,
                labelPaint
            )
            
            // Draw sample number below
            val samplePaint = android.graphics.Paint().apply {
                textSize = 32f
                setColor(android.graphics.Color.parseColor("#94A3B8"))
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Sample: $selectedSample",
                labelX,
                height - bottomPadding + 60f,
                samplePaint
            )
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
                // Sample number column header
                Text(
                    stringResource(R.string.label_sample),
                    modifier = Modifier.width(70.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                
                // Time column
                Text(
                    stringResource(R.string.label_time_seconds),
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
    
    // Pomiń linie komentarzy (zaczynające się od #) i puste
    val dataLines = lines.filter { it.isNotBlank() && !it.startsWith("#") }
    if (dataLines.isEmpty()) return CSVData(emptyList(), emptyList())
    
    val header = dataLines[0].split(";")
    android.util.Log.d("RecordingViewer", "📋 Header: ${header.joinToString(" | ")}")
    
    // Wykryj format: Offline (Sample;P1;P2...) vs Live (Timestamp;Elapsed_s;P1_unit...)
    val isOfflineFormat = header.firstOrNull()?.trim() == "Sample"
    android.util.Log.d("RecordingViewer", "📂 Format: ${if (isOfflineFormat) "OFFLINE" else "LIVE"}")
    
    val channels = mutableListOf<ChannelInfo>()
    
    if (isOfflineFormat) {
        // Format offline: Sample;P1;P2;P3;P4
        // Pobierz jednostki z nagłówka komentarzy (#)
        val unitLine = lines.find { it.startsWith("# Units:") } ?: ""
        val unitMap = mutableMapOf<Int, String>()
        Regex("P(\\d+)=([^\\s]+)").findAll(unitLine).forEach { match ->
            val ch = match.groupValues[1].toIntOrNull() ?: return@forEach
            unitMap[ch] = match.groupValues[2]
        }
        
        header.drop(1).forEachIndexed { index, colName ->
            val channelIndex = colName.trim().removePrefix("P").toIntOrNull()
            if (channelIndex != null) {
                val unit = unitMap[channelIndex] ?: ""
                channels.add(ChannelInfo(channelIndex, unit, null, null, null))
            }
        }
        
        val samples = dataLines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split(";")
            val sampleIndex = parts[0].replace(",", ".").toFloatOrNull() ?: return@mapNotNull null
            val values = mutableMapOf<Int, Float>()
            channels.forEachIndexed { index, channel ->
                val valueStr = parts.getOrNull(index + 1) ?: return@forEachIndexed
                values[channel.index] = valueStr.replace(",", ".").toFloatOrNull() ?: return@forEachIndexed
            }
            SampleData(sampleIndex, values)
        }
        
        val channelsWithStats = channels.map { channel ->
            val channelValues = samples.mapNotNull { it.values[channel.index] }
            channel.copy(
                min = channelValues.minOrNull(),
                max = channelValues.maxOrNull(),
                avg = if (channelValues.isNotEmpty()) channelValues.average().toFloat() else null
            )
        }
        android.util.Log.d("RecordingViewer", "✅ Offline: ${channelsWithStats.size} channels, ${samples.size} samples")
        return CSVData(channelsWithStats, samples)
        
    } else {
        // Format Live: Timestamp;Elapsed_s;P1_bar;P2_bar...
        header.drop(2).forEachIndexed { index, columnName ->
            if (columnName.startsWith("P")) {
                val parts = columnName.split("_")
                val channelIndex = parts[0].removePrefix("P").toIntOrNull()
                val unit = parts.getOrNull(1) ?: ""
                if (channelIndex != null) {
                    channels.add(ChannelInfo(channelIndex, unit, null, null, null))
                }
            }
        }
        
        val samples = dataLines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split(";")
            if (parts.size < 2) return@mapNotNull null
            val elapsed = parts[1].replace(",", ".").toFloatOrNull() ?: return@mapNotNull null
            val values = mutableMapOf<Int, Float>()
            channels.forEachIndexed { index, channel ->
                val valueStr = parts.getOrNull(index + 2) ?: return@forEachIndexed
                values[channel.index] = valueStr.replace(",", ".").toFloatOrNull() ?: return@forEachIndexed
            }
            SampleData(elapsed, values)
        }
        
        val channelsWithStats = channels.map { channel ->
            val channelValues = samples.mapNotNull { it.values[channel.index] }
            channel.copy(
                min = channelValues.minOrNull(),
                max = channelValues.maxOrNull(),
                avg = if (channelValues.isNotEmpty()) channelValues.average().toFloat() else null
            )
        }
        android.util.Log.d("RecordingViewer", "✅ Live: ${channelsWithStats.size} channels, ${samples.size} samples")
        return CSVData(channelsWithStats, samples)
    }
}
