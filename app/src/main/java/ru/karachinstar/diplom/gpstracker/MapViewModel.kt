package ru.karachinstar.diplom.gpstracker

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.layers.Layer


class MapViewModel(private val repository: DataRepository) : ViewModel() {
    val layers: LiveData<List<Layer>> = repository.layers

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
