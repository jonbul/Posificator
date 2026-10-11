package com.posificator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.posificator.model.Place
import com.posificator.platform.LocationPlatform
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlaceEditorDialog(
    initial: Place,
    isNew: Boolean,
    platform: LocationPlatform,
    onSave: (Place) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var latitude by remember { mutableStateOf(if (isNew) "" else initial.latitude.toString()) }
    var longitude by remember { mutableStateOf(if (isNew) "" else initial.longitude.toString()) }
    var radius by remember { mutableStateOf(initial.radiusMeters.toFloat()) }
    var locating by remember { mutableStateOf(false) }
    var locateError by remember { mutableStateOf<String?>(null) }

    val lat = latitude.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lon = longitude.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val canSave = name.isNotBlank() && lat != null && lon != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New place" else "Edit place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        isError = latitude.isNotBlank() && lat == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        isError = longitude.isNotBlank() && lon == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    enabled = !locating,
                    onClick = {
                        locating = true
                        locateError = null
                        scope.launch {
                            val here = platform.currentLocation()
                            if (here != null) {
                                latitude = here.latitude.toString()
                                longitude = here.longitude.toString()
                            } else {
                                locateError = "Couldn't get your location. Check that location access is allowed."
                            }
                            locating = false
                        }
                    },
                ) { Text(if (locating) "Locating…" else "Use my current location") }
                locateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Text("Notify me within ${formatDistance(radius.toDouble())}")
                Slider(
                    value = radius,
                    onValueChange = { radius = (it / 10f).roundToInt() * 10f },
                    valueRange = Place.MIN_RADIUS_METERS.toFloat()..Place.MAX_RADIUS_METERS.toFloat(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            latitude = lat!!,
                            longitude = lon!!,
                            radiusMeters = radius.toDouble(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
