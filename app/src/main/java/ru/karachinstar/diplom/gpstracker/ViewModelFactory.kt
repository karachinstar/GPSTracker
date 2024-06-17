package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class ViewModelFactory(private val trackRecorder: DataRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(TrackRecorderViewModel::class.java)) {
            return TrackRecorderViewModel(trackRecorder) as T
        }
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(trackRecorder) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
