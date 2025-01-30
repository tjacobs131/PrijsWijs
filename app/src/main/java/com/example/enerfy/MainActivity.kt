package com.example.enerfy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.enerfy.ui.theme.EnerfyTheme
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        println("Showing first notification")

        AlarmScheduler.scheduleHourlyAlarm(this)

        // In MainActivity's onCreate()
        Intent(this, EnergyNotificationService::class.java).also { intent ->
            startService(intent)
        }

        setContent {
            EnerfyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                }
            }
        }
    }

    private fun scheduleAlarm() {



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

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier, onShowNotificationClick: () -> Unit) {
        Column(
            modifier = modifier
        ) {
            Button(
                onClick = onShowNotificationClick,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Show Notification")
            }
        }
    }
}