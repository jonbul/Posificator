package com.posificator.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.posificator.platform.Coordinates

/**
 * A pannable map with the radius drawn around its center. The map center is the chosen point:
 * the user drags the map under a fixed pin, and [onCenterChange] reports where it ends up.
 *
 * The map jumps to [target] whenever [targetVersion] changes.
 * Android uses OpenStreetMap (osmdroid); iOS uses Apple MapKit. Neither needs an API key.
 */
@Composable
expect fun PlaceMap(
    target: Coordinates?,
    targetVersion: Int,
    radiusMeters: Double,
    onCenterChange: (Coordinates) -> Unit,
    modifier: Modifier = Modifier,
)
