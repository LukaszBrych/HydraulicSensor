package com.example.hydraulicsensorapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRecordingsScreen(
    onBack: () -> Unit,
    onViewFile: (File) -> Unit
) {
    val context = LocalContext.current
    var recordingFiles by remember { mutableStateOf<List<RecordingFile>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf<RecordingFile?>(null) }
    
    // Load files
    LaunchedEffect(Unit) {
        recordingFiles = loadRecordingFiles(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_live_recordings), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.content_desc_back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        if (recordingFiles.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.empty_state_no_recordings),
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.empty_state_start_reading),
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recordingFiles) { file ->
                    RecordingCard(
                        file = file,
                        onView = {
                            onViewFile(file.file)
                        },
                        onShare = {
                            shareFile(context, file.file)
                        },
                        onDelete = {
                            showDeleteDialog = file
                        }
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.dialog_title_delete_recording)) },
            text = { Text(stringResource(R.string.dialog_message_delete_recording, file.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        file.file.delete()
                        recordingFiles = loadRecordingFiles(context)
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.button_delete), color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}

@Composable
fun RecordingCard(
    file: RecordingFile,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onView: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // File name
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // File info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_date, file.formattedDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = stringResource(R.string.label_size, file.formattedSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = stringResource(R.string.label_samples_count, file.sampleCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onView,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF10B981)
                    )
                ) {
                    Text("👁️ " + stringResource(R.string.button_view))
                }
                
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF3B82F6)
                    )
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.button_share))
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFEF4444)
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.button_delete))
                }
            }
        }
    }
}

data class RecordingFile(
    val file: File,
    val name: String,
    val formattedDate: String,
    val formattedSize: String,
    val sampleCount: Int
)

fun loadRecordingFiles(context: android.content.Context): List<RecordingFile> {
    val dir = File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
        "HydraulicSensorApp/LiveReadings"
    )
    
    if (!dir.exists()) return emptyList()
    
    return dir.listFiles { file -> file.extension == "csv" }
        ?.sortedByDescending { it.lastModified() }
        ?.map { file ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = Date(file.lastModified())
            
            // Count lines (samples)
            val lineCount = try {
                file.readLines().size - 1 // -1 for header
            } catch (e: Exception) {
                0
            }
            
            // Format size
            val sizeKB = file.length() / 1024
            val formattedSize = if (sizeKB < 1024) {
                "$sizeKB KB"
            } else {
                String.format("%.2f MB", sizeKB / 1024.0)
            }
            
            RecordingFile(
                file = file,
                name = file.name,
                formattedDate = dateFormat.format(date),
                formattedSize = formattedSize,
                sampleCount = lineCount
            )
        } ?: emptyList()
}

fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share CSV"))
    } catch (e: Exception) {
        android.util.Log.e("LiveRecordings", "Failed to share file", e)
    }
}
