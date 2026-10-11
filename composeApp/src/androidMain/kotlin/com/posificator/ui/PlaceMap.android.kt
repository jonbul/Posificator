package com.posificator.ui

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.posificator.platform.Coordinates
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

private class MapState(val view: MapView, val circle: Polygon) {
    var appliedVersion = -1
}

@Composable
actual fun PlaceMap(
    target: Coordinates?,
    targetVersion: Int,
    radiusMeters: Double,
    onCenterChange: (Coordinates) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val currentOnCenterChange by rememberUpdatedState(onCenterChange)
    val currentRadius by rememberUpdatedState(radiusMeters)

    val state = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", 0))
            // OpenStreetMap's tile policy asks every app to identify itself.
            userAgentValue = context.packageName
        }
        val circle = Polygon().apply {
            fillPaint.color = Color.argb(48, 33, 150, 243)
            outlinePaint.color = Color.rgb(33, 150, 243)
            outlinePaint.strokeWidth = 4f
        }
        val view = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            overlays.add(circle)
        }
        MapState(view, circle)
    }

    fun redraw() {
        val center = state.view.mapCenter
        state.circle.points = Polygon.pointsAsCircle(GeoPoint(center.latitude, center.longitude), currentRadius)
        state.view.invalidate()
    }

    DisposableEffect(state) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                redraw()
                val center = state.view.mapCenter
                currentOnCenterChange(Coordinates(center.latitude, center.longitude))
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                redraw()
                return false
            }
        }
        state.view.addMapListener(listener)
        state.view.onResume()
        onDispose {
            state.view.removeMapListener(listener)
            state.view.onPause()
            state.view.onDetach()
        }
    }

    AndroidView(
        factory = { state.view },
        modifier = modifier,
        update = { view ->
            if (target != null && targetVersion != state.appliedVersion) {
                state.appliedVersion = targetVersion
                view.controller.setCenter(GeoPoint(target.latitude, target.longitude))
                currentOnCenterChange(target)
            }
            redraw()
        },
    )
}
