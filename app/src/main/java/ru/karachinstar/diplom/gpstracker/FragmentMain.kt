package ru.karachinstar.diplom.gpstracker


import android.app.Activity
import android.app.AlertDialog
import android.content.Intent

import android.graphics.Color
import android.net.Uri

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView

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
    private lateinit var lineGraphicsOverlay: GraphicsOverlay
    private lateinit var labelGraphicsOverlay: GraphicsOverlay

    private lateinit var app: MyApplication
    private lateinit var locationDisplay: LocationDisplay
    private val TAG = "FragmentMain"
    private val locationChangedListener = LocationDisplay.LocationChangedListener { locationChangedEvent ->
        val location = locationChangedEvent.location.position
        val wgs84Point = GeometryEngine.project(location, SpatialReferences.getWgs84()) as Point
        Log.d("LocationRepository", "Fragment - Location updated: $wgs84Point")
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
        app = requireActivity().application as MyApplication
        mapView.map = app.map
        viewModel = ViewModelProvider(this)[FragmentMainViewModel::class.java]
        repository = DataRepository(requireContext())
        viewModelFactory = ViewModelFactory(requireActivity().application as MyApplication)
        mapViewModel = ViewModelProvider(this, viewModelFactory)[MapViewModel::class.java]
        setupMap()
        trackRecorderViewModel =
            ViewModelProvider(this, viewModelFactory)[TrackRecorderViewModel::class.java]
        geodeticPathViewModel =
            ViewModelProvider(this, viewModelFactory)[GeodeticPathViewModel::class.java]

        lineGraphicsOverlay = GraphicsOverlay()
        labelGraphicsOverlay = GraphicsOverlay()
        binding.mapView.graphicsOverlays.add(lineGraphicsOverlay)
        binding.mapView.graphicsOverlays.add(labelGraphicsOverlay)

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
            binding.distanceTextView.setBackgroundColor(Color.WHITE)
            val formattedDistance = String.format("%.2f", distance)
            binding.distanceTextView.text = "Расстояние:\n $formattedDistance m"
            if (distance <= 1) {
                lineGraphicsOverlay.graphics.clear()
                geodeticPathViewModel.selectedFeature = null
                geodeticPathViewModel.targetPoint = null
                lineGraphicsOverlay.graphics.clear()
                binding.distanceTextView.setBackgroundColor(Color.TRANSPARENT)
                binding.distanceTextView.text = ""
            }
        }

        geodeticPathViewModel.deviation.observe(viewLifecycleOwner) { deviation ->
            binding.deviationTextView.setBackgroundColor(Color.WHITE)
            val formattedDeviation = String.format("%.2f", deviation)
            binding.deviationTextView.text = "Отклонение:\n $formattedDeviation m"
            if (binding.distanceTextView.text == "") {
                binding.deviationTextView.setBackgroundColor(Color.TRANSPARENT)
                binding.deviationTextView.text = ""
            }
        }

        geodeticPathViewModel.graphic.observe(viewLifecycleOwner) { graphic ->
            if (lineGraphicsOverlay.graphics.size > 0) {
                lineGraphicsOverlay.graphics.removeAt(0)
            }
            lineGraphicsOverlay.graphics.add(graphic)
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
        mapViewModel.layers.observe(viewLifecycleOwner) { layers ->
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

        val filteredAttributes = mapViewModel.getFilteredAttributesForFeature(identifiedFeature)

        val message = filteredAttributes.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        builder.setMessage(message)

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()}

        builder.setNegativeButton("Show Distance") { dialog, _ ->
            lineGraphicsOverlay.graphics.clear()
            viewModel.setSelectedFeature(identifiedFeature)
            drawLineAndTrackDistance(identifiedFeature, screenPoint)
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun drawLineAndTrackDistance(
        identifiedFeature: Feature,
        screenPoint: android.graphics.Point
    ) {
        val currentLocation = locationDisplay?.location?.position
        val mapPoint = binding.mapView.screenToLocation(screenPoint)
        val wgs84Point = GeometryEngine.project(mapPoint, SpatialReferences.getWgs84()) as Point

        if (currentLocation != null) {
            val currentPoint =
                Point(currentLocation.x, currentLocation.y, SpatialReferences.getWgs84())
            val geometry = identifiedFeature.geometry
            if (geometry is Point) {
                geodeticPathViewModel.targetPoint = geometry
            } else if (geometry is Polygon) {
                val result = GeometryEngine.nearestVertex(geometry, wgs84Point)
                geodeticPathViewModel.targetPoint = result.coordinate
            }

            if (geodeticPathViewModel.targetPoint != null) {
                val graphic =
                    geodeticPathViewModel.drawLineAndTrackDistance(
                        currentPoint,
                        geodeticPathViewModel.targetPoint!!
                    )
                lineGraphicsOverlay.graphics.add(graphic)
                geodeticPathViewModel.calculateDistance(
                    currentPoint,
                    geodeticPathViewModel.targetPoint!!
                )
                if (lineGraphicsOverlay.graphics.isNotEmpty()) {
                    val line = lineGraphicsOverlay.graphics[0]
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                handleFile(uri)
            }
        }
    }

    private fun handleFile(uri: Uri) {
        val path = uri.path
        val format = path?.substringAfterLast(".")
        when (format) {
            "tif" -> mapViewModel.loadGeoTiff(uri)
            "shp" -> {
                mapViewModel.loadShapefile(uri, labelGraphicsOverlay)
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
        lineGraphicsOverlay.graphics.clear()

        mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    override fun onDestroy() {
        super.onDestroy()
        mapView.dispose()
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