package ru.karachinstar.diplom.gpstracker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager


import android.net.Uri
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
    fun requestPermissions(activity: Activity,
                           requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
                           manageStorageRequestLauncher: ActivityResultLauncher<Intent>)
    {
        val permissionsToRequest = mutableListOf<String>()

        //Проверка разрешения на доступ к точному местоположению
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Проверка разрешения на доступ к приблизительному местоположению (для Android 14 и выше)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(activity,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Запрос разрешений для точного местоположения в фоновом режиме
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 и выше
            if (ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.SCHEDULE_EXACT_ALARM) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 и выше
                if (ContextCompat.checkSelfPermission(activity,
                        android.Manifest.permission.USE_EXACT_ALARM) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(android.Manifest.permission.USE_EXACT_ALARM)
                }
            }
        }

        // Проверка версии Android и запрос соответствующих разрешений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 и выше
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                manageStorageRequestLauncher.launch(intent)
            } else {
                checkAllPermissionsGranted(activity)
            }
        } else {
            // Android 9 и ниже
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                checkAllPermissionsGranted(activity)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Функция для обработки результатов запроса разрешений (кроме MANAGE_EXTERNAL_STORAGE)
    fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        // Проверяем, предоставлены ли все разрешения (кроме MANAGE_EXTERNAL_STORAGE)
        if (permissions.all { it.value }) {
            // Если все остальные разрешения предоставлены, проверяем MANAGE_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    _permissionsGranted.value = true
                }
            } else {
                _permissionsGranted.value = true
            }
        } else {
            _permissionsGranted.value = false
        }
    }

    // Функция для проверки, предоставлены ли все разрешения
    fun checkAllPermissionsGranted(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            _permissionsGranted.value = Environment.isExternalStorageManager() &&
                    ContextCompat.checkSelfPermission(activity,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        } else {
            _permissionsGranted.value = ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}