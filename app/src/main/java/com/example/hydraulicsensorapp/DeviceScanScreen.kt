package com.example.hydraulicsensorapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay

data class ScannedDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScanScreen(
    onBackClick: () -> Unit,
    onDeviceSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scannedDevices by remember { mutableStateOf<List<ScannedDevice>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    // Check permissions
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Excluded device name patterns (case-insensitive)
    val excludedPatterns = listOf(
        "apple", "iphone", "ipad", "airpods", "watch",
        "microsoft", "surface",
        "samsung", "galaxy",
        "google", "pixel",
        "exposure notification", "contact tracing",
        "android", "wear os",
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "oppo", "vivo", "realme", "oneplus"
    )
    
    fun shouldExcludeDevice(deviceName: String?): Boolean {
        // Exclude devices without name
        if (deviceName == null || deviceName.isBlank()) return true
        
        // Exclude "Unknown Device"
        if (deviceName.equals("Unknown Device", ignoreCase = true)) return true
        
        // Exclude devices matching patterns
        val lowerName = deviceName.lowercase()
        return excludedPatterns.any { pattern -> lowerName.contains(pattern) }
    }
    
    val scanCallback = remember {
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Unknown Device"
                val deviceAddress = device.address
                val rssi = result.rssi
                
                // Filter out excluded devices
                if (shouldExcludeDevice(deviceName)) {
                    Log.d("BLEScan", "Filtered out device: $deviceName ($deviceAddress)")
                    return
                }
                
                // Add to list if not already present
                val existingDevice = scannedDevices.find { it.address == deviceAddress }
                if (existingDevice == null) {
                    scannedDevices = scannedDevices + ScannedDevice(deviceName, deviceAddress, rssi)
                    Log.d("BLEScan", "Found device: $deviceName ($deviceAddress) RSSI: $rssi")
                } else {
                    // Update RSSI if device already exists
                    scannedDevices = scannedDevices.map {
                        if (it.address == deviceAddress) it.copy(rssi = rssi) else it
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLEScan", "Scan failed with error: $errorCode")
                errorMessage = "Scan failed with error code: $errorCode"
                isScanning = false
            }
        }
    }
    
    fun stopScan() {
        if (!hasBluetoothPermissions()) {
            Log.w("BLEScan", "Missing permissions for stopScan")
            return
        }
        
        try {
            @SuppressLint("MissingPermission")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.d("BLEScan", "Scan stopped")
        } catch (e: Exception) {
            Log.e("BLEScan", "Failed to stop scan", e)
        }
        isScanning = false
    }
    
    fun startScan() {
        Log.d("BLEScan", "startScan() called")
        
        if (!hasBluetoothPermissions()) {
            errorMessage = "Bluetooth permissions not granted"
            Log.e("BLEScan", "Missing Bluetooth permissions")
            return
        }
        
        if (bluetoothAdapter == null) {
            errorMessage = "Bluetooth not available"
            Log.e("BLEScan", "BluetoothAdapter is null")
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            errorMessage = "Bluetooth is disabled. Please enable it."
            Log.e("BLEScan", "Bluetooth is disabled")
            return
        }
        
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            errorMessage = "BLE Scanner not available"
            Log.e("BLEScan", "BluetoothLeScanner is null")
            return
        }
        
        scannedDevices = emptyList()
        errorMessage = null
        isScanning = true
        
        try {
            @SuppressLint("MissingPermission")
            scanner.startScan(scanCallback)
            Log.d("BLEScan", "Scan started successfully")
        } catch (e: Exception) {
            Log.e("BLEScan", "Failed to start scan", e)
            errorMessage = "Failed to start scan: ${e.message}"
            isScanning = false
        }
    }
    
    // Auto-stop scan after 10 seconds
    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(10000) // 10 seconds
            stopScan()
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (isScanning) {
                stopScan()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_scan_devices)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isScanning) stopScan()
                        onBackClick()
                    }) {
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
                .padding(16.dp)
        ) {
            // Scan button
            Button(
                onClick = {
                    if (isScanning) {
                        stopScan()
                    } else {
                        startScan()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) Color(0xFFEF4444) else Color(0xFF10B981)
                )
            ) {
                Text(
                    if (isScanning) stringResource(R.string.button_stop_scan) else stringResource(R.string.button_start_scan),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Error message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Scanning indicator
            if (isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF3B82F6)
                        )
                        Text(
                            stringResource(R.string.label_scanning),
                            color = Color(0xFF93C5FD),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Device list header
            if (scannedDevices.isNotEmpty()) {
                Text(
                    stringResource(R.string.label_found_devices, scannedDevices.size),
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Device list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedDevices.sortedByDescending { it.rssi }) { device ->
                    DeviceCard(
                        device = device,
                        onClick = {
                            if (isScanning) stopScan()
                            onDeviceSelected(device.address)
                        }
                    )
                }
                
                if (scannedDevices.isEmpty() && !isScanning) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.label_no_devices_found),
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.hint_tap_scan_to_find_devices),
                                    color = Color(0xFF64748B),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: ScannedDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.address,
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // RSSI indicator
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    color = when {
                        device.rssi > -60 -> Color(0xFF10B981) // Strong
                        device.rssi > -80 -> Color(0xFFFBBF24) // Medium
                        else -> Color(0xFFEF4444) // Weak
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        device.rssi > -60 -> "Strong"
                        device.rssi > -80 -> "Medium"
                        else -> "Weak"
                    },
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
