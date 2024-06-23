package ru.karachinstar.diplom.gpstracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val viewModel: PermissionsViewModel by viewModels()

    // ActivityResultLauncher для запроса остальных разрешений
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> by lazy {
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            viewModel.handlePermissionsResult(permissions)
        }
    }

//    // ActivityResultLauncher для запроса разрешения на управление файлами
//    private val manageStoragePermissionLauncher: ActivityResultLauncher<Intent> by lazy {
//        registerForActivityResult(
//            ActivityResultContracts.StartActivityForResult()
//        ) { result ->
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                if (Environment.isExternalStorageManager()) {
//                    // Разрешение на управление файлами предоставлено
//                    viewModel.requestPermissions(this, requestPermissionLauncher, manageStoragePermissionLauncher)
//                } else {
//                    // Обработка отказа в разрешении на управление файлами
//                }
//            }
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем ActivityResultLauncher
        requestPermissionLauncher

        // Запрашиваем разрешения
        viewModel.requestPermissions(this, requestPermissionLauncher)

        viewModel.permissionsGranted.observe(this) { granted ->
            if (granted) {
                setContentView(R.layout.activity_main)
                if (savedInstanceState ==null) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, FragmentMain())
                        .commitNow()
                }
            }
        }
    }
}