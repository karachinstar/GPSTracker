package ru.karachinstar.diplom.gpstracker


import android.app.Activity
import android.app.AlertDialog
import android.content.Intent

import android.graphics.Color
import android.net.Uri

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
import com.esri.arcgisruntime.mapping.view.LatitudeLongitudeGrid
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
    private lateinit var viewModel: FragmentMainViewModel
    private lateinit var repository: DataRepository
    private lateinit var viewModelFactory: ViewModelFactory
    private lateinit var mapViewModel: MapViewModel
    private lateinit var trackRecorderViewModel: TrackRecorderViewModel
    private lateinit var geodeticPathViewModel: GeodeticPathViewModel
    private lateinit var graphicsOverlay: GraphicsOverlay
    private lateinit var app: MyApplication
    private lateinit var locationDisplay: LocationDisplay
    private val TAG = "FragmentMain"
    private val locationChangedListener = LocationDisplay.LocationChangedListener { locationChangedEvent ->
        val location = locationChangedEvent.location.position
        val wgs84Point = GeometryEngine.project(location, SpatialReferences.getWgs84()) as Point
        Log.d("LocationRepository", "Fragment - Location updated: $wgs84Point")

        // Now use wgs84Point for your calculations:
        trackRecorderViewModel.onLocationChanged(wgs84Point.x, wgs84Point.y)

        if (geodeticPathViewModel.selectedFeature != null && geodeticPathViewModel.targetPoint != null) {
            geodeticPathViewModel.calculateDistance(
                wgs84Point,
                geodeticPathViewModel.targetPoint!!
            )
            geodeticPathViewModel.calculateDeviation(
                wgs84Point,
                geodeticPathViewModel.polyline!!
            )
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setApiKeyForApp()
        _binding = MainFragmentBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreate called")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onCreate called")
        mapView = binding.mapView
        //geodeticPathData = GeodeticPathData()
        app = requireActivity().application as MyApplication
        mapView.map = app.map
//        val grid = LatitudeLongitudeGrid()
//        mapView.grid = grid
        viewModel = ViewModelProvider(this)[FragmentMainViewModel::class.java]
        repository = DataRepository(requireContext())
        viewModelFactory = ViewModelFactory(requireActivity().application as MyApplication)
        mapViewModel = ViewModelProvider(this, viewModelFactory)[MapViewModel::class.java]
        setupMap()
        trackRecorderViewModel =
            ViewModelProvider(this, viewModelFactory)[TrackRecorderViewModel::class.java]
        geodeticPathViewModel =
            ViewModelProvider(this, viewModelFactory)[GeodeticPathViewModel::class.java]
        graphicsOverlay = GraphicsOverlay()
        binding.mapView.graphicsOverlays.add(graphicsOverlay)
        // Восстановление состояния из ViewModel
        viewModel.selectedFeature.observe(viewLifecycleOwner) { feature ->
            geodeticPathViewModel.selectedFeature = feature
        }
        viewModel.targetPoint.observe(viewLifecycleOwner) { point ->
            geodeticPathViewModel.targetPoint = point
        }
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "GPSTracker/Track")
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


        locationDisplay.addLocationChangedListener(locationChangedListener)

        binding.buttonRecordTrack.setOnClickListener {
            trackRecorderViewModel.startStopRecording()
        }
        binding.loadFileButton.setOnClickListener {
            openFile()
        }
        binding.buttonGPS.setOnClickListener {
            locationDisplay.autoPanMode = LocationDisplay.AutoPanMode.COMPASS_NAVIGATION
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
            viewModel.setSelectedFeature(identifiedFeature) // Сохраняем selectedFeature в ViewModel
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
                    geodeticPathViewModel.drawLineAndTrackDistance(
                        currentPoint,
                        geodeticPathViewModel.targetPoint!!
                    )
                graphicsOverlay.graphics.add(graphic)

                // Рассчитать и отобразить расстояние
                geodeticPathViewModel.calculateDistance(
                    currentPoint,
                    geodeticPathViewModel.targetPoint!!
                )

                // Рассчитать и отобразить отклонение
                if (graphicsOverlay.graphics.isNotEmpty()) {
                    val line = graphicsOverlay.graphics[0]
                    geodeticPathViewModel.polyline = line.geometry as? Polyline
                    if (geodeticPathViewModel.polyline != null) {
                        geodeticPathViewModel.calculateDeviation(
                            currentPoint,
                            geodeticPathViewModel.polyline!!
                        )
                    }
                }
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
        if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
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
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Очищаем binding
    }
    override fun onDestroy() {
        super.onDestroy()
        mapView.dispose()
        //_binding = null
    }


    private fun setApiKeyForApp() {
        // set your API key
        // Note: it is not best practice to store API keys in source code. The API key is referenced
        // here for the convenience of this tutorial.
        ArcGISRuntimeEnvironment.setApiKey("AAPKd7c72361e3384a66b1faabf8969fd6a4osFGH2DPIkVkzcd0eLJ68WuEV1O6C_LYjRBXs4exL5DjCUXj28G-uj5QDcHo7tKn")
    }

    companion object {
        private const val OPEN_FILE_REQUEST_CODE = 3
    }


}