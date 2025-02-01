package com.example.enerfy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.enerfy.ui.theme.EnerfyTheme
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    var vibrateEnabled by mutableStateOf(true)
    var soundEnabled by mutableStateOf(false)
    var bedTimeHour by mutableStateOf(21)  // 21h
    var wakeTimeHour by mutableStateOf(6)  // 6h

    val persistence = Persistence(this)
    var settings: Settings? = null

    var showPopup by mutableStateOf(false)
    var popupMessage by mutableStateOf("")

    fun saveSettings(){
        val settings = Settings()
        settings.vibrate = vibrateEnabled
        settings.bedTime = bedTimeHour
        settings.wakeUpTime = wakeTimeHour
        persistence.saveSettings(settings)

        AlarmScheduler.scheduleHourlyAlarm(this, settings!!.bedTime, settings!!.wakeUpTime)
    }

    fun loadSettings(){
        settings = persistence.loadSettings()
        vibrateEnabled = settings!!.vibrate
        bedTimeHour = settings!!.bedTime
        wakeTimeHour = settings!!.wakeUpTime
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSettings()

        requestPermissions()

        println("Showing first notification")

        AlarmScheduler.scheduleHourlyAlarm(this, settings!!.bedTime, settings!!.wakeUpTime)

        // In MainActivity's onCreate()
        Intent(this, EnergyNotificationService::class.java).also { intent ->
            startService(intent)
        }

        // In MainActivity's setContent block:
        setContent {
            EnerfyTheme {
                SettingsScreen(
                    vibrateEnabled = vibrateEnabled,
                    bedTimeHour = bedTimeHour,
                    wakeTimeHour = wakeTimeHour,
                    onVibrateChanged = { newValue ->
                        vibrateEnabled = newValue
                        saveSettings()
                        popupMessage = if (newValue) "Vibrate turned on" else "Vibrate turned off"
                        showPopup = true
                    },
                    onBedTimeChanged = { newHour ->
                        bedTimeHour = newHour
                        saveSettings()
                        popupMessage = "Notifications will turn off at ${String.format("%02d:00", newHour)}"
                        showPopup = true
                    },
                    onWakeTimeChanged = { newHour ->
                        wakeTimeHour = newHour
                        saveSettings()
                        popupMessage = "Notifications will turn on at ${String.format("%02d:00", newHour)}"
                        showPopup = true
                    },
                    showPopup = showPopup,
                    popupMessage = popupMessage,
                    onPopupShown = { showPopup = false }
                )
            }
        }
    }

    @Composable
    fun SettingsScreen(
        vibrateEnabled: Boolean,
        bedTimeHour: Int,
        wakeTimeHour: Int,
        onVibrateChanged: (Boolean) -> Unit,
        onBedTimeChanged: (Int) -> Unit,
        onWakeTimeChanged: (Int) -> Unit,
        showPopup: Boolean,
        popupMessage: String,
        onPopupShown: () -> Unit
    ) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(showPopup) {
            if (showPopup) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = popupMessage,
                        duration = SnackbarDuration.Short
                    )
                    onPopupShown()
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    // Existing content remains the same
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Vibrate",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = onVibrateChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    HourSelector(
                        label = "Bed Time",
                        currentHour = bedTimeHour,
                        onHourSelected = onBedTimeChanged
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HourSelector(
                        label = "Wake Up Time",
                        currentHour = wakeTimeHour,
                        onHourSelected = onWakeTimeChanged
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HourSelector(
        label: String,
        currentHour: Int,
        onHourSelected: (Int) -> Unit
    ) {
        val hours = (0..23).toList()
        var expanded by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.width(120.dp)) {  // Set fixed width here
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = String.format("%02d:00", currentHour),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.5f)  // Adjust menu width
                    ) {
                        hours.forEach { hour ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        String.format("%02d:00", hour),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                onClick = {
                                    onHourSelected(hour)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val allPermissions = 101
        val permissions = arrayOf(
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND,
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
            android.Manifest.permission.WAKE_LOCK
        )
        ActivityCompat.requestPermissions(this, permissions, allPermissions)
    }
}