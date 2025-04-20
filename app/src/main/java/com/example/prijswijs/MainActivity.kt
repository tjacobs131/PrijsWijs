package com.example.prijswijs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.prijswijs.Alarms.AlarmScheduler
import com.example.prijswijs.Notifications.EnergyNotificationService
import com.example.prijswijs.Persistence.Persistence
import com.example.prijswijs.Model.Settings
import com.example.prijswijs.ui.theme.PrijsWijsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var vibrateEnabled by mutableStateOf(false)
    private var bedTimeHour by mutableIntStateOf(21)  // 21h
    private var wakeTimeHour by mutableIntStateOf(6)  // 6h

    private val scheduler: AlarmScheduler = AlarmScheduler()

    private val persistence = Persistence.getInstance()
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSettings()

        requestPermissions()

        Log.println(Log.INFO, "PrijsWijs", "Showing first notification")

        scheduler.scheduleHourlyAlarm(this)

        Intent(this, EnergyNotificationService::class.java).also { intent ->
            startService(intent)
        }

        setContent {
            PrijsWijsTheme {
                SettingsScreen(
                    vibrateEnabled = vibrateEnabled,
                    bedTimeHour = bedTimeHour,
                    wakeTimeHour = wakeTimeHour,
                    onVibrateChanged = { newValue ->
                        vibrateEnabled = newValue
                        saveSettings()
                    },
                    onBedTimeChanged = { newHour ->
                        bedTimeHour = newHour
                        saveSettings()
                        scheduler.scheduleHourlyAlarm(this)
                    },
                    onWakeTimeChanged = { newHour ->
                        wakeTimeHour = newHour
                        saveSettings()
                        scheduler.scheduleHourlyAlarm(this)
                    }
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
    ) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val backgroundColor = Color(0xFFfbe8da)
        val cardBackgroundColor = Color(0xFFe8bc8d)
        val textColorPrimary = Color(0xFF81889b)
        val buttonTextColor = Color.Black

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = backgroundColor
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = "Notification Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(color = textColorPrimary),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                //text = "Vibrate When Price\nBecomes High",
                                text = "Vibrate When\nPrice Goes Up",
                                style = MaterialTheme.typography.titleMedium.copy(color = textColorPrimary),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = vibrateEnabled,
                                onCheckedChange = { newValue ->
                                    onVibrateChanged(newValue)
                                    scope.launch {
                                        // Dismiss any currently showing Snackbar
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            message = if (newValue) "Vibrate turned on" else "Vibrate turned off",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color(0xFFfbe8da),
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    uncheckedTrackColor = textColorPrimary.copy(alpha = 0.4f),
                                    uncheckedThumbColor = textColorPrimary
                                )
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        StepperHourSelector(
                            label = "Bed Time Notification",
                            currentHour = bedTimeHour,
                            onHourSelected = { newHour ->
                                onBedTimeChanged(newHour)
                                scope.launch {
                                    // Dismiss any currently showing Snackbar
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = "Last notification of the day at ${String.format("%02d:00", newHour)}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            textColor = textColorPrimary,
                            backgroundColor = cardBackgroundColor
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        StepperHourSelector(
                            label = "Wake-Up Notification",
                            currentHour = wakeTimeHour,
                            onHourSelected = { newHour ->
                                onWakeTimeChanged(newHour)
                                scope.launch {
                                    // Dismiss any currently showing Snackbar
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = "Notifications turn back on at ${String.format("%02d:00", newHour)}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            textColor = textColorPrimary,
                            backgroundColor = cardBackgroundColor
                        )
                    }

                    Button(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .align(Alignment.End),
                        onClick = {
                            Intent(
                                this@MainActivity,
                                EnergyNotificationService::class.java
                            ).also { intent ->
                                startService(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cardBackgroundColor,
                            contentColor = buttonTextColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Refresh Notification")
                    }
                }
            }
        }
    }

    @Composable
    fun StepperHourSelector(
        label: String,
        currentHour: Int,
        onHourSelected: (Int) -> Unit,
        textColor: Color,
        backgroundColor: Color,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(color = textColor),
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val newHour = if (currentHour > 0) currentHour - 1 else 23
                    onHourSelected(newHour)
                }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Decrease Hour",
                        tint = textColor
                    )
                }

                Text(
                    text = String.format("%02d:00", currentHour),
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor, textAlign = TextAlign.Center),
                    modifier = Modifier.width(54.dp)
                )

                IconButton(onClick = {
                    val newHour = if (currentHour < 23) currentHour + 1 else 0
                    onHourSelected(newHour)
                }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Increase Hour",
                        tint = textColor
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        settings = persistence.loadSettings(this) ?: Settings() // Initialize settings if null
        vibrateEnabled = settings.vibrate
        bedTimeHour = settings.bedTime
        wakeTimeHour = settings.wakeUpTime
    }

    private fun saveSettings() {
        settings.vibrate = vibrateEnabled
        settings.bedTime = bedTimeHour
        settings.wakeUpTime = wakeTimeHour
        persistence.saveSettings(this, settings)
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
        )
        ActivityCompat.requestPermissions(this, permissions, allPermissions)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 ) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.println(Log.INFO, "PrijsWijs", "Permission granted")
                // Start notification service
                Intent(this, EnergyNotificationService::class.java).also { intent ->
                    startService(intent)
                }
            } else {
                Log.println(Log.INFO, "PrijsWijs", "Permission denied")
            }
        }
    }
}