package ru.karachinstar.diplom.gpstracker

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.esri.arcgisruntime.geometry.SpatialReference
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle



class MyApplication : Application() {
    lateinit var map: ArcGISMap

    override fun onCreate() {
        super.onCreate()

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo

        val spatialReference = SpatialReference.create(3857)

        map = if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
            ArcGISMap(BasemapStyle.ARCGIS_IMAGERY_STANDARD)
        } else {
            ArcGISMap(spatialReference)
        }

    }
}

