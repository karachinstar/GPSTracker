package ru.karachinstar.diplom.gpstracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.data.ShapefileFeatureTable
import com.esri.arcgisruntime.geometry.GeometryType
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.layers.KmlLayer
import com.esri.arcgisruntime.layers.Layer
import com.esri.arcgisruntime.layers.RasterLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.ogc.kml.KmlDataset
import com.esri.arcgisruntime.raster.Raster
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.SimpleRenderer
import ru.karachinstar.diplom.gpstracker.databinding.ActivityMainBinding
import java.io.File


class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val mapView: MapView by lazy {
        binding.mapView
    }
    private val repository = DataRepository(this)
    private val viewModelFactory = ViewModelFactory(repository)
    private val mapViewModel: MapViewModel by viewModels { viewModelFactory }
    private val trackRecorderViewModel: TrackRecorderViewModel by viewModels { viewModelFactory }



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val locationDisplay = binding.mapView.locationDisplay

        val folder: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "GPSTracker/Track")
        } else {
            File(Environment.getExternalStorageDirectory(), "Documents/GPSTracker/Track")
        }
        if (!folder.exists()) {
            folder.mkdirs()
        }

        locationDisplay.autoPanMode = LocationDisplay.AutoPanMode.COMPASS_NAVIGATION
        requestPermissions()
        setApiKeyForApp()
        setupMap()
        setupFileButton()
        setupSpinner()
        setupTouchListener()
        locationDisplay.startAsync()
        locationDisplay.addLocationChangedListener { locationChangedEvent ->
            val location = locationChangedEvent.location.position
            trackRecorderViewModel.onLocationChanged(location.x, location.y)
        }


        binding.buttonRecordTrack.setOnClickListener {
            trackRecorderViewModel.startStopRecording()
        }
        // Наблюдение за изменениями в слоях
        mapViewModel.layers.observe(this) { layers ->
            // Обновление слоев на карте
            mapView.map.operationalLayers.clear()
            mapView.map.operationalLayers.addAll(layers)
        }

    }

    private fun setupTouchListener() {
        binding.mapView.onTouchListener = getOnTouchListener()
    }

    private fun getOnTouchListener(): DefaultMapViewOnTouchListener {
        return object : DefaultMapViewOnTouchListener(this, binding.mapView) {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val screenPoint = android.graphics.Point(Math.round(e.x), Math.round(e.y))

                // identify on all layers
                val identifyFuture =
                    binding.mapView.identifyLayersAsync(screenPoint, 12.0, false)
                identifyFuture.addDoneListener {
                    try {
                        val identifyResults = identifyFuture.get()
                        if (identifyResults.size > 0) {
                            val identifyResult =
                                identifyResults[0] // get result from the top-most layer
                            val resultGeoElements = identifyResult.elements

                            if (resultGeoElements.size > 0) {
                                if (resultGeoElements[0] is Feature) {
                                    val identifiedFeature = resultGeoElements[0] as Feature

                                    // Use identifiedFeature's attributes to get information about the feature
                                    val attributes = identifiedFeature.attributes
                                    showInfoDialog(attributes)
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e(
                            "IdentifyError",
                            "Error in identifyLayersAsync: " + ex.message
                        )
                    }
                }

                // Call performClick to handle accessibility actions properly
                return super.onSingleTapConfirmed(e) || binding.mapView.performClick()
            }
        }
    }

    private fun showInfoDialog(attributes: Map<String, Any>) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Feature Information")

        // Отфильтровать атрибуты, которые вы хотите отобразить
        val filteredAttributes = attributes.filterKeys { key ->
            key in listOf(
                "MD",
                "PK",
                "Offset",
                "Profile"
            ) // Замените на имена атрибутов, которые вы хотите отобразить
        }

        val message = filteredAttributes.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        builder.setMessage(message)

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }


    private fun setupFileButton() {
        binding.loadFileButton.setOnClickListener {
            openFile()
        }
    }

    private fun setupSpinner() {
        val spinner: Spinner = findViewById(R.id.spinnerLayersList)
        val layers = binding.mapView.map.operationalLayers
        val layerNames = layers.map { it.name }.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, layerNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                val selectedLayerName = parent.getItemAtPosition(position).toString()
                val selectedLayer = layers.firstOrNull { it.name == selectedLayerName }
                if (selectedLayer != null) {
                    layers.remove(selectedLayer)
                    layers.add(0, selectedLayer)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Ничего не делать
            }
        }
    }

    private fun updateSpinner() {
        val layers = binding.mapView.map.operationalLayers
        val layerNames = layers.map { it.name }.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, layerNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLayersList.adapter = adapter
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, MY_PERMISSIONS_REQUEST_MANAGE_EXTERNAL_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
                )
            }
        }
        // Запрос разрешения ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Разрешение было предоставлено
                } else {
                    // Разрешение было отклонено
                    Toast.makeText(
                        this,
                        "Permission denied to write to external storage",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Разрешение на доступ к местоположению было предоставлено
                } else {
                    // Разрешение на доступ к местоположению было отклонено
                    Toast.makeText(
                        this,
                        "Permission denied to access fine location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            // Другие 'case' строки для проверки других
            // разрешений, запрашиваемых этим приложением.
            else -> {
                // Игнорировать все другие запросы разрешений
            }
        }
    }

    private fun setupMap() {
        val map = ArcGISMap(BasemapStyle.OSM_STANDARD)
        mapView.map = map
        mapView.setViewpoint(Viewpoint(52.2750, 104.2605, 72000.0))
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Добавление флага
        }
        startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MY_PERMISSIONS_REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    // Разрешение не было предоставлено
                    Toast.makeText(
                        this,
                        "Permission denied to manage external storage",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data // Uri выбранного файла
            if (uri != null) {
                handleFile(uri)
            }
        }
    }

    private fun handleFile(uri: Uri) {
        // Получение пути к файлу из Uri
        val path = uri.path
        // Определение формата файла по его расширению
        val format = path?.substringAfterLast(".")
        when (format) {
            "tif" -> loadGeoTiff(uri)
            "shp" -> {
                Log.d("MainActivity", "Loading shapefile from $path")
                mapViewModel.loadShapefile(uri)

            }
            "kml" -> {
                loadKMLfile(uri)
            }

            else -> Toast.makeText(this, "Unsupported file format", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadShapefile(uri: Uri) {
        val path = uri.path?.substringAfter(":") // Получение пути к файлу из Uri
        val fullPath = "/storage/emulated/0/$path"
        Log.d("MainActivity", "Full path to shapefile: $fullPath")

        // Ваш код для Android 11 и выше
        val shapefileFeatureTable = ShapefileFeatureTable(fullPath)
        shapefileFeatureTable.loadAsync()
        shapefileFeatureTable.addDoneLoadingListener {
            if (shapefileFeatureTable.loadStatus == LoadStatus.LOADED) {
                val featureLayer = FeatureLayer(shapefileFeatureTable)
                // Проверка типа геометрии слоя
                when (featureLayer.featureTable.geometryType) {
                    GeometryType.POINT -> {
                        // Если это точки, установите красный цвет
                        val redMarkerSymbol = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 10f)
                        featureLayer.renderer = SimpleRenderer(redMarkerSymbol)
                    }
                    GeometryType.POLYGON -> {
                        // Если это полигон, установите синий контур без заливки
                        val blueOutline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 2f)
                        val fillSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, Color.TRANSPARENT, blueOutline)
                        featureLayer.renderer = SimpleRenderer(fillSymbol)
                    }
                    GeometryType.POLYLINE -> {
                        // Если это линия, установите бледно-голубой цвет
                        val paleBlueLineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.parseColor("#ADD8E6"), 2f)
                        featureLayer.renderer = SimpleRenderer(paleBlueLineSymbol)
                    }

                    GeometryType.ENVELOPE -> TODO()
                    GeometryType.MULTIPOINT -> TODO()
                    GeometryType.UNKNOWN -> TODO()
                }
                layersSortedToMap(featureLayer)
                updateSpinner()
            } else {
                Log.e(
                    "MainActivity",
                    "Error loading shapefile: " + shapefileFeatureTable.loadError.message
                )
            }
        }
    }


    private fun loadGeoTiff(uri: Uri) {
        val path = uri.path?.substringAfter(":") // Получение пути к файлу из Uri
        Toast.makeText(this, "$path", Toast.LENGTH_SHORT).show()
        val fullPath = "/storage/emulated/0/$path"
        val raster = Raster(fullPath)
        val rasterLayer = RasterLayer(raster)
        layersSortedToMap(rasterLayer)
        updateSpinner()
    }

    private fun loadKMLfile(uri: Uri) {
        val path = uri.path?.substringAfter(":") // Получение пути к файлу из Uri
        Toast.makeText(this, "$path", Toast.LENGTH_SHORT).show()
        val fullPath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            "/storage/emulated/0/Documents/$path"
        } else {
            "/storage/emulated/0/$path"
        }
        // Создание KmlDataset из Uri
        val kmlDataset = KmlDataset(fullPath)

        // Создание KmlLayer из KmlDataset
        val kmlLayer = KmlLayer(kmlDataset)

        // Добавление слоя на карту

        binding.mapView.map.operationalLayers.add(kmlLayer)

        // Обработка ошибок
        kmlLayer.addDoneLoadingListener {
            if (kmlLayer.loadStatus == LoadStatus.FAILED_TO_LOAD) {
                Toast.makeText(this, "Failed to load KML layer", Toast.LENGTH_SHORT).show()
            }
        }

        // Загрузка слоя
        kmlLayer.loadAsync()
        updateSpinner()


    }

    private fun layersSortedToMap(layer: Layer){
        when (layer){
            is FeatureLayer -> {
                binding.mapView.map.operationalLayers.add(layer)
            }
            is RasterLayer -> {
                binding.mapView.map.operationalLayers.add(layer)
            }
            is KmlLayer -> {
                binding.mapView.map.operationalLayers.add(layer)
                layer.addDoneLoadingListener {
                    if (layer.loadStatus == LoadStatus.FAILED_TO_LOAD) {
                        Toast.makeText(this, "Failed to load KML layer", Toast.LENGTH_SHORT).show()
                    }
                }

                // Загрузка слоя
                //layer.loadAsync()
                //updateSpinner()
            }
        }
        //binding.mapView.map.operationalLayers.add(layer)
        // Сортируем слои в соответствии с критериями сортировки
        //val sortedLayers = mapView.map.operationalLayers.sortedWith(compareBy({ getLayerType(it) }, { getGeometryType(it) }))

        // Очищаем список слоев
        //mapView.map.operationalLayers.clear()

        // Добавляем отсортированные слои обратно на карту
       // mapView.map.operationalLayers.addAll(sortedLayers)
        updateSpinner()
    }

