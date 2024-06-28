package ru.karachinstar.diplom.gpstracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData


class MainActivity : AppCompatActivity() {
    private val viewModel: PermissionsViewModel by viewModels()
    private lateinit var manageStorageRequestLauncher: ActivityResultLauncher<Intent>

    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> by lazy {
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            viewModel.handlePermissionsResult(permissions)}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called") // Лог при создании активности

        manageStorageRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.checkAllPermissionsGranted(this)
            } else {
                (viewModel.permissionsGranted as MutableLiveData<Boolean>).postValue(false)
            }
        }

        requestPermissionLauncher

        viewModel.requestPermissions(this, requestPermissionLauncher, manageStorageRequestLauncher)

        viewModel.permissionsGranted.observe(this) { granted ->
            if (granted) {
                Log.d("MainActivity", "Permissions granted, setting content view") // Лог при получении разрешений
                setContentView(R.layout.activity_main)
                if (savedInstanceState == null) {
                    val existingFragment = supportFragmentManager.findFragmentById(R.id.container)
                    if (existingFragment == null) {
                        Log.d("MainActivity", "Loading FragmentMain") // Лог при загрузке фрагмента
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.container, FragmentMain())
                            .setReorderingAllowed(true)
                            .commit()
                    }
                }
            } else {
                Log.d("MainActivity", "Permissions not granted") // Лог при отсутствии разрешений
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called") // Лог при возобновлении активности
    }
}