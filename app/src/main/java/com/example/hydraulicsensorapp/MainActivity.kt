package com.example.hydraulicsensorapp

import android.Manifest
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import android.util.Log
import com.example.hydraulicsensorapp.ui.theme.HydraulicSensorAppTheme
import java.util.*

class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    private val channelValues = mutableStateListOf(
        "P1: ---", "P2: ---", "P3: ---", "P4: ---", "P5: ---", "P6: ---"
    )

    private val ranges = mutableStateListOf("10bar","10bar","10bar","125°C","605lpm","394lpm")
    private val pendingRanges = MutableList(6){ ranges[it] }
    private val endValUnitCache = MutableList(6){ MutableList(5){"---"} }

    private val connectionStatus = mutableStateOf("Rozłączono")
    private var isReadingLive = false

    private val charUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val serviceUuid = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val sensorMac = "3C:A5:09:7A:D3:23"

    private val rangeCodes = listOf(
        mapOf("10bar" to 1,"60bar" to 2,"250bar" to 3,"400bar" to 4,"600bar" to 5),
        mapOf("10bar" to 1,"60bar" to 2,"250bar" to 3,"400bar" to 4,"600bar" to 5),
        mapOf("10bar" to 1,"60bar" to 2,"250bar" to 3,"400bar" to 4,"600bar" to 5),
        mapOf("125°C" to 1,"500°C" to 2,"Custom" to 3),
        mapOf("605lpm" to 1,"27lpm" to 2,"1000lpm" to 3,"0lpm" to 4,"Custom" to 5),
        mapOf("394lpm" to 1,"1291lpm" to 2,"0.00lpm" to 3,"6000U/m" to 4,"Custom" to 5)
    )

    private val currentRanges = MutableList(6){ ranges[it] }

    // --- KOLEJKA BLE ---
    private val writeQueue = ArrayDeque<String>()
    private var isWriting = false

    private var notificationsEnabled = false
    private val WRITE_QUEUE_WARN_THRESHOLD = 20
    private var notificationSet = false //zapobiega podwójnej rejestracji BLE

    private fun enqueueWrite(gatt: BluetoothGatt, cmd: String) {
        writeQueue.add(cmd)
        if (writeQueue.size > WRITE_QUEUE_WARN_THRESHOLD) {
            Log.w("SensorBox", "writeQueue grew large: ${writeQueue.size}")
        }
        if (!isWriting) processNextWrite(gatt)
    }

    private fun processNextWrite(gatt: BluetoothGatt) {
        if (writeQueue.isEmpty()) {
            isWriting = false
            return
        }
        isWriting = true
        val cmd = writeQueue.removeFirst()
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w("SensorBox", "Brak BLUETOOTH_CONNECT przy processNextWrite")
                isWriting = false
                return
            }
            val service = gatt.getService(serviceUuid) ?: run {
                Log.w("SensorBox", "Service not found in processNextWrite")
                isWriting = false
                return
            }
            val characteristic = service.getCharacteristic(charUuid) ?: run {
                Log.w("SensorBox", "Characteristic not found in processNextWrite")
                isWriting = false
                return
            }

            characteristic.value = cmd.toByteArray()
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val ok = gatt.writeCharacteristic(characteristic)
            Log.d("SensorBox", "writeCharacteristic (enqueued): $cmd  -> result:$ok  queueRemaining:${writeQueue.size}")
            if (!ok) {
                handler.postDelayed({ processNextWrite(gatt) }, 100)
            }
        } catch (e: SecurityException) {
            Log.e("SensorBox", "SecurityException podczas zapisu: ${e.message}")
            isWriting = false
        }
    }

    // centralny callback GATT
    private val gattCallback = object : BluetoothGattCallback() {
        private var buffer = ""

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectionStatus.value = "Połączono z SensorBox"
                        Log.d("SensorBox", "Connected to GATT, status=$status")
                        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectionStatus.value = "Rozłączono"
                        Log.d("SensorBox", "Disconnected from GATT, status=$status")
                        writeQueue.clear()
                        isWriting = false
                        notificationsEnabled = false
                        notificationSet = false
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || notificationSet) {
                if (notificationSet) Log.d("SensorBox", "🔔 Powiadomienia już ustawione — pomijam duplikat")
                return
            }

            val service = gatt.getService(serviceUuid) ?: run {
                Log.w("SensorBox","Service not found onServicesDiscovered")
                return
            }
            val characteristic = service.getCharacteristic(charUuid) ?: run {
                Log.w("SensorBox","Characteristic not found onServicesDiscovered")
                return
            }

            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val ok = gatt.setCharacteristicNotification(characteristic, true)
                Log.d("SensorBox","setCharacteristicNotification ok=$ok")

                val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val writeDescOk = gatt.writeDescriptor(it)
                    Log.d("SensorBox", "writeDescriptor called -> $writeDescOk")
                    notificationSet = true // ✅ ustawione tylko raz
                } ?: run {
                    Log.w("SensorBox","Descriptor nie znaleziony — wznawiam bez potwierdzenia")
                    notificationsEnabled = true
                    notificationSet = true
                    startLiveRead()
                    sendAllRangesAndValues()
                }
            } else {
                Log.w("SensorBox","Brak permisji BLUETOOTH_CONNECT w onServicesDiscovered")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (descriptor.uuid.toString().equals("00002902-0000-1000-8000-00805f9b34fb", ignoreCase = true)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("SensorBox","CCCD descriptor written OK")
                    notificationsEnabled = true
                    startLiveRead()
                    sendAllRangesAndValues()
                    requestInfo(gatt,"e\n")
                    handler.postDelayed({ requestInfo(gatt,"u\n") },200)
                } else {
                    Log.w("SensorBox","CCCD descriptor write failed, status=$status")
                    handler.postDelayed({
                        try {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        } catch (e: Exception) {
                            Log.e("SensorBox","Błąd przy ponownym zapisie descriptor: ${e.message}")
                        }
                    }, 500)
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            isWriting = false
            Log.d("SensorBox", "onCharacteristicWrite status=$status remainingQueue=${writeQueue.size}")
            processNextWrite(gatt)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val raw = characteristic.value ?: return
            val text = String(raw)
            buffer += text
            Log.d("SensorBox", "Odebrano: $text")

            while (true) {
                val frameEnd = buffer.indexOfNth('#', 7)
                if (frameEnd == -1 || frameEnd + 1 > buffer.length) break
                val safeEnd = minOf(frameEnd + 1, buffer.length)
                val frame = buffer.substring(0, safeEnd)
                buffer = buffer.substring(safeEnd)

                val parts = frame.split("#").filter { it.isNotBlank() }
                if (parts.size >= 6) {
                    handler.post {
                        for (i in 0 until 6) {
                            val rawVal = parts[i].replace("[^0-9.-]".toRegex(), "")
                            val value = rawVal.toDoubleOrNull()
                            channelValues[i] =
                                if (value != null) "P${i + 1}: $value" else "P${i + 1}: brak czujnika"
                        }
                    }
                } else {
                    buffer = frame + buffer
                    break
                }
            }

            if (buffer.length > 512) buffer = buffer.takeLast(256)
        }

        private fun String.indexOfNth(char: Char, n: Int): Int {
            var count = 0
            for (i in indices) {
                if (this[i] == char) {
                    count++
                    if (count == n) return i
                }
            }
            return -1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        requestPermissionsIfNeeded()

        setContent {
            HydraulicSensorAppTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    OfflineRecordingScreen(
                        channelValues = channelValues,
                        ranges = ranges,
                        connectionStatus = connectionStatus.value,
                        onRangeChange = { index, range ->
                            // ✅ poprawione zachowanie zakresów
                            ranges[index] = range
                            pendingRanges[index] = range

                            if (isReadingLive) stopLiveRead()

                            gatt?.let { g ->
                                if (notificationsEnabled) {
                                    sendAllRangesAndValues()
                                    // Poczekaj, aż kolejka się opróżni, zanim wznowisz odczyt
                                    handler.postDelayed({
                                        if (writeQueue.isEmpty()) {
                                            startLiveRead()
                                        } else {
                                            handler.postDelayed({
                                                if (writeQueue.isEmpty()) startLiveRead()
                                            }, 500)
                                        }
                                    }, 800)
                                } else {
                                    Log.d("SensorBox","Range zmieniony, ale notificationsEnabled=false")
                                }
                            }
                        },
                        onStartRecording = { _, _, _, _, _, _ -> connectAndStart() },
                        onStopRecording = { stopLiveRead() },
                        onBack = { finish() }
                    )
                }
            }
        }
        Log.d("SensorBox","MainActivity uruchomiona poprawnie")
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        if (permissions.any{ActivityCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}){
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(),1)
        }
    }

    private fun connectAndStart() {
        if (bluetoothAdapter==null){
            Log.e("SensorBox","BluetoothAdapter null!")
            return
        }
        connectToDeviceByMac(sensorMac)
    }

    private fun connectToDeviceByMac(macAddress:String){
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            connectionStatus.value = "Proszę przyznać uprawnienia do BLE"
            return
        }
        val device = bluetoothAdapter?.getRemoteDevice(macAddress) ?: run {
            Log.e("SensorBox","Nie znaleziono urządzenia BLE!")
            return
        }
        gatt = device.connectGatt(this,false,gattCallback)
    }

    private fun requestInfo(gatt: BluetoothGatt, cmd:String){
        enqueueWrite(gatt, cmd)
    }

    private fun sendAllRangesAndValues() {
        val g = gatt ?: run {
            Log.w("SensorBox", "Brak połączenia BLE, nie wysyłam zakresów")
            return
        }

        val rCmd = buildString {
            append("r")
            for (i in 0..5) {
                val code = rangeCodes[i][pendingRanges[i]] ?: 1
                append(code)
                currentRanges[i] = pendingRanges[i]
            }
        } + "\n"
        enqueueWrite(g, rCmd)
        Log.d("SensorBox", "Enqueued rCmd: $rCmd")

        for (i in 0..5) {
            for (j in 0..4) {
                val cmd = "w${i+1}${j+1} ${pendingRanges[i]}\n"
                handler.postDelayed({
                    gatt?.let { enqueueWrite(it, cmd) }
                    endValUnitCache[i][j] = pendingRanges[i]
                }, (i*300 + j*80).toLong())
            }
        }
    }

    private val liveReadRunnable = object:Runnable{
        override fun run(){
            if (writeQueue.size < 12) {
                gatt?.let { enqueueWrite(it,"d\n") }
            } else {
                Log.d("SensorBox","liveRead skipped — writeQueue size=${writeQueue.size}")
            }
            handler.postDelayed(this,250)
        }
    }

    private fun startLiveRead(){
        isReadingLive = true
        handler.post(liveReadRunnable)
    }

    private fun stopLiveRead(){
        isReadingLive = false
        handler.removeCallbacks(liveReadRunnable)
    }

    private fun stopBleConnection(){
        stopLiveRead()
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gatt?.close()
            } else {
                Log.w("SensorBox", "Brak uprawnienia BLUETOOTH_CONNECT — pomijam zamknięcie GATT")
            }
        } catch (e: SecurityException) {
            Log.e("SensorBox", "SecurityException przy zamykaniu GATT: ${e.message}")
        }
        gatt = null
        notificationsEnabled = false
        writeQueue.clear()
        isWriting = false
        connectionStatus.value = "Rozłączono"
    }

    fun val2decimal(value: Double, unit:String): String {
        return when(unit){
            "U/m","rpm","psi" -> value.toInt().toString()
            "C","F" -> String.format("%.1f", value)
            else -> when{
                value<10 -> String.format("%.2f",value)
                value<1000 -> String.format("%.1f",value)
                else -> value.toInt().toString()
            }
        }
    }
}
