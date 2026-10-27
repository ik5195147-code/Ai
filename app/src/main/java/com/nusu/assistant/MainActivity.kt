package com.nusu.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecureStore(this)
        setContent { MaterialTheme { Screen(store) } }
    }

    private fun startAssistant() {
        ContextCompat.startForegroundService(this, Intent(this, AssistantService::class.java))
    }

    private fun stopAssistant() {
        startService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_STOP))
    }

    @Composable
    private fun Screen(store: SecureStore) {
        var key by remember { mutableStateOf(store.apiKey) }
        var model by remember { mutableStateOf(store.model) }
        var voice by remember { mutableStateOf(store.voice) }
        var a11yOn by remember { mutableStateOf(false) }
        val status by AssistantService.status.collectAsState()

        LaunchedEffect(Unit) {
            while (true) { a11yOn = NusuAccessibilityService.instance != null; delay(1000) }
        }

        val perms = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { r -> if (r[Manifest.permission.RECORD_AUDIO] == true) startAssistant() }

        fun save() { store.apiKey = key; store.model = model; store.voice = voice }

        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("NUSU Starter", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(),
                label = { Text("Gemini API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(),
                label = { Text("Live model") }, singleLine = true)
            OutlinedTextField(voice, { voice = it }, Modifier.fillMaxWidth(),
                label = { Text("Voice (e.g. Puck)") }, singleLine = true)
            OutlinedButton({ save() }, Modifier.fillMaxWidth()) { Text("Save settings") }

            Text("Accessibility service: ${if (a11yOn) "ON" else "OFF"}")
            OutlinedButton(
                { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                Modifier.fillMaxWidth()
            ) { Text("1. Enable NUSU in Accessibility settings") }
            OutlinedButton(
                {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                },
                Modifier.fillMaxWidth()
            ) { Text("2. Allow display over other apps") }

            Button(
                {
                    save()
                    perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                },
                Modifier.fillMaxWidth()
            ) { Text("3. Start assistant") }
            OutlinedButton({ stopAssistant() }, Modifier.fillMaxWidth()) { Text("Stop") }

            Text("Status: $status")
            Text("Tip: use headphones the first time, so the AI doesn't hear itself.")
        }
    }
}
