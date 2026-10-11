package com.posificator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.posificator.platform.Coordinates
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCircle
import platform.MapKit.MKCircleRenderer
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.UIKit.UIColor
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class MapController : NSObject(), MKMapViewDelegateProtocol {
    val mapView = MKMapView()
    var onCenterChange: (Coordinates) -> Unit = {}
    var radiusMeters = 200.0
    var appliedVersion = -1
    private var circle: MKCircle? = null

    init {
        mapView.delegate = this
    }

    fun moveTo(target: Coordinates) {
        val center = CLLocationCoordinate2DMake(target.latitude, target.longitude)
        mapView.setRegion(MKCoordinateRegionMakeWithDistance(center, 1_500.0, 1_500.0), animated = true)
    }

    fun redraw() {
        circle?.let { mapView.removeOverlay(it) }
        val newCircle = MKCircle.circleWithCenterCoordinate(mapView.centerCoordinate, radiusMeters)
        mapView.addOverlay(newCircle)
        circle = newCircle
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        redraw()
        mapView.centerCoordinate.useContents { onCenterChange(Coordinates(latitude, longitude)) }
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        val renderer = MKCircleRenderer(circle = rendererForOverlay as MKCircle)
        renderer.fillColor = UIColor.systemBlueColor.colorWithAlphaComponent(0.2)
        renderer.strokeColor = UIColor.systemBlueColor
        renderer.lineWidth = 2.0
        return renderer
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlaceMap(
    target: Coordinates?,
    targetVersion: Int,
    radiusMeters: Double,
    onCenterChange: (Coordinates) -> Unit,
    modifier: Modifier,
) {
    val controller = remember { MapController() }
    controller.onCenterChange = onCenterChange

    UIKitView(
        factory = { controller.mapView },
        modifier = modifier,
        update = {
            controller.radiusMeters = radiusMeters
            if (target != null && targetVersion != controller.appliedVersion) {
                controller.appliedVersion = targetVersion
                controller.moveTo(target)
                onCenterChange(target)
            }
            controller.redraw()
        },
    )
}
