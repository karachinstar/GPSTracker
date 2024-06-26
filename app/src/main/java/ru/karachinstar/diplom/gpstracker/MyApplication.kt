package ru.karachinstar.diplom.gpstracker

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.esri.arcgisruntime.geometry.SpatialReference
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import leakcanary.AppWatcher
import leakcanary.LeakCanary


class MyApplication : Application() {
    lateinit var map: ArcGISMap

    override fun onCreate() {
        super.onCreate()

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo

        val spatialReference = SpatialReference.create(28418)

        map = if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
            ArcGISMap(BasemapStyle.OSM_STANDARD)
        } else {
            ArcGISMap(spatialReference)
        }

       // AppWatcher.config = AppWatcher.config.copy(watchActivities = true, watchFragments = true)
    }
}

