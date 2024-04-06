package com.example.enerfy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.enerfy.ui.theme.EnerfyTheme
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()


        setContent {
            EnerfyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting(
                        name = "Android",
                        onShowNotificationClick = { startHourlyNotificationDisplay() }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        registerForActivityResult(ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                return@registerForActivityResult
            }
        }
    }

    private fun startHourlyNotificationDisplay(){
        val serviceIntent = Intent(this, EnergyNotificationService::class.java)
        startService(serviceIntent)
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier, onShowNotificationClick: () -> Unit) {
        Column(
            modifier = modifier
        ) {
            Text(
                text = "Hello $name!",
                modifier = Modifier.padding(16.dp)
            )
            Button(
                onClick = onShowNotificationClick,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Show Notification")
            }
        }
    }
}