package ru.karachinstar.diplom.gpstracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.mapping.view.Graphic

class GeodeticPathViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DataRepository =DataRepository(application)
    val distance: LiveData<Double> = repository.distance
    val deviation: LiveData<Double> = repository.deviation
    val graphic: LiveData<Graphic> = repository.graphic
    var selectedFeature: Feature? = null
    var targetPoint: Point? = null
    var polyline: Polyline? = null
    fun drawLineAndTrackDistance(currentPoint: Point, targetPoint: Point): Graphic {
        val graphic = repository.drawLineAndTrackDistance(currentPoint, targetPoint)

        return graphic
    }

    fun calculateDistance(currentPoint: Point, targetPoint: Point) {
        repository.calculateDistance(currentPoint, targetPoint)
    }

    fun calculateDeviation(currentPoint: Point, polyline: Polyline) {
        repository.calculateDeviation(currentPoint, polyline)
    }
}