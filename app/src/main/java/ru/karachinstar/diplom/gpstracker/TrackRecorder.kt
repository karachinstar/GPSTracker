package ru.karachinstar.diplom.gpstracker

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract.Data
import android.provider.MediaStore
import android.util.Xml
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileWriter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
//
//class TrackRecorder(private val filePath: String) {
//    private var isRecording = false
//    private var xmlSerializer: XmlSerializer? = null
//    private var fileWriter: FileWriter? = null
//
//    fun toggleRecording() {
//        if (isRecording) {
//            finishRecording()
//        } else {
//            startRecording()
//        }
//        isRecording = !isRecording
//    }

class TrackRecorder(private val context: Context) {
    private var isRecording = false
    private var xmlSerializer: XmlSerializer? = null
    private var outputStream: OutputStream? = null
    private var uri: Uri? = null

    fun toggleRecording() {
        if (isRecording) {
            finishRecording()
        } else {
            startRecording()
        }
        isRecording = !isRecording
    }

    fun startRecording() {
//        val trackFolder = File(filePath, "Track")
//        if (!trackFolder.exists()) {
//            trackFolder.mkdir()
//        }
//        val sdf = SimpleDateFormat("dd.MM.yyyy-HH:mm", Locale.getDefault())
//        val currentDate = sdf.format(Date())
//        val trackFile = File(trackFolder, "$currentDate Track.kml")
//        xmlSerializer = Xml.newSerializer()
//        fileWriter = FileWriter(trackFile)
//        xmlSerializer?.setOutput(fileWriter)

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
        if (isRecording) {
            xmlSerializer?.text("$longitude,$latitude,0 ")
        }
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
//        xmlSerializer?.endDocument()
//        fileWriter?.close()

        xmlSerializer?.endDocument()
        outputStream?.close()
    }

}
