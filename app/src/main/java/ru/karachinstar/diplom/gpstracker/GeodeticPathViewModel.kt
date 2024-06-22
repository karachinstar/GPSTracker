package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.mapping.view.Graphic

class GeodeticPathViewModel(private val repository: DataRepository) : ViewModel() {
    val distance: LiveData<Double> = repository.distance
    val deviation: LiveData<Double> = repository.deviation
    val graphic: LiveData<Graphic> = repository.graphic
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