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
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.geometry.AngularUnit
import com.esri.arcgisruntime.geometry.AngularUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.LinearUnit
import com.esri.arcgisruntime.geometry.LinearUnitId
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.PolylineBuilder
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.symbology.SimpleLineSymbol

import ru.karachinstar.diplom.gpstracker.databinding.MainFragmentBinding
import java.io.File


class FragmentMain : Fragment() {
    private var _binding: MainFragmentBinding? = null
    private val binding: MainFragmentBinding
        get() {
            return _binding!!
        }
    private lateinit var mapView: MapView
    private lateinit var repository: DataRepository
    private lateinit var viewModelFactory: ViewModelFactory
    private lateinit var mapViewModel: MapViewModel
    private lateinit var trackRecorderViewModel: TrackRecorderViewModel
    private lateinit var geodeticPathViewModel: GeodeticPathViewModel
    private lateinit var graphicsOverlay: GraphicsOverlay
    private lateinit var app: MyApplication
    private lateinit var locationDisplay: LocationDisplay
//    private var geodeticPathData: GeodeticPathData? = null
//    private var selectedFeature: Feature? = null
//    private var targetPoint: Point? = null
//    private var polyline: Polyline? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MainFragmentBinding.inflate(inflater, container, false)
        mapView = binding.mapView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()
        setApiKeyForApp()
        //geodeticPathData = GeodeticPathData()
        app = requireActivity().application as MyApplication
        mapView.map = app.map
        repository = DataRepository(requireContext())
        viewModelFactory = ViewModelFactory(repository)
        mapViewModel = ViewModelProvider(this, viewModelFactory)[MapViewModel::class.java]
        setupMap()
        trackRecorderViewModel =
            ViewModelProvider(this, viewModelFactory)[TrackRecorderViewModel::class.java]
        geodeticPathViewModel =
            ViewModelProvider(this, viewModelFactory)[GeodeticPathViewModel::class.java]
        graphicsOverlay = GraphicsOverlay()
        binding.mapView.graphicsOverlays.add(graphicsOverlay)
        val folder: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "GPSTracker/Track"
            )
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Documents/GPSTracker/Track"
            )
        }
        if (!folder.exists()) {
            folder.mkdirs()
        }
        locationDisplay = mapView.locationDisplay
        setupTouchListener()
        locationDisplay.startAsync()

        geodeticPathViewModel.distance.observe(viewLifecycleOwner) { distance ->
            // Обновите текстовое поле для расстояния
            binding.distanceTextView.setBackgroundColor(Color.WHITE)
            val formattedDistance = String.format("%.2f", distance)
            binding.distanceTextView.text = "Расстояние:\n $formattedDistance m"
            if (distance <= 1) {
                graphicsOverlay.graphics.clear()
                geodeticPathViewModel.selectedFeature = null
                geodeticPathViewModel.targetPoint = null
                geodeticPathViewModel.polyline = null
                graphicsOverlay.graphics.clear()
                binding.distanceTextView.setBackgroundColor(Color.TRANSPARENT)
                binding.distanceTextView.text = ""
            }
        }

        geodeticPathViewModel.deviation.observe(viewLifecycleOwner) { deviation ->
            // Обновите текстовое поле для отклонения
            binding.deviationTextView.setBackgroundColor(Color.WHITE)
            val formattedDeviation = String.format("%.2f", deviation)
            binding.deviationTextView.text = "Отклонение:\n $formattedDeviation m"
            if (binding.distanceTextView.text == "") {
                binding.deviationTextView.setBackgroundColor(Color.TRANSPARENT)
                binding.deviationTextView.text = ""
            }
        }

        geodeticPathViewModel.graphic.observe(viewLifecycleOwner) { graphic ->
            // Удалите старую графику
            if (graphicsOverlay.graphics.size > 0) {
                graphicsOverlay.graphics.removeAt(0)
            }
            // Добавьте новую графику
            graphicsOverlay.graphics.add(graphic)
        }


        locationDisplay.addLocationChangedListener { locationChangedEvent ->
            val location = locationChangedEvent.location.position
            val wgs84Point = GeometryEngine.project(location, SpatialReferences.getWgs84()) as Point
            Log.d("LocationRepository", "Fragment - Location updated: $wgs84Point")

            // Now use wgs84Point for your calculations:
            trackRecorderViewModel.onLocationChanged(wgs84Point.x, wgs84Point.y)

            if (geodeticPathViewModel.selectedFeature != null && geodeticPathViewModel.targetPoint != null) {
                geodeticPathViewModel.calculateDistance(wgs84Point, geodeticPathViewModel.targetPoint!!)
                geodeticPathViewModel.calculateDeviation(wgs84Point, geodeticPathViewModel.polyline!!)
            }
        }

        binding.buttonRecordTrack.setOnClickListener {
            trackRecorderViewModel.startStopRecording()
        }
        binding.loadFileButton.setOnClickListener {
            openFile()
        }
        // Наблюдение за изменениями в слоях
        mapViewModel.layers.observe(viewLifecycleOwner) { layers ->
            // Обновление слоев на карте
            mapView.map.operationalLayers.clear()
            val sortedLayers = repository.sortLayers(layers)
            mapView.map.operationalLayers.addAll(sortedLayers)
        }

    }

    private fun setupTouchListener() {
        binding.mapView.onTouchListener =
            object : DefaultMapViewOnTouchListener(requireContext(), binding.mapView) {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val screenPoint = android.graphics.Point(Math.round(e.x), Math.round(e.y))
                    val identifyFuture =
                        binding.mapView.identifyLayersAsync(screenPoint, 12.0, false)
                    identifyFuture.addDoneListener {
                        val identifyResults = identifyFuture.get()
                        if (identifyResults.size > 0) {
                            val identifyResult = identifyResults[0]
                            val resultGeoElements = identifyResult.elements
                            if (resultGeoElements.size > 0) {
                                if (resultGeoElements[0] is Feature) {
                                    val identifiedFeature = resultGeoElements[0] as Feature
                                    val attributes = identifiedFeature.attributes
                                    showInfoDialog(attributes, identifiedFeature, screenPoint)
                                }
                            }
                        }
                    }
                    return super.onSingleTapConfirmed(e) || binding.mapView.performClick()
                }
            }
    }

    private fun showInfoDialog(
        attributes: Map<String, Any>,
        identifiedFeature: Feature,
        screenPoint: android.graphics.Point
    ) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Feature Information")

        val filteredAttributes = attributes.filterKeys { key ->
            key in listOf(
                "MD",
                "PK",
                "Offset",
                "Profile"
            )
        }

        val message = filteredAttributes.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        builder.setMessage(message)

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }

        builder.setNegativeButton("Show Distance") { dialog, _ ->
            graphicsOverlay.graphics.clear()
            geodeticPathViewModel.selectedFeature = identifiedFeature
            drawLineAndTrackDistance(identifiedFeature, screenPoint)
            dialog.dismiss()
            println("${geodeticPathViewModel.selectedFeature}")
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun drawLineAndTrackDistance(
        identifiedFeature: Feature,
        screenPoint: android.graphics.Point
    ) {
        // Получить текущее местоположение
        val currentLocation = locationDisplay?.location?.position
        val mapPoint = binding.mapView.screenToLocation(screenPoint)
        val wgs84Point = GeometryEngine.project(mapPoint, SpatialReferences.getWgs84()) as Point
        println(
            "WGS84 координаты: ${wgs84Point.y}, ${wgs84Point.x}\n" +
                    "$currentLocation"
        )

        if (currentLocation != null) {
            val currentPoint =
                Point(currentLocation.x, currentLocation.y, SpatialReferences.getWgs84())
            // Определить тип объекта касания
            val geometry = identifiedFeature.geometry
            //var targetPoint: Point? = null
            if (geometry is Point) {
                geodeticPathViewModel.targetPoint = geometry
            } else if (geometry is Polygon) {
                // Найти ближайшую вершину
                val result = GeometryEngine.nearestVertex(geometry, wgs84Point)
                geodeticPathViewModel.targetPoint = result.coordinate
            }

            if (geodeticPathViewModel.targetPoint != null) {
                // Отрисовать линию
                val graphic =
                    geodeticPathViewModel.drawLineAndTrackDistance(currentPoint, geodeticPathViewModel.targetPoint!!)
                graphicsOverlay.graphics.add(graphic)

                // Рассчитать и отобразить расстояние
                geodeticPathViewModel.calculateDistance(currentPoint, geodeticPathViewModel.targetPoint!!)

                // Рассчитать и отобразить отклонение
                if (graphicsOverlay.graphics.isNotEmpty()) {
                    val line = graphicsOverlay.graphics[0]
                    geodeticPathViewModel.polyline = line.geometry as? Polyline
                    if (geodeticPathViewModel.polyline != null) {
                        geodeticPathViewModel.calculateDeviation(currentPoint, geodeticPathViewModel.polyline!!)
                    }
                }
            }
        }
    }


    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, MY_PERMISSIONS_REQUEST_MANAGE_EXTERNAL_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
                )
            }
        }
        // Запрос разрешения ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
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
                        requireContext(),
                        "Permission denied to write to external storage",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Разрешение на доступ к местоположению было предоставлено
                } else {
                    // Разрешение на доступ к местоположению было отклонено
                    Toast.makeText(
                        requireContext(),
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
                        requireContext(),
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
            "tif" -> mapViewModel.loadGeoTiff(uri)
            "shp" -> {
                mapViewModel.loadShapefile(uri)
            }

            "kml" -> {
                mapViewModel.loadKMLfile(uri)
            }

            else -> {
                Toast.makeText(requireContext(), "Unsupported file format", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupMap() {
        mapView.setViewpoint(mapViewModel.mapCenter)
    }


    override fun onPause() {
        mapViewModel.mapCenter = mapView.getCurrentViewpoint(Viewpoint.Type.CENTER_AND_SCALE)
        mapView.map.operationalLayers.clear()
        graphicsOverlay.graphics.clear()
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
        _binding = null
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