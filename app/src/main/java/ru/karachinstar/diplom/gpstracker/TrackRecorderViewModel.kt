package ru.karachinstar.diplom.gpstracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

class TrackRecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DataRepository = DataRepository(application)
    val isRecording: LiveData<Boolean> = repository.isRecording

    fun startStopRecording() {
        repository.toggleRecording()
    }

    fun onLocationChanged(longitude: Double, latitude: Double) {
        if (isRecording.value == true) {
            repository.writeLocation(longitude, latitude)
        }
    }
}