package com.posificator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.posificator.data.PlaceRepository
import com.posificator.model.Dates
import com.posificator.model.Place
import com.posificator.model.currentLocalNow
import com.posificator.model.isExpiredAt
import com.posificator.platform.LocationAccess
import com.posificator.platform.LocationPlatform
import com.posificator.platform.PermissionStatus
import kotlinx.coroutines.flow.combine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(repository: PlaceRepository, platform: LocationPlatform) {
    val places by repository.places.collectAsState()
    val permissions by platform.permissions.collectAsState()
    var editing by remember { mutableStateOf<Place?>(null) }

    // Keep the OS geofences in step with the saved places and permission changes.
    LaunchedEffect(repository, platform) {
        combine(repository.places, platform.permissions) { all, _ ->
            val now = currentLocalNow()
            all.filter { it.enabled && !it.isExpiredAt(now) }
        }
            .collect { platform.syncGeofences(it) }
    }

    MaterialTheme {
        if (editing == null) Scaffold(
            topBar = { TopAppBar(title = { Text("Posificator") }) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = Place(id = PlaceRepository.newId(), name = "", latitude = 0.0, longitude = 0.0)
                    },
                ) { Text("+ Add place") }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!permissions.readyForGeofencing) {
                    item { PermissionCard(permissions, onGrant = platform::requestPermissions) }
                }
                val enabledCount = places.count { it.enabled }
                if (enabledCount > platform.maxGeofences) {
                    item {
                        Text(
                            "Only the first ${platform.maxGeofences} enabled places can be watched on this device.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (places.isEmpty()) {
                    item {
                        Text(
                            "No places yet. Add one and choose how close you need to be before you get a notification.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(places, key = { it.id }) { place ->
                    PlaceRow(
                        place = place,
                        onClick = { editing = place },
                        onToggle = { repository.upsert(place.copy(enabled = it)) },
                    )
                }
            }
        }

        editing?.let { place ->
            PlaceEditorScreen(
                initial = place,
                isNew = repository.find(place.id) == null,
                platform = platform,
                onSave = {
                    repository.upsert(it)
                    editing = null
                },
                onDelete = {
                    repository.delete(place.id)
                    editing = null
                },
                onCancel = { editing = null },
            )
        }
    }
}

@Composable
private fun PermissionCard(status: PermissionStatus, onGrant: () -> Unit) {
    val message = when {
        status.location == LocationAccess.Denied ->
            "Location access is off. Turn it on and choose \"Allow all the time\" so arrivals can be detected."
        status.location != LocationAccess.Always ->
            "Posificator needs location access \"all the time\" to notify you when you arrive, even when the app is closed."
        else -> "Allow notifications so Posificator can tell you when you arrive."
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant) { Text("Grant access") }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Within ${formatDistance(place.radiusMeters)} of ${formatCoordinate(place.latitude)}, ${formatCoordinate(place.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                scheduleSummary(place)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = place.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun scheduleSummary(place: Place): String? {
    val parts = buildList {
        if (place.startMinute != null && place.endMinute != null) {
            add("${Dates.formatMinute(place.startMinute)}–${Dates.formatMinute(place.endMinute)}")
        }
        if (place.startDate != null && place.endDate != null) {
            add("${Dates.format(place.startDate)} to ${Dates.format(place.endDate)}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
