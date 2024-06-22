package ru.karachinstar.diplom.gpstracker

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.view.MapView

class LocationRepository(private var mapView: MapView) {
    private val locationDisplay = mapView.locationDisplay
    private val _location = MutableLiveData<Point>()
    val location: LiveData<Point> get() = _location

    init {
        locationDisplay.addLocationChangedListener { locationChangedEvent ->
            val location = locationChangedEvent.location.position
            _location.value = Point(location.x, location.y, SpatialReferences.getWgs84())
            Log.d("LocationRepository", "Location updated: $location")
        }
    }

    fun start() {
        if (!locationDisplay.isStarted) {
            locationDisplay.startAsync()
            Log.d("LocationRepository", "LocationDisplay started")
        } else {
            Log.d("LocationRepository", "LocationDisplay already started")
        }
    }

    fun stop() {
        if (locationDisplay.isStarted) {
            locationDisplay.stop()
        }
    }

}