//    fun getLayerType(layer: Layer): String {
//        return when (layer) {
//            is FeatureLayer -> "3"
//            is RasterLayer -> "1"
//            is KmlLayer -> "2"
//            else -> "unknown"
//        }
//    }
//    fun getGeometryType(layer: Layer): String {
//        return if (layer is FeatureLayer) {
//            when (layer.featureTable.geometryType) {
//                GeometryType.POINT -> "6"
//                GeometryType.POLYLINE -> "5"
//                GeometryType.POLYGON -> "4"
//                else -> "unknown"
//            }
//        } else {
//            "unknown"
//        }
//    }


    override fun onPause() {
        mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }


    override fun onDestroy() {
        mapView.dispose()
        super.onDestroy()
    }

    private fun setApiKeyForApp() {
        // set your API key
        // Note: it is not best practice to store API keys in source code. The API key is referenced
        // here for the convenience of this tutorial.

        ArcGISRuntimeEnvironment.setApiKey("AAPKd7c72361e3384a66b1faabf8969fd6a4osFGH2DPIkVkzcd0eLJ68WuEV1O6C_LYjRBXs4exL5DjCUXj28G-uj5QDcHo7tKn")

    }

    companion object {
        private const val MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1
        private const val MY_PERMISSIONS_REQUEST_MANAGE_EXTERNAL_STORAGE = 2
        private const val MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 4
        private const val OPEN_FILE_REQUEST_CODE = 3
    }
}

