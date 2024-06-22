package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class ViewModelFactory(private val trackRecorder: DataRepository, private val locationRepository: LocationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(TrackRecorderViewModel::class.java)) {
            return TrackRecorderViewModel(trackRecorder) as T
        }
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(trackRecorder) as T
        }
        if (modelClass.isAssignableFrom(GeodeticPathViewModel::class.java)) {
            return GeodeticPathViewModel(trackRecorder) as T
        }
        if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            return LocationViewModel(locationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
