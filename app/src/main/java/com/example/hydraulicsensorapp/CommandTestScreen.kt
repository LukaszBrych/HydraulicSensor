package com.example.hydraulicsensorapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Ekran testowy do wysyłania komend do SensorBox MC6600
 * Umożliwia ręczne testowanie wszystkich komend z instrukcji
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandTestScreen(
    activity: MainActivity,
    onBack: () -> Unit
) {
    var customCommand by remember { mutableStateOf("") }
    var lpmChannel by remember { mutableStateOf("5") }
    var lpmRange by remember { mutableStateOf("1") }
    var lpmParams by remember { mutableStateOf("2600.00 25.0 1200.0 12.00 160.0 1.50") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test komend SensorBox") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Powrót")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Komendy pomiarowe",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { activity.queryReadableMeasurement() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("z - Pomiar czytelny")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Konfiguracja zakresów",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { activity.queryCurrentRanges() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("p - Aktualne zakresy")
                }

                Button(
                    onClick = { activity.queryEndValues() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("e - Wartości końcowe")
                }
            }

            Button(
                onClick = { activity.queryBulkAll() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ba - Lista wartości/jednostek")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Konfiguracja LPM (Q1, Q2)",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { activity.queryLpmConfig() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("g - Lista wartości LPM")
            }

            Text(
                "Ustaw wartości LPM:",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = lpmChannel,
                    onValueChange = { lpmChannel = it },
                    label = { Text("Kanał (5-6)") },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = lpmRange,
                    onValueChange = { lpmRange = it },
                    label = { Text("Zakres (1-5)") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = lpmParams,
                onValueChange = { lpmParams = it },
                label = { Text("Parametry (6 wartości)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    val ch = lpmChannel.toIntOrNull() ?: return@Button
                    val rng = lpmRange.toIntOrNull() ?: return@Button
                    val params = lpmParams.split(" ")
                        .mapNotNull { it.toFloatOrNull() }
                    
                    if (params.size == 6) {
                        activity.setLpmConfig(ch, rng, params)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("K - Wyślij konfigurację LPM")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Kalibracja",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "UWAGA: Kalibracja 'ka' wymaga 20mA na P2, pozostałe kanały wolne!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { activity.calibrateCurrentChannels() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("ka - Kalibruj")
                }

                Button(
                    onClick = { activity.queryCalibrationValues() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("kr - Wartości")
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "System",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { activity.queryBatteryVoltage() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("v - Akumulator")
                }

                Button(
                    onClick = { activity.queryVersion() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("h - Wersja")
                }
            }

            Button(
                onClick = { activity.restoreFactorySettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("q + we - RESET FABRYCZNY")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Własna komenda",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                label = { Text("Wpisz komendę") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (customCommand.isNotBlank()) {
                        activity.sendCustomCommand(customCommand)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wyślij własną komendę")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Odpowiedzi są logowane w Logcat z tagiem 'SensorBox'",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

