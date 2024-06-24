package ru.karachinstar.diplom.gpstracker

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.layers.Layer
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay


class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DataRepository = DataRepository(application)
    val layers: LiveData<List<Layer>> = repository.layers
    //var mapScale: Double = 72000.0
    var mapCenter: Viewpoint = Viewpoint(52.2750, 104.2605, 72000.0)

    fun loadShapefile(uri: Uri, graphicsOverlay: GraphicsOverlay) {
        repository.loadShapefile(uri, graphicsOverlay)
    }

    fun loadGeoTiff(uri: Uri) {
        repository.loadGeoTiff(uri)
    }

    fun loadKMLfile(uri: Uri) {
        repository.loadKMLfile(uri)
    }

    fun getFilteredAttributesForFeature(feature: Feature): Map<String, Any> {
        return repository.getFilteredAttributes(feature)
    }



    // Ваши другие методы
}
