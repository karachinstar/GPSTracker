package ru.karachinstar.diplom.gpstracker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PermissionsViewModel : ViewModel() {
    private val _permissionsGranted = MutableLiveData<Boolean>(false)
    val permissionsGranted: LiveData<Boolean> = _permissionsGranted

    // Функция для запроса разрешений
    fun requestPermissions(activity: Activity,requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
                           manageStoragePermissionLauncher: ActivityResultLauncher<Intent>) {
        val permissionsToRequest = mutableListOf<String>()

        // Проверка разрешения на управление файлами (Android 11 и выше)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            // Запуск Intent для запроса разрешения на управление файлами
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageStoragePermissionLauncher.launch(intent)
        } else {
            // Проверка разрешения на запись во внешнее хранилище (Android 10 и ниже)
            if (ContextCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            // Проверка разрешения на доступ к точному местоположению
            if (ContextCompat.checkSelfPermission(
                    activity,android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }

            // Запуск запроса разрешений
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                // Все разрешения уже предоставлены
                _permissionsGranted.value = true
            }
        }
    }

    // Функция для обработки результатов запроса разрешений
    fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.all { it.value }
        _permissionsGranted.value = allGranted
    }
}