package com.screenbridge

import android.net.Uri
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
    private var status by mutableStateOf("Выбери изображение для теста")
    private var selectedImage by mutableStateOf<Uri?>(null)

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedImage = uri
            status = if (uri != null) {
                "Изображение выбрано. Следующий этап — подключить анализ."
            } else {
                "Выбор изображения отменён."
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
                        Text("Выбери один снимок из галереи — приложение пока ничего не отправляет.")
                        Spacer(modifier = Modifier.height(28.dp))
                        Text("Состояние: $status")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (selectedImage == null) "Файл не выбран"
                            else "Выбран файл: ${selectedImage.toString().substringAfterLast('/')}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Выбрать изображение")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                selectedImage = null
                                status = "Выбор сброшен."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Сбросить")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Это промежуточная версия: выбранное изображение остаётся на телефоне. " +
                                "Отправка в OpenAI и голосовое описание будут подключены отдельно, " +
                                "после проверки выбора файла.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
