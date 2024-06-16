package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TrackRecorderViewModel(private val trackRecorder: TrackRecorder) : ViewModel() {

    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> get() = _isRecording

    init {
        _isRecording.value = false
    }

    fun toggleRecording() {
        _isRecording.value = !_isRecording.value!!
        if (_isRecording.value!!) {
            trackRecorder.startRecording()
        } else {
            trackRecorder.finishRecording()
        }
    }

    fun writeLocation(longitude: Double, latitude: Double) {
        if (_isRecording.value!!) {
            trackRecorder.writeLocation(longitude, latitude)
        }
    }
}