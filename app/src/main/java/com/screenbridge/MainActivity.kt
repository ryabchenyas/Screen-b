package com.screenbridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var status by mutableStateOf("Выбери изображение для анализа")
    private var selectedImage by mutableStateOf<Uri?>(null)
    private var apiKey by mutableStateOf("")
    private var isBusy by mutableStateOf(false)
    private var answer by mutableStateOf("")
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private lateinit var tts: TextToSpeech

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedImage = uri
            answer = ""
            status = if (uri != null) {
                "Изображение выбрано. Нажми «Проанализировать»."
            } else {
                "Выбор изображения отменён."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiKey = ApiKeyStore.load(this).orEmpty()
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Screen Bridge", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Выбери одну фотографию. Она будет отправлена в OpenAI только после нажатия кнопки ниже.")
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("OpenAI API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    ApiKeyStore.save(this@MainActivity, apiKey.trim())
                                    status = "Ключ сохранён только на этом телефоне."
                                } catch (e: Exception) {
                                    status = "Не удалось сохранить ключ: ${e.localizedMessage ?: "ошибка"}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = apiKey.trim().startsWith("sk-")
                        ) {
                            Text("Сохранить ключ на телефоне")
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Состояние: $status")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (selectedImage == null) "Файл не выбран"
                            else "Изображение выбрано",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy
                        ) {
                            Text("Выбрать изображение")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { analyzeSelectedImage() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedImage != null && apiKey.trim().startsWith("sk-") && !isBusy
                        ) {
                            Text(if (isBusy) "Анализирую..." else "Проанализировать и озвучить")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                selectedImage = null
                                answer = ""
                                status = "Выбор сброшен."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy
                        ) {
                            Text("Сбросить")
                        }
                        if (answer.isNotBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Ответ ИИ", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(answer)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { speak(answer) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = ttsReady
                            ) {
                                Text("Повторить озвучивание")
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Важно: API оплачивается отдельно от ChatGPT. Ключ хранится в зашифрованном виде на телефоне, но это тестовая версия для личного использования. Не используй её на чужих или общедоступных устройствах. Отправляется только выбранное изображение и только после нажатия кнопки анализа.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun analyzeSelectedImage() {
        val uri = selectedImage ?: return
        val key = apiKey.trim()
        if (!key.startsWith("sk-")) {
            status = "Сначала введи действующий OpenAI API key."
            return
        }
        isBusy = true
        answer = ""
        status = "Подготавливаю изображение и отправляю запрос..."
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    requestImageDescription(uri, key)
                }
                answer = result
                status = "Готово. Ответ получен."
                speak(result)
            } catch (e: Exception) {
                status = "Ошибка: ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                isBusy = false
            }
        }
    }

    private fun requestImageDescription(uri: Uri, key: String): String {
        val dataUrl = imageAsDataUrl(uri)
        val content = JSONArray()
            .put(JSONObject().put("type", "input_text").put(
                "text",
                "Опиши по-русски, что изображено на фотографии. Сначала главное, затем важные детали. Говори коротко и естественно, как голосовой помощник. Если на изображении есть текст, прочитай наиболее важный."
            ))
            .put(JSONObject().put("type", "input_image")
                .put("image_url", dataUrl)
                .put("detail", "high"))

        val message = JSONObject()
            .put("role", "user")
            .put("content", content)

        val body = JSONObject()
            .put("model", "gpt-5-mini")
            .put("input", JSONArray().put(message))
            .put("max_output_tokens", 250)

        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val messageText = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw IllegalStateException(messageText?.takeIf { it.isNotBlank() } ?: "OpenAI API вернул HTTP $code")
            }

            val json = JSONObject(responseText)
            val output = json.optJSONArray("output") ?: JSONArray()
            val pieces = mutableListOf<String>()
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val parts = item.optJSONArray("content") ?: continue
                for (j in 0 until parts.length()) {
                    val part = parts.optJSONObject(j) ?: continue
                    if (part.optString("type") == "output_text") {
                        part.optString("text").takeIf { it.isNotBlank() }?.let { pieces.add(it) }
                    }
                }
            }
            return pieces.joinToString("\n").ifBlank {
                throw IllegalStateException("В ответе API не найден текст. Попробуй ещё раз.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun imageAsDataUrl(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть изображение" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Формат изображения не распознан" }

        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось повторно открыть изображение" }
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalStateException("Не удалось прочитать изображение")

        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, output)
            output.toByteArray()
        }
        bitmap.recycle()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "screen-bridge-answer")
        } else {
            pendingSpeech = text
            status = "Ответ готов. Подготавливаю русский голос..."
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val languageResult = tts.setLanguage(Locale("ru", "RU"))
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            if (ttsReady) {
                pendingSpeech?.let {
                    tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, "screen-bridge-answer")
                    pendingSpeech = null
                }
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}

private object ApiKeyStore {
    private const val PREFS = "screen_bridge_secure"
    private const val KEY_ALIAS = "screen_bridge_api_key_v1"
    private const val VALUE_KEY = "encrypted_key"
    private const val IV_KEY = "encryption_iv"

    fun save(context: Context, value: String) {
        require(value.startsWith("sk-")) { "Ключ должен начинаться с sk-." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VALUE_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): String? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = Base64.decode(prefs.getString(VALUE_KEY, null), Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString(IV_KEY, null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
