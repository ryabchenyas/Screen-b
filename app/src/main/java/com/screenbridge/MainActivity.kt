package com.screenbridge

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var status by mutableStateOf("Ожидание запуска")
    private var hasScreenPermission by mutableStateOf(false)

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                hasScreenPermission = true
                status = "Разрешение получено. Следующий этап — захват и передача изображения."
            } else {
                hasScreenPermission = false
                status = "Доступ к экрану не предоставлен."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Screen Bridge", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Передача изображения с телефона помощнику")
                        Spacer(modifier = Modifier.height(28.dp))
                        Text("Состояние: $status")
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val manager = getSystemService(MEDIA_PROJECTION_SERVICE)
                                    as MediaProjectionManager
                                screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (hasScreenPermission) "Запросить доступ снова" else "Начать")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                hasScreenPermission = false
                                status = "Остановлено. Активная передача пока не запускается."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Остановить") }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Это ранний прототип: сейчас проверяется системное разрешение Android. " +
                                "Передача видео и подключение к помощнику будут добавлены следующими этапами.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
