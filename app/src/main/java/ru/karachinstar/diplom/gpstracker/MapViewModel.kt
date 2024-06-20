package ru.karachinstar.diplom.gpstracker

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.layers.Layer
import com.esri.arcgisruntime.mapping.Viewpoint


class MapViewModel(private val repository: DataRepository) : ViewModel() {
    val layers: LiveData<List<Layer>> = repository.layers
    //var mapScale: Double = 72000.0
    var mapCenter: Viewpoint = Viewpoint(52.2750, 104.2605, 72000.0)

    fun loadShapefile(uri: Uri) {
        repository.loadShapefile(uri)
    }

    fun loadGeoTiff(uri: Uri) {
        repository.loadGeoTiff(uri)
    }

    fun loadKMLfile(uri: Uri) {
        repository.loadKMLfile(uri)
    }

    // Ваши другие методы
}
