package ru.karachinstar.diplom.gpstracker

import android.app.Application
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import leakcanary.AppWatcher
import leakcanary.LeakCanary


class MyApplication : Application() {
    lateinit var map: ArcGISMap

    override fun onCreate() {
        super.onCreate()
        map = ArcGISMap(BasemapStyle.OSM_STANDARD)
        AppWatcher.config = AppWatcher.config.copy(watchActivities = true, watchFragments = true)
    }
}
