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


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.concurrent.ListenableFuture
import com.esri.arcgisruntime.data.FeatureCollection
import com.esri.arcgisruntime.data.FeatureCollectionTable
import com.esri.arcgisruntime.data.FeatureTable
import com.esri.arcgisruntime.data.Field
import com.esri.arcgisruntime.data.FieldDescription
import com.esri.arcgisruntime.data.Geodatabase
import com.esri.arcgisruntime.data.GeodatabaseFeatureTable
import com.esri.arcgisruntime.data.QueryParameters
import com.esri.arcgisruntime.data.ShapefileFeatureTable
import com.esri.arcgisruntime.data.TableDescription
import com.esri.arcgisruntime.geometry.AngularUnit
import com.esri.arcgisruntime.geometry.AngularUnitId
import com.esri.arcgisruntime.geometry.DatumTransformation
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.GeometryType
import com.esri.arcgisruntime.geometry.LinearUnit
import com.esri.arcgisruntime.geometry.LinearUnitId
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.PolygonBuilder
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.PolylineBuilder
import com.esri.arcgisruntime.geometry.SpatialReference
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.layers.KmlLayer
import com.esri.arcgisruntime.layers.Layer
import com.esri.arcgisruntime.layers.RasterLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.labeling.LabelExpression
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.ogc.kml.KmlDataset
import com.esri.arcgisruntime.raster.Raster
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.SimpleRenderer
import com.esri.arcgisruntime.symbology.TextSymbol
import org.osgeo.proj4j.CRSFactory
import org.osgeo.proj4j.CoordinateTransformFactory
import org.osgeo.proj4j.ProjCoordinate
import org.xmlpull.v1.XmlSerializer
import java.io.File
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
    private var openGeodatabases: MutableMap<FeatureLayer, Geodatabase> = mutableMapOf()

    fun loadShapefile(
        uri: Uri,
        graphicsOverlay: GraphicsOverlay
    ): LiveData<FeatureLayer> {
        val result = MutableLiveData<FeatureLayer>()
        val fullPath = getFullPath(uri)
        println(fullPath)
        val shapefileFeatureTable = ShapefileFeatureTable(fullPath)
        shapefileFeatureTable.loadAsync()
        shapefileFeatureTable.addDoneLoadingListener {
            if (shapefileFeatureTable.loadStatus == LoadStatus.LOADED) {
                val wgs84 = SpatialReference.create(4326)

                if (shapefileFeatureTable.spatialReference.wkid == 4326) {
                    // Shape-файл уже в WGS84, используем его напрямую
                    val featureLayer = FeatureLayer(shapefileFeatureTable)
                    applyRendererAndLabels(
                        featureLayer,
                        graphicsOverlay
                    ) // Применяем рендерер и метки
                    _layers.value = _layers.value?.plus(featureLayer) ?: listOf(featureLayer)
                    result.value = featureLayer
                } else {
                    // Shape-файл в другой пространственной привязке, перепроецируем и сохраняем в базу геоданных
                    reprojectAndSaveToGeodatabase(
                        fullPath,
                        shapefileFeatureTable,
                        wgs84,
                        graphicsOverlay
                    ) { geodatabaseFeatureTable ->
                        // Создаем слой на основе таблицы из базы геоданных
                        val featureLayer = FeatureLayer(geodatabaseFeatureTable)
                        applyRendererAndLabels(
                            featureLayer,
                            graphicsOverlay
                        )// Применяем рендерер и метки
                        _layers.value = _layers.value?.plus(featureLayer) ?: listOf(featureLayer)
                        result.value = featureLayer
                    }
                }
            } else {
                Log.e(
                    "MainActivity",
                    "Error loading shapefile: " + shapefileFeatureTable.loadError.message
                )
            }
        }
        return result
    }

    // Функция для перепроецирования и сохранения в базу геоданных
    private fun reprojectAndSaveToGeodatabase(
        fullPath: String,
        shapefileFeatureTable: ShapefileFeatureTable,
        wgs84: SpatialReference,
        graphicsOverlay: GraphicsOverlay,
        callback: (GeodatabaseFeatureTable) -> Unit
    ) {
        val geodatabaseFile = File(
            fullPath.substringBeforeLast("/"),
            fullPath.substringAfterLast("/").substringBeforeLast(".") + ".geodatabase"
        )
        if (geodatabaseFile.exists()) {
            geodatabaseFile.delete()
        }
        val geodatabaseFuture = Geodatabase.createAsync(geodatabaseFile.path)
        geodatabaseFuture.addDoneListener {
            try {
                val geodatabase = geodatabaseFuture.get()
                val tableDescription = TableDescription(
                    "my_feature_table",
                    SpatialReferences.getWgs84(),
                    shapefileFeatureTable.geometryType
                ) // Исправлено
                Log.d("DataRepository", "Table geometry type: ${tableDescription.geometryType}")
                val shapefileFields = shapefileFeatureTable.fields
                val fields = shapefileFields.map { shapefileField ->
                        FieldDescription(shapefileField.name, shapefileField.fieldType)
                    }
                tableDescription.fieldDescriptions.addAll(fields)
                tableDescription.apply {
                    setHasAttachments(false)
                    setHasM(false)
                    setHasZ(false)
                }
                val featureTableFuture = geodatabase.createTableAsync(tableDescription)
                featureTableFuture.addDoneListener {
                    val geodatabaseFeatureTable = featureTableFuture.get() as GeodatabaseFeatureTable
                    val features = shapefileFeatureTable.queryFeaturesAsync(QueryParameters()).get()
                    val sourceCRS = CRSFactory().createFromName("EPSG:28418") // Pulkovo 1942 / Gauss-Kruger zone 8
                    val targetCRS = CRSFactory().createFromName("EPSG:4326") // WGS84
                    val transform = CoordinateTransformFactory().createTransform(sourceCRS, targetCRS)
                    for (feature in features) {
                        val originalGeometry = feature.geometry
                        Log.d("DataRepository", "Original geometry: $originalGeometry")
                        val projectedGeometry = when (originalGeometry.geometryType) {
                            GeometryType.POINT -> {
                                val point = originalGeometry as Point
                                val sourceCoords = ProjCoordinate(point.x, point.y)
                                val targetCoords = ProjCoordinate()
                                transform.transform(sourceCoords, targetCoords)
                                Point(targetCoords.x, targetCoords.y, wgs84)
                            }
                            GeometryType.POLYLINE -> {
                                val polyline = originalGeometry as Polyline
                                val projectedPolylineBuilder = PolylineBuilder(wgs84)
                                for (point in polyline.parts[0].points) {
                                    val sourceCoords = ProjCoordinate(point.x, point.y)
                                    val targetCoords = ProjCoordinate()
                                    transform.transform(sourceCoords, targetCoords)
                                    projectedPolylineBuilder.addPoint(targetCoords.x, targetCoords.y)
                                }
                                projectedPolylineBuilder.toGeometry()
                            }
                            GeometryType.POLYGON -> {
                                val polygon = originalGeometry as Polygon
                                val projectedPolygonBuilder = PolygonBuilder(wgs84)
                                for (part in polygon.parts) {
                                    for (point in part.points) {
                                        val sourceCoords = ProjCoordinate(point.x, point.y)
                                        val targetCoords = ProjCoordinate()
                                        transform.transform(sourceCoords, targetCoords)
                                        projectedPolygonBuilder.addPoint(targetCoords.x, targetCoords.y)
                                    }
                                }
                                projectedPolygonBuilder.toGeometry()
                            }
                            else -> {
                                // Обработка других типов геометрии, если необходимо
                                null
                            }
                        }
//                        val originalGeometry = feature.geometry
//                        Log.d("DataRepository", "Original geometry: $originalGeometry")
//                        val projectedGeometry = GeometryEngine.project(originalGeometry, wgs84)
//                        Log.d("DataRepository", "Projected geometry: $projectedGeometry")
//                        Log.d("DataRepository", "Feature attributes: ${feature.attributes}")

                        // Создаем новый объект Feature и добавляем его в таблицу базы геоданных
                        if (projectedGeometry != null) {
                            Log.d("DataRepository", "Projected geometry: $projectedGeometry")
                            val attributes = feature.attributes
                            val newFeature = geodatabaseFeatureTable.createFeature(attributes, projectedGeometry)
                            geodatabaseFeatureTable.addFeatureAsync(newFeature)
                        }
                    }
                    callback(geodatabaseFeatureTable) // Вызываем callback с таблицей из базы геоданных

                    val geodatabaseUri = Uri.fromFile(geodatabaseFile)
                    loadGeodatabase(geodatabaseUri, graphicsOverlay)
                    //geodatabase.close() // Закрываем базу геоданных, чтобы сохранить изменения
                }
            }catch (e: Exception) {
                // Обрабатываем ошибки при создании базы геоданных
                Log.e("DataRepository", "Error creating geodatabase", e) // Выводим ошибку в лог
                // ... (добавьте другую обработку ошибок)
            }
        }
    }


    private fun applyRendererAndLabels(
        featureLayer: FeatureLayer,
        graphicsOverlay: GraphicsOverlay
    ) {
        when (featureLayer.featureTable.geometryType) {
            GeometryType.POINT -> {
                val redMarkerSymbol =
                    SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 10f)
                featureLayer.renderer = SimpleRenderer(redMarkerSymbol)
            }

            GeometryType.POLYGON -> {
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

        val features = featureLayer.featureTable.queryFeaturesAsync(QueryParameters()).get()


        for (feature in features) {
            if (feature.geometry is Point) {
                val point = feature.geometry as Point
                val pkValue =
                    (feature.attributes["PK"] as? Number)?.toInt()?.toString() ?: ""
                val textSymbol = TextSymbol(
                    14f,
                    pkValue,
                    Color.RED,
                    TextSymbol.HorizontalAlignment.RIGHT,
                    TextSymbol.VerticalAlignment.TOP
                ).apply {
                    color = Color.RED
                    haloColor = Color.WHITE
                    haloWidth = 2f
                }
                val graphic = Graphic(point, textSymbol)
                graphicsOverlay.graphics.add(graphic)
            } else if (feature.geometry is Polygon) {
                val point = feature.geometry as Polygon
                val pkValue =
                    (feature.attributes["MD"] as? Number)?.toInt()?.toString() ?: ""
                val textSymbol = TextSymbol(
                    14f,
                    pkValue,
                    Color.BLUE,
                    TextSymbol.HorizontalAlignment.LEFT,
                    TextSymbol.VerticalAlignment.BOTTOM
                ).apply {
                    color = Color.BLUE
                    haloColor = Color.WHITE
                    haloWidth = 2f

                }
                val graphic = Graphic(point, textSymbol)
                graphicsOverlay.graphics.add(graphic)

            }
        }

    }

    fun loadGeodatabase(
        uri: Uri,
        graphicsOverlay: GraphicsOverlay
    ): LiveData<FeatureLayer> {
        val result = MutableLiveData<FeatureLayer>()
        val fullPath = getFullPath(uri)
        val geodatabaseFile = File(fullPath)
        println(fullPath)
        println(geodatabaseFile.path)

        try {
            // Загружаем существующую базу геоданных
            val geodatabase = Geodatabase(geodatabaseFile.path)

            // Получаем список таблиц
            val featureTables = geodatabase.geodatabaseFeatureTables

            if (featureTables.isNotEmpty()) {
                // Выбираем первую таблицу из списка (если есть)
                val geodatabaseFeatureTable = featureTables.firstOrNull()

                if (geodatabaseFeatureTable != null) {
                    // Создаем слой на основе таблицы из базы геоданных
                    val featureLayer = FeatureLayer(geodatabaseFeatureTable)
                    applyRendererAndLabels(featureLayer, graphicsOverlay)
                    _layers.value = _layers.value?.plus(featureLayer) ?: listOf(featureLayer)
                    result.value = featureLayer
                    openGeodatabases[featureLayer] = geodatabase
                    removeLayerAndCloseGeodatabase(featureLayer)
                }else {
                    Log.e("DataRepository", "No feature tables found in geodatabase.")
                }
            } else {
                Log.e("DataRepository", "Geodatabase is empty.")
            }

        } catch (e: Exception) {
            Log.e("DataRepository", "Error loading geodatabase", e)
        }

        return result
    }
    fun removeLayerAndCloseGeodatabase(featureLayer: FeatureLayer) {
        _layers.value = _layers.value?.minus(featureLayer)
        openGeodatabases[featureLayer]?.close() // Закрываем базу геоданных
        openGeodatabases.remove(featureLayer) // Удаляем ссылку на базу геоданных
    }

    fun loadGeoTiff(uri: Uri): LiveData<RasterLayer> {
        val result = MutableLiveData<RasterLayer>()
        val fullPath = getFullPath(uri)
        println(fullPath)
        val raster = Raster(fullPath)
        val rasterLayer = RasterLayer(raster)
        _layers.value = _layers.value?.plus(rasterLayer) ?: listOf(rasterLayer)
        result.value = rasterLayer
        return result
    }

    fun loadKMLfile(uri: Uri): LiveData<KmlLayer> {
        val result = MutableLiveData<KmlLayer>()
        val fullPath = getFullPath(uri)
        println(fullPath)

        val kmlDataset = KmlDataset(fullPath)

        val kmlLayer = KmlLayer(kmlDataset)

        kmlLayer.addDoneLoadingListener {
            if (kmlLayer.loadStatus == LoadStatus.FAILED_TO_LOAD) {
                TODO()
            } else {
                _layers.value = _layers.value?.plus(kmlLayer) ?: listOf(kmlLayer)
                result.value = kmlLayer
            }
        }

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
        val line = PolylineBuilder(SpatialReferences.getWgs84())
        val lineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.MAGENTA, 4f)
        line.addPoint(currentPoint)
        line.addPoint(targetPoint)
        _graphic.value = Graphic(line.toGeometry(), lineSymbol)
        return Graphic(line.toGeometry(), lineSymbol)
    }

    fun calculateDistance(currentPoint: Point, targetPoint: Point) {
        val distance = GeometryEngine.distanceGeodetic(
            currentPoint, targetPoint, LinearUnit(
                LinearUnitId.KILOMETERS
            ), AngularUnit(AngularUnitId.DEGREES), GeodeticCurveType.GEODESIC
        )
        _distance.value = distance.getDistance() * 1000
    }

    fun calculateDeviation(currentPoint: Point, polyline: Polyline) {
        val nearestCoordinate = GeometryEngine.nearestCoordinate(polyline, currentPoint)
        val deviation = GeometryEngine.distanceGeodetic(
            currentPoint,
            nearestCoordinate.getCoordinate(),
            LinearUnit(LinearUnitId.KILOMETERS),
            AngularUnit(AngularUnitId.DEGREES),
            GeodeticCurveType.GEODESIC
        )
        _deviation.value = (deviation.getDistance() * 1000)
    }

    fun getFilteredAttributes(feature: Feature): Map<String, Any> {
        return feature.attributes.filterKeys { key ->
            key in listOf("MD", "PK", "Offset", "Profile", "ID")
        }
    }
}
