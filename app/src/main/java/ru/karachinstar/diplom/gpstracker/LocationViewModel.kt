package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.geometry.Point

class LocationViewModel(private val repository: LocationRepository) : ViewModel() {
    val location: LiveData<Point> get() = repository.location
    private val _isActive = MutableLiveData<Boolean>()

    fun start() {
        _isActive.value = true
        repository.start()
    }

    fun stop() {
        _isActive.value = false
        repository.stop()
    }

    override fun onCleared() {
        super.onCleared()
        // Остановите отслеживание местоположения, когда ViewModel уничтожается
        stop()
    }
}
