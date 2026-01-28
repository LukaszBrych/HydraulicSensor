package com.example.hydraulicsensorapp

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Klasa do automatycznego zapisu danych live reading do CSV
 */
class CSVLogger(private val context: Context) {
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var startTime: Long = 0
    private var sampleCount: Int = 0
    
    /**
     * Start zapisu do nowego pliku CSV
     */
    fun startLogging(activeChannels: List<Int>, units: List<String>): Boolean {
        try {
            // Utworz folder LiveReadings
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "HydraulicSensorApp/LiveReadings"
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            // Nazwa pliku z timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            currentFile = File(dir, "LiveReadings_$timestamp.csv")
            
            writer = BufferedWriter(FileWriter(currentFile))
            startTime = System.currentTimeMillis()
            sampleCount = 0
            
            // Nagłówek CSV
            writer?.write("Timestamp;Elapsed_s")
            for (ch in 1..6) {
                if (activeChannels.contains(ch)) {
                    val unit = units.getOrNull(ch - 1) ?: ""
                    writer?.write(";P${ch}_$unit")
                }
            }
            writer?.write("\n")
            writer?.flush()
            
            Log.d("CSVLogger", "Started logging to: ${currentFile?.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e("CSVLogger", "Failed to start logging", e)
            return false
        }
    }
    
    /**
     * Zapisz próbkę danych
     * @param values mapa: channel -> value (np. 1 -> 150.5)
     */
    fun logSample(values: Map<Int, Float>) {
        try {
            val now = System.currentTimeMillis()
            val elapsed = (now - startTime) / 1000.0
            
            // Timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            writer?.write(dateFormat.format(Date(now)))
            writer?.write(";%.3f".format(elapsed))
            
            // Wartości kanałów (w kolejności 1-6)
            for (ch in 1..6) {
                values[ch]?.let { value ->
                    writer?.write(";%.3f".format(value))
                }
            }
            
            writer?.write("\n")
            sampleCount++
            
            // Flush co 10 próbek (żeby nie tracić danych przy crashu)
            if (sampleCount % 10 == 0) {
                writer?.flush()
            }
        } catch (e: Exception) {
            Log.e("CSVLogger", "Failed to log sample", e)
        }
    }
    
    /**
     * Zakończ zapis i zamknij plik
     */
    fun stopLogging() {
        try {
            writer?.flush()
            writer?.close()
            Log.d("CSVLogger", "Stopped logging. Total samples: $sampleCount")
        } catch (e: Exception) {
            Log.e("CSVLogger", "Failed to stop logging", e)
        } finally {
            writer = null
            currentFile = null
        }
    }
    
    /**
     * Czy aktualnie trwa zapis?
     */
    fun isLogging(): Boolean = writer != null
    
    /**
     * Pobierz ścieżkę aktualnego pliku
     */
    fun getCurrentFile(): File? = currentFile
}
