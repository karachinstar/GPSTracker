package ru.karachinstar.diplom.gpstracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.geometry.Point

class FragmentMainViewModel : ViewModel() {
    private val _selectedFeature = MutableLiveData<Feature?>()
    val selectedFeature: LiveData<Feature?> = _selectedFeature

    private val _targetPoint = MutableLiveData<Point?>()
    val targetPoint: LiveData<Point?> = _targetPoint

    fun setSelectedFeature(feature: Feature?) {
        _selectedFeature.value = feature
    }
    fun setTargetPoint(point: Point?) {
        _targetPoint.value = point
    }
}