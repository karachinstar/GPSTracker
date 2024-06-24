package ru.karachinstar.diplom.gpstracker

import android.content.ContentValues
import android.content.Context
import android.graphics.Color

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Xml
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.esri.arcgisruntime.arcgisservices.LabelDefinition
import com.esri.arcgisruntime.data.ShapefileFeatureTable
import com.esri.arcgisruntime.geometry.AngularUnit
import com.esri.arcgisruntime.geometry.AngularUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.GeometryType
import com.esri.arcgisruntime.geometry.LinearUnit
import com.esri.arcgisruntime.geometry.LinearUnitId
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.PolylineBuilder
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.layers.KmlLayer
import com.esri.arcgisruntime.layers.Layer
import com.esri.arcgisruntime.layers.RasterLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.labeling.LabelExpression
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.ogc.kml.KmlDataset
import com.esri.arcgisruntime.raster.Raster
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.SimpleRenderer
import com.esri.arcgisruntime.symbology.TextSymbol
import org.xmlpull.v1.XmlSerializer
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataRepository(private val context: Context) {
    private var xmlSerializer: XmlSerializer? = null
    private var outputStream: OutputStream? = null
    private var uri: Uri? = null

    private val _layers = MutableLiveData<List<Layer>>()
    val layers: LiveData<List<Layer>> get() = _layers

    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> get() = _isRecording

    private val _distance = MutableLiveData<Double>()
    val distance: LiveData<Double> get() = _distance

    private val _deviation = MutableLiveData<Double>()
    val deviation: LiveData<Double> get() = _deviation

    private val _graphic = MutableLiveData<Graphic>()
    val graphic: LiveData<Graphic> get() = _graphic

    /**
     * Methods loading file
     */
    fun loadShapefile(uri: Uri): LiveData<FeatureLayer> {
        val result = MutableLiveData<FeatureLayer>()
        val fullPath = getFullPath(uri)
        println(fullPath)
        val shapefileFeatureTable = ShapefileFeatureTable(fullPath)
        shapefileFeatureTable.loadAsync()
        shapefileFeatureTable.addDoneLoadingListener {
            if (shapefileFeatureTable.loadStatus == LoadStatus.LOADED) {
                val featureLayer = FeatureLayer(shapefileFeatureTable)
                // Проверка типа геометрии слоя
                when (featureLayer.featureTable.geometryType) {
                    GeometryType.POINT -> {
                        // Если это точки, установите красный цвет
                        val redMarkerSymbol =
                            SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 10f)
                        featureLayer.renderer = SimpleRenderer(redMarkerSymbol)
                    }

                    GeometryType.POLYGON -> {
                        // Если это полигон, установите синий контур без заливки
                        val blueOutline =
                            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 2f)
                        val fillSymbol = SimpleFillSymbol(
                            SimpleFillSymbol.Style.NULL,
                            Color.TRANSPARENT,
                            blueOutline
                        )
                        featureLayer.renderer = SimpleRenderer(fillSymbol)
                    }

                    GeometryType.POLYLINE -> {
                        // Если это линия, установите бледно-голубой цвет
                        val paleBlueLineSymbol = SimpleLineSymbol(
                            SimpleLineSymbol.Style.SOLID,
                            Color.parseColor("#ADD8E6"),
                            2f
                        )
                        featureLayer.renderer = SimpleRenderer(paleBlueLineSymbol)
                    }

                    GeometryType.ENVELOPE -> TODO()
                    GeometryType.MULTIPOINT -> TODO()
                    GeometryType.UNKNOWN -> TODO()
                }
                _layers.value = _layers.value?.plus(featureLayer) ?: listOf(featureLayer)
                result.value = featureLayer
            } else {
                Log.e(
                    "MainActivity",
                    "Error loading shapefile: " + shapefileFeatureTable.loadError.message
                )
            }
        }
        return result
    }

    fun loadGeoTiff(uri: Uri): LiveData<RasterLayer> {
        val result = MutableLiveData<RasterLayer>()
        val fullPath = getFullPath(uri)
        val raster = Raster(fullPath)
        val rasterLayer = RasterLayer(raster)
        _layers.value = _layers.value?.plus(rasterLayer) ?: listOf(rasterLayer)
        result.value = rasterLayer
        return result
    }

    fun loadKMLfile(uri: Uri): LiveData<KmlLayer> {
        val result = MutableLiveData<KmlLayer>()
        val fullPath = getFullPath(uri)
        // Создание KmlDataset из Uri
        val kmlDataset = KmlDataset(fullPath)

        // Создание KmlLayer из KmlDataset
        val kmlLayer = KmlLayer(kmlDataset)

        // Обработка ошибок
        kmlLayer.addDoneLoadingListener {
            if (kmlLayer.loadStatus == LoadStatus.FAILED_TO_LOAD) {
                // Здесь вы можете обработать ошибку, возможно, установив значение для другого LiveData объекта,
                // который будет наблюдать ваша ViewModel или Activity.
            } else {
                _layers.value = _layers.value?.plus(kmlLayer) ?: listOf(kmlLayer)
                result.value = kmlLayer
            }
        }

        // Загрузка слоя
        kmlLayer.loadAsync()
        return result
    }

    fun sortLayers(layers: List<Layer>): List<Layer> {
        return layers.sortedWith(compareBy({ getLayerTypeOrder(it) }, { getGeometryTypeOrder(it) }))
    }

    private fun getLayerTypeOrder(layer: Layer): Int {
        return when (layer) {
            is RasterLayer -> 1
            is KmlLayer -> 2
            is FeatureLayer -> 3
            else -> 4
        }
    }

    fun getFullPath(uri: Uri): String {
        val path = uri.path?.substringAfter(":") // Получение пути к файлу из Uri
        return when {
            uri.path?.startsWith("/document/primary:") == true -> {
                "/storage/emulated/0/$path"
            }
            uri.path?.startsWith("/document/home:") == true -> {
                "/storage/emulated/0/Documents/$path"
            }
            else -> {
                // Обработка других случаев
                path ?: ""
            }
        }
    }

    private fun getGeometryTypeOrder(layer: Layer): Int {
        return if (layer is FeatureLayer) {
            when (layer.featureTable.geometryType) {
                GeometryType.POLYGON -> 1
                GeometryType.POLYLINE -> 2
                GeometryType.POINT -> 3
                else -> 4
            }
        } else {
            0
        }
    }


    fun toggleRecording() {
        if (_isRecording.value == true) {
            finishRecording()
        } else {
            startRecording()
        }
    }

    fun startRecording() {
        _isRecording.value = true
        val sdf = SimpleDateFormat("dd.MM.yyyy-HH_mm_ss", Locale.getDefault())
        if (context != null) {
            val currentDate = sdf.format(Date())
            val fileName = "$currentDate Track.kml"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.google-earth.kml+xml")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val documentsDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).path
                    put(MediaStore.MediaColumns.DATA, "$documentsDir/GPSTracker/Track/$fileName")
                } else {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/GPSTracker/Track")
                }
            }
            uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            outputStream = resolver.openOutputStream(uri!!)

            xmlSerializer = Xml.newSerializer()
            xmlSerializer?.setOutput(outputStream, "UTF-8")

            xmlSerializer?.startDocument("UTF-8", true)
            xmlSerializer?.text("\n")
            xmlSerializer?.startTag(null, "kml")
            xmlSerializer?.attribute(null, "xmlns", "http://www.opengis.net/kml/2.2")
            xmlSerializer?.text("\n")
            xmlSerializer?.startTag(null, "Document")
            xmlSerializer?.attribute(null, "id", "root_doc")
            xmlSerializer?.text("\n\t")
            xmlSerializer?.startTag(null, "Folder")
            xmlSerializer?.startTag(null, "name")
            xmlSerializer?.text("Track")
            xmlSerializer?.endTag(null, "name")
            xmlSerializer?.text("\n\t")
            xmlSerializer?.startTag(null, "Placemark")
            xmlSerializer?.text("\n\t\t")
            xmlSerializer?.startTag(null, "Style")
            xmlSerializer?.text("\n\t\t\t")
            xmlSerializer?.startTag(null, "LineStyle")
            xmlSerializer?.text("\n\t\t\t\t")
            xmlSerializer?.startTag(null, "color")
            xmlSerializer?.text("FF000000") // Черный цвет
            xmlSerializer?.endTag(null, "color")
            xmlSerializer?.text("\n\t\t\t\t")
            xmlSerializer?.startTag(null, "width")
            xmlSerializer?.text("5.66928") // Толщина линии
            xmlSerializer?.endTag(null, "width")
            xmlSerializer?.text("\n\t\t\t")
            xmlSerializer?.endTag(null, "LineStyle")
            xmlSerializer?.text("\n\t\t\t")
            xmlSerializer?.startTag(null, "PolyStyle")
            xmlSerializer?.startTag(null, "fill")
            xmlSerializer?.text("0")
            xmlSerializer?.endTag(null, "fill")
            xmlSerializer?.text("\n\t\t\t")
            xmlSerializer?.endTag(null, "PolyStyle")
            xmlSerializer?.text("\n\t\t")
            xmlSerializer?.endTag(null, "Style")
            xmlSerializer?.text("\n\t\t")
            xmlSerializer?.startTag(null, "LineString")
            xmlSerializer?.text("\n\t\t\t")
            xmlSerializer?.startTag(null, "coordinates")
        }
    }

    fun writeLocation(longitude: Double, latitude: Double) {
        xmlSerializer?.text("$longitude,$latitude,0 ")
        println("Я записываю $longitude, $latitude")
    }

    fun finishRecording() {
        xmlSerializer?.endTag(null, "coordinates")
        xmlSerializer?.text("\n\t\t")
        xmlSerializer?.endTag(null, "LineString")
        xmlSerializer?.text("\n\t")
        xmlSerializer?.endTag(null, "Placemark")
        xmlSerializer?.text("\n")
        xmlSerializer?.endTag(null, "Folder")
        xmlSerializer?.text("\n")
        xmlSerializer?.endTag(null, "Document")
        xmlSerializer?.text("\n")
        xmlSerializer?.endTag(null, "kml")
        xmlSerializer?.endDocument()
        outputStream?.close()
        _isRecording.value = false
    }

    fun drawLineAndTrackDistance(currentPoint: Point, targetPoint: Point): Graphic {
        // Отрисовать линию
        val line = PolylineBuilder(SpatialReferences.getWgs84())
        val lineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.MAGENTA, 4f)
        line.addPoint(currentPoint)
        line.addPoint(targetPoint)
        _graphic.value = Graphic(line.toGeometry(), lineSymbol)
        return Graphic(line.toGeometry(), lineSymbol)
    }

    fun calculateDistance(currentPoint: Point, targetPoint: Point) {
        // Рассчитать и отобразить расстояние
        val distance = GeometryEngine.distanceGeodetic(currentPoint, targetPoint, LinearUnit(
            LinearUnitId.KILOMETERS), AngularUnit(AngularUnitId.DEGREES), GeodeticCurveType.GEODESIC)
        _distance.value = distance.getDistance() * 1000
    }

    fun calculateDeviation(currentPoint: Point, polyline: Polyline) {
        // Рассчитать и отобразить отклонение
        val nearestCoordinate = GeometryEngine.nearestCoordinate(polyline, currentPoint)
        val deviation = GeometryEngine.distanceGeodetic(currentPoint, nearestCoordinate.getCoordinate(),
            LinearUnit(LinearUnitId.KILOMETERS), AngularUnit(AngularUnitId.DEGREES), GeodeticCurveType.GEODESIC)
        _deviation.value = (deviation.getDistance() * 1000)
    }
}