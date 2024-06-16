package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class TrackRecorderViewModelFactory(private val trackRecorder: TrackRecorder) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(TrackRecorderViewModel::class.java)) {
            return TrackRecorderViewModel(trackRecorder) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
