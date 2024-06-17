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
import com.esri.arcgisruntime.data.ShapefileFeatureTable
import com.esri.arcgisruntime.geometry.GeometryType
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.layers.KmlLayer
import com.esri.arcgisruntime.layers.Layer
import com.esri.arcgisruntime.layers.RasterLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.ogc.kml.KmlDataset
import com.esri.arcgisruntime.raster.Raster
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.SimpleRenderer
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

    // Метод для загрузки Shapefile
    fun loadShapefile(uri: Uri): LiveData<FeatureLayer> {
        val result = MutableLiveData<FeatureLayer>()
        val path = uri.path?.substringAfter(":") // Получение пути к файлу из Uri
        val fullPath = "/storage/emulated/0/$path"
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

    // Метод для загрузки GeoTiff
    fun loadGeoTiff(uri: Uri): LiveData<RasterLayer> {
        val result = MutableLiveData<RasterLayer>()
        val path = uri.path?.substringAfter(":") // Получение пути к файлу из Uri
        val fullPath = "/storage/emulated/0/$path"
        val raster = Raster(fullPath)
        val rasterLayer = RasterLayer(raster)
        result.value = rasterLayer
        return result
    }

    // Метод для загрузки KML
    fun loadKMLfile(uri: Uri): LiveData<KmlLayer> {
        val result = MutableLiveData<KmlLayer>()
        val path = uri.path?.substringAfter(":") // Получение пути к файлу из Uri
        val fullPath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            "/storage/emulated/0/Documents/$path"
        } else {
            "/storage/emulated/0/$path"
        }
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
                result.value = kmlLayer
            }
        }

        // Загрузка слоя
        kmlLayer.loadAsync()
        return result
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
        val currentDate = sdf.format(Date())
        val fileName = "$currentDate Track.kml"
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.google-earth.kml+xml")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).path
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
}