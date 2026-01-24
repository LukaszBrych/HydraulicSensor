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

/**
 * MainActivity - Główna aktywność aplikacji HydraulicSensorApp
 * 
 * Komunikacja z SensorBox MC6600 przez BLE (Bluetooth Low Energy)
 * MAC: 3C:A5:09:7A:D3:23
 * Service UUID: 0000ffe0-0000-1000-8000-00805f9b34fb
 * Characteristic UUID: 0000ffe1-0000-1000-8000-00805f9b34fb
 * 
 * DOSTĘPNE KOMENDY (zgodnie z instrukcją MC 6600):
 * 
 * Pomiary:
 * - 'd' - Live data stream (250ms), format: #val1#val2#val3#val4#val5#val6#
 * - 'z' - Pojedynczy pomiar w formacie czytelnym z jednostkami
 * 
 * Konfiguracja zakresów:
 * - 'p' - Zapytanie o aktualne zakresy, odpowiedź: np. "555212"
 * - 'r######' - Ustawienie zakresów, np. r433222 (6 cyfr dla P1-P6, wartości 1-5)
 * - 'w{CH}{RN} {value} {unit}' - Ustawienie wartości końcowej zakresu
 *   Przykład: w23 250 bar (kanał 2, zakres 3, wartość 250 bar)
 * 
 * Odczyt wartości końcowych:
 * - 'e' - Wyprowadzenie wartości końcowych, format: #100.00#200.00#...#
 * - 'ba' - Lista wszystkich wartości końcowych zakresów/jednostek
 * 
 * Konfiguracja LPM (tylko Q1=kanał 5, Q2=kanał 6):
 * - 'g' - Zapytanie o wartości LPM dla wszystkich zakresów
 * - 'K{CH}{RN} {params}' - Ustawienie wartości LPM
 *   Przykład: K51 2600.00 25.0 1200.0 12.00 160.0 1.50
 *   (6 parametrów krzywej kalibracyjnej turbiny)
 * 
 * Kalibracja:
 * - 'ka' - Kalibracja kanałów prądowych (20mA na P2, pozostałe wolne)
 * - 'kr' - Wyświetlenie wartości kalibracji i % odchylenia
 * 
 * System:
 * - 'v' - Napięcie akumulatora (w voltach)
 * - 'h' - Numer wersji firmware
 * - 'we' - Reset do ustawień fabrycznych (poprzedzić komendą 'q')
 * 
 * ZAKRESY (R1-R5) = TYPY CZUJNIKÓW, nie skalowanie!
 * P1, P2: Sensory ciśnienia 10, 60, 100, 250, 600 bar
 * P3: R1=różnicowy P1-P2, R2-5 jak P1/P2
 * T: Sensory temperatury 125°C, 500°C, 200°C, custom
 * Q1: Typy turbin 1-60, 1-100, 1-250, 1-600 lpm + custom
 * Q2: R1-2=przepływ, R3=moc hydrauliczna P1×Q1/600, R4-5=RPM
 */
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
    
    // Przechowuje aktualne wartości końcowe (W) odczytane z SensorBox komendą 'e'
    private val endValues = mutableStateListOf("10.00", "10.00", "10.00", "125.00", "150.00", "394.00")
    
    // Oryginalne jednostki z SensorBox (nigdy się nie zmieniają hardware'owo)
    private val originalUnits = mutableStateListOf("bar", "bar", "bar", "C", "lpm", "lpm")
    
    // Jednostki wyświetlane w UI (mogą być zmieniane przez użytkownika)
    private val displayUnits = mutableStateListOf("bar", "bar", "bar", "C", "lpm", "lpm")

    private val connectionStatus = mutableStateOf("Rozłączono")
    private val isReadingLive = mutableStateOf(false)

    private val charUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val serviceUuid = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val sensorMac = "3C:A5:09:7A:D3:23"

    // Zakresy R1-R5 = TYPY CZUJNIKÓW (nie "mało-dużo")
    private val rangeCodes = listOf(
        // P1 - Pressure sensor (4-20mA)
        mapOf("10 bar" to 1,"60 bar" to 2,"100 bar" to 3,"250 bar" to 4,"600 bar" to 5),
        // P2 - Pressure sensor (4-20mA)
        mapOf("10 bar" to 1,"60 bar" to 2,"100 bar" to 3,"250 bar" to 4,"600 bar" to 5),
        // P3 - Differential/Pressure (R1 = P1-P2)
        mapOf("P1-P2" to 1,"60 bar" to 2,"100 bar" to 3,"250 bar" to 4,"600 bar" to 5),
        // T - Temperature sensor
        mapOf("125°C" to 1,"500°C" to 2,"200°C" to 3,"Custom" to 4,"Custom" to 5),
        // Q1 - Flow (turbine types)
        mapOf("1-60 lpm" to 1,"1-100 lpm" to 2,"1-250 lpm" to 3,"1-600 lpm" to 4,"Custom" to 5),
        // Q2 - Multi-function (R1-2:flow, R3:power, R4-5:RPM)
        mapOf("Flow R1" to 1,"Flow R2" to 2,"P1×Q1" to 3,"RPM-1" to 4,"RPM-2" to 5)
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
                    Log.w("SensorBox","Descriptor nie znaleziony — wznawiamy bez potwierdzenia")
                    notificationsEnabled = true
                    notificationSet = true
                    // Usunięto automatyczne startLiveRead() - użytkownik musi kliknąć przycisk
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
                    
                    // Po połączeniu odpytaj o aktualna konfiguracje
                    handler.postDelayed({ queryCurrentRanges() }, 100)
                    handler.postDelayed({ queryBulkAll() }, 300)
                    handler.postDelayed({ queryBatteryVoltage() }, 500)
                    
                    // Usunięto automatyczne startowanie pomiarów
                    // Użytkownik musi kliknąć "Rozpocznij pomiary"
                    handler.postDelayed({ 
                        sendAllRangesAndValues()
                    }, 700)
                    
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
            Log.d("SensorBox", "Odebrano RAW: $text (buffer: ${buffer.length} znaków)")

            // NAJPIERW sprawdź odpowiedzi komend 'e', 'p', etc. (PRZED ramkami pomiarowymi!)
            if (buffer.contains("#") && buffer.count { it == '#' } >= 7) {
                // Może to odpowiedź 'e' - szukaj kompletnej ramki #val1#val2#...#val6#
                val eMatch = Regex("#([0-9.]+)#([0-9.]+)#([0-9.]+)#([0-9.]+)#([0-9.]+)#([0-9.]+)#").find(buffer)
                if (eMatch != null) {
                    val fullMatch = eMatch.value
                    Log.d("SensorBox", "✅ Odpowiedź 'e': $fullMatch")
                    
                    val values = eMatch.groupValues.drop(1)  // Pomin fullMatch, wez tylko grupy
                    handler.post {
                        for (i in 0 until 6) {
                            endValues[i] = values[i].trim()
                            
                            val unit = when(i) {
                                0, 1, 2 -> "bar"  // P1, P2, P3
                                3 -> "C"          // P4
                                4, 5 -> "lpm"     // P5, P6
                                else -> "bar"
                            }
                            
                            // Zapisz oryginalną jednostkę z SensorBox
                            originalUnits[i] = unit
                            // Domyślnie displayUnit = originalUnit
                            if (displayUnits[i] == "bar" || displayUnits[i] == "C" || displayUnits[i] == "lpm") {
                                displayUnits[i] = unit
                            }
                            
                            ranges[i] = "${endValues[i]} $unit"
                            pendingRanges[i] = "${endValues[i]} $unit"
                            Log.d("SensorBox", "✅ P${i+1} = ${endValues[i]} $unit (original)")
                        }
                    }
                    
                    // Wyczyść odpowiedź 'e' z bufora
                    buffer = buffer.replace(fullMatch, "")
                }
            }
            
            // Komenda 'p' - 6 cyfr
            val pMatch = Regex("[1-5]{6}").find(buffer)
            if (pMatch != null) {
                if (parseCommandResponse(pMatch.value)) {
                    buffer = buffer.replace(pMatch.value, "")
                }
            }

            // Parsowanie ramek z danymi pomiarowymi (#val1#val2#...#val6#)
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

            // Inne odpowiedzi (g, z, etc.)
            if (buffer.isNotEmpty()) {
                if (parseCommandResponse(buffer)) {
                    buffer = ""  // Wyczyść tylko jeśli parseCommandResponse zwrócił true
                }
            }

            // Ogranicz wielkość bufora
            if (buffer.length > 200) {
                Log.d("SensorBox", "Buffer overflow, czyszczę bufor (było ${buffer.length} znaków)")
                buffer = ""  // Wyczyść całkowicie zamiast zachowywać śmieci
            }
        }

        // Parsowanie odpowiedzi na komendy
        private fun parseCommandResponse(response: String): Boolean {
            Log.d("SensorBox", "parseCommandResponse: '$response'")
            
            // Komenda 'p' - odpowiedź: np. "555212" (6 cyfr 1-5)
            if (response.matches(Regex("^[1-5]{6}\\s*$"))) {
                Log.d("SensorBox", "✅ Odpowiedź 'p': Aktualne zakresy = $response")
                val rangesList = response.trim().map { it.toString().toInt() }
                handler.post {
                    for (i in rangesList.indices) {
                        val rangeIndex = rangesList[i] - 1  // R1=0, R2=1, etc.
                        Log.d("SensorBox", "P${i+1} = R${rangesList[i]}")
                        
                        // Zaktualizuj ranges z aktualnym zakresem z SensorBox
                        // Pobieramy wartość końcową z endValues jeśli już pobrana
                        if (endValues[i] != "---") {
                            // Musimy odgadnąć jednostkę na podstawie typu czujnika
                            val unit = when(i) {
                                0, 1, 2 -> "bar"  // P1, P2, P3 - ciśnienie
                                3 -> "C"          // P4 - temperatura
                                4, 5 -> "lpm"     // P5, P6 - przepływ
                                else -> "bar"
                            }
                            ranges[i] = "${endValues[i]} $unit"
                            pendingRanges[i] = "${endValues[i]} $unit"
                            Log.d("SensorBox", "Zaktualizowano P${i+1}: ${ranges[i]}")
                        }
                    }
                }
                return true
            }

            // Komenda 'g' - odpowiedź: konfiguracja LPM (K51-K62)
            // Format: wieloliniowa odpowiedź z K51, K52, etc.
            if (response.contains("K5") || response.contains("K6")) {
                Log.d("SensorBox", "✅ 📊 LPM Config: $response")
                // Podziel na linie jeśli są
                val lines = response.split("\n", "\r\n", "\r").filter { it.isNotBlank() }
                lines.forEach { line ->
                    if (line.startsWith("K5") || line.startsWith("K6")) {
                        Log.d("SensorBox", "   $line")
                    }
                }
                return true
            }

            // Komenda 'e' - parsowana wcześniej w onCharacteristicChanged

            // Komenda 'ba' - NIE używamy, bo odpowiedź przychodzi w pakietach BLE
            // Używamy zamiast tego 'e' + hardcoded units
            // if (response.contains("bar") || ...

            // Komenda 'z' - odpowiedź: pomiar w formacie czytelnym
            if (response.contains("lpm") || response.contains("gpm")) {
                Log.d("SensorBox", "✅ Odpowiedź 'z': Pomiar czytelny = $response")
                return true
            }

            // Komenda 'v' - odpowiedź zawiera napięcie w V
            if (response.contains("V") && response.matches(Regex(".*\\d+\\.\\d+.*V.*"))) {
                Log.d("SensorBox", "✅ Odpowiedź 'v': Napięcie akumulatora = $response")
                return true
            }

            // Komenda 'h' - odpowiedź: numer wersji
            if (response.contains("Version")) {
                Log.d("SensorBox", "✅ Odpowiedź 'h': Wersja = $response")
                return true
            }

            // Komenda 'kr' - odpowiedź: wartości kalibracji
            if (response.contains("Calibration") || response.contains("%")) {
                Log.d("SensorBox", "✅ Odpowiedź 'kr': Kalibracja = $response")
                return true
            }

            // Nie rozpoznano jako odpowiedź na komendę
            return false
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
            var currentScreen by remember { mutableStateOf("main") }
            
            HydraulicSensorAppTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "main" -> OfflineRecordingScreen(
                            channelValues = channelValues,
                            ranges = ranges,
                            connectionStatus = connectionStatus.value,
                            isReadingLive = isReadingLive.value,
                            endValues = endValues,
                            originalUnits = originalUnits,
                            displayUnits = displayUnits,
                            onQueryEndValues = { queryEndValues() },
                            onRangeChange = { index, range ->
                                // ✅ poprawione zachowanie zakresów
                                ranges[index] = range
                                pendingRanges[index] = range

                                if (isReadingLive.value) stopLiveRead()

                                // NIE wysyłamy sendAllRangesAndValues() tutaj!
                                // onSendRangeSettings już wysyła poprawne wartości z konwersją
                                Log.d("SensorBox","Range zaktualizowany lokalnie: P${index+1} = $range")
                            },
                            onSendRangeSettings = { channelIndex, activeRangeIndex, allRanges, selectedUnit ->
                                // Zapisz wybraną jednostkę jako displayUnit
                                displayUnits[channelIndex] = selectedUnit
                                // Nowa funkcja wysyłania zakresów z dialogu
                                sendRangeSettingsToSensorBox(channelIndex, activeRangeIndex, allRanges)
                            },
                            onConnect = { connectToDevice() },
                            onDisconnect = { disconnectFromDevice() },
                            onStartMeasurement = { startLiveRead() },
                            onStopMeasurement = { stopLiveRead() },
                            onBack = { finish() },
                            onCommandTest = { currentScreen = "commands" }
                        )
                        
                        "commands" -> CommandTestScreen(
                            activity = this@MainActivity,
                            onBack = { currentScreen = "main" }
                        )
                    }
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

    private fun connectToDevice() {
        if (bluetoothAdapter==null){
            Log.e("SensorBox","BluetoothAdapter null!")
            return
        }
        connectToDeviceByMac(sensorMac)
    }
    
    private fun disconnectFromDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        // Zatrzymaj live reading jeśli aktywny
        if (isReadingLive.value) {
            stopLiveRead()
        }
        
        // Wyczyść kolejkę zapisu
        writeQueue.clear()
        isWriting = false
        
        // Rozłącz BLE
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        
        // Zresetuj flagi - ważne dla ponownego połączenia!
        notificationsEnabled = false
        notificationSet = false
        
        // Zresetuj wyświetlane wartości
        for (i in 0 until 6) {
            channelValues[i] = "P${i+1}: ---"
        }
        
        connectionStatus.value = "Rozłączono"
        Log.d("SensorBox", "Rozłączono z urządzeniem BLE")
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

    // Nowa funkcja: Wysyłanie zakresów z dialogu RangeSettings
    // Konwersja jednostek
    private fun convertValue(value: Float, fromUnit: String, toUnit: String): Float {
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
    
    private fun sendRangeSettingsToSensorBox(
        channelIndex: Int,  // 0-5 (P1-P6)
        activeRangeIndex: Int,  // 0-4 (R1-R5) - który zakres jest aktywny
        allRanges: List<String>  // Wszystkie 5 wartości np. ["60 bar", "250 bar", ...]
    ) {
        Log.d("SensorBox", "Wysyłanie zakresów dla P${channelIndex+1}, aktywny: R${activeRangeIndex+1}")
        
        gatt?.let { g ->
            if (!notificationsEnabled) {
                Log.w("SensorBox", "BLE nie połączony, pomijam wysyłanie")
                return
            }

            // Zatrzymaj live read
            if (isReadingLive.value) stopLiveRead()

            // Krok 1: Wyślij komendę 'r' (ustawienie aktywnego zakresu dla tego kanału)
            // Format: r{P1range}{P2range}{P3range}{P4range}{P5range}{P6range}
            // Musimy zachować aktualne zakresy innych kanałów
            val currentActiveRanges = MutableList(6) { idx ->
                if (idx == channelIndex) {
                    activeRangeIndex + 1  // R1=1, R2=2, etc.
                } else {
                    // Zachowaj aktualny zakres z currentRanges
                    1  // Domyślnie R1, możesz to ulepszyć
                }
            }
            
            val rCmd = "r${currentActiveRanges.joinToString("")}\n"
            Log.d("SensorBox", "Wysyłam: $rCmd")
            enqueueWrite(g, rCmd)

            // Krok 2: Wyślij komendy 'w' dla wszystkich 5 zakresów tego kanału
            // Format: w{channel}{range} {value} {unit}
            //WAŻNE: Zawsze wysyłaj w oryginalnej jednostce z SensorBox!
            allRanges.forEachIndexed { rangeIdx, rangeValue ->
                Log.d("SensorBox", "DEBUG: rangeValue='$rangeValue' channelIndex=$channelIndex")
                val parts = rangeValue.split(" ")
                Log.d("SensorBox", "DEBUG: parts.size=${parts.size} parts=$parts")
                if (parts.size >= 2) {
                    val displayValue = parts[0].toFloatOrNull() ?: 0f
                    val displayUnit = parts[1]
                    val originalUnit = originalUnits[channelIndex]
                    
                    Log.d("SensorBox", "DEBUG: displayValue=$displayValue displayUnit=$displayUnit originalUnit=$originalUnit")
                    
                    // Konwertuj z display unit na original unit
                    val originalValue = convertValue(displayValue, displayUnit, originalUnit)
                    val cmd = "w${channelIndex+1}${rangeIdx+1} ${originalValue.toInt()} $originalUnit\n"
                    Log.d("SensorBox", "Konwersja: $rangeValue → ${originalValue.toInt()} $originalUnit")
                    Log.d("SensorBox", "Wysyłam: $cmd")
                    
                    handler.postDelayed({
                        enqueueWrite(g, cmd)
                        endValUnitCache[channelIndex][rangeIdx] = "${originalValue.toInt()} $originalUnit"
                    }, (rangeIdx * 100).toLong())
                } else {
                    Log.w("SensorBox", "BŁĄD: rangeValue nie ma jednostki: '$rangeValue'")
                }
            }

            // Krok 3: Zaktualizuj lokalny stan
            ranges[channelIndex] = allRanges[activeRangeIndex]
            pendingRanges[channelIndex] = allRanges[activeRangeIndex]

            // NIE wznawiamy automatycznie pomiarów - użytkownik musi kliknąć "Start"
            Log.d("SensorBox", "Ustawienia zapisane. Kliknij 'Start' aby rozpocząć pomiary.")
        }
    }

    // === DODATKOWE KOMENDY Z INSTRUKCJI ===
    
    // Zapytanie o ustawione zakresy (komenda 'p')
    // Odpowiedź: np. "555212" oznacza P1=R5, P2=R5, P3=R5, P4=R2, P5=R1, P6=R2
    fun queryCurrentRanges() {
        gatt?.let { g ->
            enqueueWrite(g, "p\n")
            Log.d("SensorBox", "Wysłano komendę: p (zapytanie o zakresy)")
        }
    }

    // Zapytanie o wartości końcowe zakresów (komenda 'e')
    // Odpowiedź: #100.00#200.00#300.00#500.00#2600.00#6000.00#
    fun queryEndValues() {
        gatt?.let { g ->
            enqueueWrite(g, "e\n")
            Log.d("SensorBox", "Wysłano komendę: e (zapytanie o wartości końcowe)")
        }
    }

    // Zapytanie o wszystkie wartości końcowe i jednostki (komenda 'ba')
    fun queryBulkAll() {
        gatt?.let { g ->
            enqueueWrite(g, "ba\n")
            Log.d("SensorBox", "Wysłano komendę: ba (lista wartości końcowych/jednostek)")
        }
    }

    // Zapytanie o napięcie akumulatora (komenda 'v')
    fun queryBatteryVoltage() {
        gatt?.let { g ->
            enqueueWrite(g, "v\n")
            Log.d("SensorBox", "Wysłano komendę: v (napięcie akumulatora)")
        }
    }

    // Zapytanie o wersję firmware (komenda 'h')
    fun queryVersion() {
        gatt?.let { g ->
            enqueueWrite(g, "h\n")
            Log.d("SensorBox", "Wysłano komendę: h (numer wersji)")
        }
    }

    // Zapytanie o konfigurację LPM (komenda 'g')
    // Zapytanie o konfigurację LPM (komenda 'g')
    fun queryLpmConfig() {
        gatt?.let { g ->
            if (!notificationsEnabled) {
                Log.w("SensorBox", "BLE nie połączony, pomijam wysyłanie 'g'")
                return
            }
            
            // Zatrzymaj live read aby nie było kolizji
            val wasReading = isReadingLive.value
            if (wasReading) stopLiveRead()
            
            enqueueWrite(g, "g\n")
            Log.d("SensorBox", "🔍 Wysłano komendę: g (zapytanie o wartości LPM)")
            
            // Wznów live read po chwili
            handler.postDelayed({
                if (wasReading && writeQueue.isEmpty()) {
                    startLiveRead()
                }
            }, 1500)
        } ?: run {
            Log.w("SensorBox", "GATT jest null, nie można wysłać komendy 'g'")
        }
    }

    // Ustawienie wartości LPM (komenda 'K')
    // Przykład: K51 2600.00 25.0 1200.0 12.00 160.0 1.50
    // Kanał 5 lub 6, zakresy 1-5 (dla P5) lub 1-2 (dla P6)
    fun setLpmConfig(channel: Int, range: Int, params: List<Float>) {
        if (channel !in 5..6) {
            Log.w("SensorBox", "Kanał LPM musi być 5 lub 6")
            return
        }
        if ((channel == 5 && range !in 1..5) || (channel == 6 && range !in 1..2)) {
            Log.w("SensorBox", "Nieprawidłowy zakres dla kanału $channel")
            return
        }
        
        gatt?.let { g ->
            val paramsStr = params.joinToString(" ") { "%.2f".format(it) }
            val cmd = "K$channel$range $paramsStr\n"
            enqueueWrite(g, cmd)
            Log.d("SensorBox", "Wysłano komendę: $cmd (konfiguracja LPM)")
        }
    }

    // Przywrócenie ustawień fabrycznych (komenda 'q' + 'we')
    fun restoreFactorySettings() {
        gatt?.let { g ->
            enqueueWrite(g, "q\n")
            handler.postDelayed({
                enqueueWrite(g, "we\n")
                Log.d("SensorBox", "Wysłano komendę: q + we (reset do ustawień fabrycznych)")
            }, 100)
        }
    }

    // Kalibracja kanałów prądowych (komenda 'ka')
    // UWAGA: Wszystkie kanały oprócz P2 powinny być wolne, 20mA na P2
    fun calibrateCurrentChannels() {
        gatt?.let { g ->
            // Najpierw ustaw zakresy: r555111 (P1-P3=600bar, T=125°C)
            enqueueWrite(g, "r555111\n")
            handler.postDelayed({
                enqueueWrite(g, "ka\n")
                Log.d("SensorBox", "Wysłano komendę: ka (kalibracja kanałów prądowych)")
            }, 200)
        }
    }

    // Wyświetlenie wartości kalibracji (komenda 'kr')
    fun queryCalibrationValues() {
        gatt?.let { g ->
            enqueueWrite(g, "kr\n")
            Log.d("SensorBox", "Wysłano komendę: kr (wartości kalibracji)")
        }
    }

    // Pomiar w formacie czytelnym (komenda 'z')
    // Wyświetla wszystkie 6 kanałów w ustawionej jednostce
    fun queryReadableMeasurement() {
        gatt?.let { g ->
            enqueueWrite(g, "z\n")
            Log.d("SensorBox", "Wysłano komendę: z (pomiar czytelny)")
        }
    }

    // Wysłanie własnej komendy (do testów)
    fun sendCustomCommand(command: String) {
        gatt?.let { g ->
            val cmd = if (command.endsWith("\n")) command else "$command\n"
            enqueueWrite(g, cmd)
            Log.d("SensorBox", "Wysłano własną komendę: $command")
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
        isReadingLive.value = true
        handler.post(liveReadRunnable)
    }

    private fun stopLiveRead(){
        isReadingLive.value = false
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
