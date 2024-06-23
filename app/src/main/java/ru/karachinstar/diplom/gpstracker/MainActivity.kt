package ru.karachinstar.diplom.gpstracker

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем ActivityResultLauncher
        requestPermissionLauncher

        // Запрашиваем разрешения
        viewModel.requestPermissions(this, requestPermissionLauncher)

        viewModel.permissionsGranted.observe(this) { granted ->
            if (granted) {
                setContentView(R.layout.activity_main)
                if (savedInstanceState == null) {
                    val existingFragment = supportFragmentManager.findFragmentById(R.id.container)
                    if (existingFragment == null) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.container, FragmentMain())
                            .setReorderingAllowed(true)
                            .commit()}
                }
            }
        }
    }
}