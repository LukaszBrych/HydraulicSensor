package com.example.hydraulicsensorapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun SetRangeScreen(navController: NavController) {

    val ranges = remember { mutableStateListOf(1,1,1,1,1,1) }
    val endValUnits = remember { mutableStateListOf(
        mutableStateListOf("100 bar","200 bar","300 bar","400 bar","500 bar"),
        mutableStateListOf("100 bar","200 bar","300 bar","400 bar","500 bar"),
        mutableStateListOf("100 bar","200 bar","300 bar","400 bar","500 bar"),
        mutableStateListOf("100 bar","200 bar","300 bar","400 bar","500 bar"),
        mutableStateListOf("100 bar","200 bar","300 bar","400 bar","500 bar"),
        mutableStateListOf("100 bar","200 bar","300 bar","400 bar","500 bar"),
    ) }

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {

        Text("Set Range Parameters", style = MaterialTheme.typography.titleMedium)

        for (ch in 0..5) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("P${ch+1}", modifier = Modifier.width(50.dp))
                ranges.forEachIndexed { idx, _ ->
                    RadioButton(
                        selected = ranges[ch] == idx+1,
                        onClick = { ranges[ch] = idx+1 }
                    )
                    Text("R${idx+1}")
                }
            }

            Row {
                endValUnits[ch].forEachIndexed { j, evu ->
                    OutlinedTextField(
                        value = evu,
                        onValueChange = { endValUnits[ch][j] = it },
                        label = { Text("R${j+1}") },
                        modifier = Modifier.width(100.dp).padding(end=4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = {
                // BluetoothService.sendSettings(ranges, endValUnits)
            }) { Text("Send") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                // BluetoothService.resetSettings()
            }) { Text("Reset") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }) { Text("Back") }
    }
}
