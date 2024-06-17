package ru.karachinstar.diplom.gpstracker

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TrackRecorderViewModel(private val repository: DataRepository) : ViewModel() {
    val isRecording: LiveData<Boolean> = repository.isRecording

    fun startStopRecording() {
        repository.toggleRecording()
    }

    fun onLocationChanged(longitude: Double, latitude: Double) {
        if (isRecording.value == true) {
            repository.writeLocation(longitude, latitude)
        }
    }

    // Ваши другие методы
}