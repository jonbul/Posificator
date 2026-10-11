package com.posificator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.posificator.model.Dates
import com.posificator.model.Place
import com.posificator.model.currentLocalNow
import com.posificator.platform.Coordinates
import com.posificator.platform.LocationPlatform
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class PickerTarget { StartDate, EndDate, StartTime, EndTime }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorScreen(
    initial: Place,
    isNew: Boolean,
    platform: LocationPlatform,
    onSave: (Place) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var center by remember { mutableStateOf(if (isNew) null else Coordinates(initial.latitude, initial.longitude)) }
    var target by remember { mutableStateOf(center) }
    var targetVersion by remember { mutableIntStateOf(0) }
    var radiusText by remember { mutableStateOf(initial.radiusMeters.roundToInt().toString()) }
    // The map keeps the last valid radius while the user is mid-edit.
    var shownRadius by remember { mutableStateOf(initial.radiusMeters) }
    var locating by remember { mutableStateOf(false) }
    var locateError by remember { mutableStateOf<String?>(null) }

    val today = remember { currentLocalNow().date }
    var useDates by remember { mutableStateOf(initial.startDate != null || initial.endDate != null) }
    var startDate by remember { mutableIntStateOf(initial.startDate ?: today) }
    var endDate by remember { mutableIntStateOf(initial.endDate ?: today) }
    var useHours by remember { mutableStateOf(initial.startMinute != null && initial.endMinute != null) }
    var startMinute by remember { mutableIntStateOf(initial.startMinute ?: 8 * 60) }
    var endMinute by remember { mutableIntStateOf(initial.endMinute ?: 20 * 60) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    fun locate() {
        locating = true
        locateError = null
        scope.launch {
            val here = platform.currentLocation()
            if (here != null) {
                target = here
                targetVersion++
            } else {
                locateError = "Couldn't get your location. Drag the map to the place instead."
            }
            locating = false
        }
    }

    PlatformBackHandler(onBack = onCancel)

    // New places start at the user's position.
    LaunchedEffect(Unit) { if (isNew) locate() }

    val radius = radiusText.trim().toDoubleOrNull()?.takeIf { it in Place.MIN_RADIUS_METERS..Place.MAX_RADIUS_METERS }
    val datesValid = !useDates || startDate <= endDate
    val canSave = name.isNotBlank() && center != null && datesValid && radius != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New place" else "Edit place") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val c = center ?: return@TextButton
                            onSave(
                                initial.copy(
                                    name = name.trim(),
                                    latitude = c.latitude,
                                    longitude = c.longitude,
                                    radiusMeters = radius ?: return@TextButton,
                                    startDate = if (useDates) startDate else null,
                                    endDate = if (useDates) endDate else null,
                                    startMinute = if (useHours) startMinute else null,
                                    endMinute = if (useHours) endMinute else null,
                                ),
                            )
                        },
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight
            val portraitMapHeight = minOf(320.dp, maxHeight * 0.45f)
            // The map is rebuilt when the layout flips, so start it where the pin was.
            LaunchedEffect(landscape) {
                center?.let {
                    target = it
                    targetVersion++
                }
            }
            val map: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier.clipToBounds()) {
                    PlaceMap(
                        target = target,
                        targetVersion = targetVersion,
                        radiusMeters = shownRadius,
                        onCenterChange = { center = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    CenterPin(Modifier.align(Alignment.Center))
                }
            }
            val form: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        center?.let { "Drag the map to move the pin · ${formatCoordinate(it.latitude)}, ${formatCoordinate(it.longitude)}" }
                            ?: "Drag the map to put the pin on the place",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(enabled = !locating, onClick = ::locate) {
                        Text(if (locating) "Locating…" else "Go to my current location")
                    }
                    locateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { text ->
                            radiusText = text.filter { it.isDigit() }.take(6)
                            radiusText.toDoubleOrNull()
                                ?.takeIf { it in Place.MIN_RADIUS_METERS..Place.MAX_RADIUS_METERS }
                                ?.let { shownRadius = it }
                        },
                        label = { Text("Radius in meters") },
                        singleLine = true,
                        isError = radius == null,
                        supportingText = {
                            Text(
                                if (radius == null) {
                                    "Enter ${Place.MIN_RADIUS_METERS.roundToInt()} to ${Place.MAX_RADIUS_METERS.roundToInt()} meters"
                                } else {
                                    "Notify me within ${formatDistance(radius)}"
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    HorizontalDivider()
                    ToggleRow("Only between certain dates", useDates) { useDates = it }
                    if (useDates) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { picker = PickerTarget.StartDate }) { Text("From ${Dates.format(startDate)}") }
                            OutlinedButton(onClick = { picker = PickerTarget.EndDate }) { Text("To ${Dates.format(endDate)}") }
                        }
                        if (!datesValid) {
                            Text("The end date is before the start date.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    ToggleRow("Only between certain hours", useHours) { useHours = it }
                    if (useHours) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { picker = PickerTarget.StartTime }) { Text("From ${Dates.formatMinute(startMinute)}") }
                            OutlinedButton(onClick = { picker = PickerTarget.EndTime }) { Text("To ${Dates.formatMinute(endMinute)}") }
                        }
                        if (startMinute > endMinute) {
                            Text("Runs overnight, past midnight.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (!isNew) {
                        HorizontalDivider()
                        TextButton(onClick = onDelete) { Text("Delete place", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    map(Modifier.weight(1f).fillMaxHeight())
                    form(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    map(Modifier.fillMaxWidth().height(portraitMapHeight))
                    form(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    when (val p = picker) {
        PickerTarget.StartDate, PickerTarget.EndDate -> DatePickerPopup(
            initial = if (p == PickerTarget.StartDate) startDate else endDate,
            onPicked = { if (p == PickerTarget.StartDate) startDate = it else endDate = it },
            onDismiss = { picker = null },
        )
        PickerTarget.StartTime, PickerTarget.EndTime -> TimePickerPopup(
            initialMinute = if (p == PickerTarget.StartTime) startMinute else endMinute,
            onPicked = { if (p == PickerTarget.StartTime) startMinute = it else endMinute = it },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CenterPin(modifier: Modifier) {
    Canvas(modifier.size(24.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.White, radius = size.minDimension / 2)
        drawCircle(Color(0xFFD32F2F), radius = size.minDimension / 2 - 3.dp.toPx())
        drawCircle(Color.White, radius = 3.dp.toPx(), center = c)
        drawCircle(Color(0xFF7F0000), radius = size.minDimension / 2, style = Stroke(1.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerPopup(initial: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toUtcMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(Dates.fromUtcMillis(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerPopup(initialMinute: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPicked(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
