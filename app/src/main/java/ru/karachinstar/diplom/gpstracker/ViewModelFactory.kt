package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class ViewModelFactory(private val application: MyApplication) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(TrackRecorderViewModel::class.java)) {
            return TrackRecorderViewModel(application) as T
        }
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(application) as T
        }
        if (modelClass.isAssignableFrom(GeodeticPathViewModel::class.java)) {
            return GeodeticPathViewModel(application) as T
        }
        if (modelClass.isAssignableFrom(PermissionsViewModel::class.java)) {
            return PermissionsViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
//