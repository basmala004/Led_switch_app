package com.example.espcontroller

import com.example.led_switch.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControllerUI() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerUI() {

    var red by remember { mutableStateOf(false) }
    var green by remember { mutableStateOf(false) }
    var blue by remember { mutableStateOf(false) }

    // Switch availability
    var canSend by remember { mutableStateOf(true) }

    // Countdown timer for ThingSpeak limit
    var countdown by remember { mutableStateOf(0) }

    // Fetch initial states
    LaunchedEffect(Unit) {
        val (r, g, b) = fetchLedStates()
        red = r
        green = g
        blue = b
    }

    fun startCountdown() {
        canSend = false
        countdown = 15  // ThingSpeak free limit = 15 seconds

        CoroutineScope(Dispatchers.Main).launch {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            canSend = true
        }
    }

    fun sendToServer() {
        val apiKey = "6O2EAN3G693D3FQB"
        val url =
            "https://api.thingspeak.com/update?api_key=$apiKey" +
                    "&field1=${if (red) 1 else 0}" +
                    "&field2=${if (green) 1 else 0}" +
                    "&field3=${if (blue) 1 else 0}"

        CoroutineScope(Dispatchers.IO).launch {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().readText()
            connection.disconnect()
        }

        startCountdown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("CONTROLLERS", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Image(
                painterResource(id = R.drawable.logo1),
                contentDescription = "Logo",
                modifier = Modifier.size(350.dp)
            )

            // ====================== NEW NOTE ===========================
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (canSend)
                    "You can send a command now."
                else
                    "Please wait $countdown seconds before the next command…",
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))
            // ============================================================

            LEDControl("Red", red, canSend) {
                red = it
                sendToServer()
            }
            LEDControl("Yellow", blue, canSend) {
                blue = it
                sendToServer()
            }
            LEDControl("Green", green, canSend) {
                green = it
                sendToServer()
            }
        }
    }
}

@Composable
fun LEDControl(label: String, state: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .padding(15.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(5.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(label, fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(
                    text = if (state) "ON" else "OFF",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 10.dp)
                )

                Switch(
                    modifier = Modifier,
                    checked = state,
                    enabled = enabled,        // << DISABLE SWITCH HERE
                    onCheckedChange = {
                        if (enabled) onChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = when (label) {
                            "Red" -> Color(0xFFFFA5AC)
                            "Green" -> Color(0xFFC8E6C9)
                            "Yellow" -> Color(0xFFFFF5C2)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                )
            }
        }
    }
}

suspend fun fetchLedStates(): Triple<Boolean, Boolean, Boolean> {
    val channelID = "3188417"
    val readAPI = "URNIIJK97DPHUJ5E"

    return withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.thingspeak.com/channels/$channelID/feeds/last.json?api_key=$readAPI"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = org.json.JSONObject(response)

            val r = json.optString("field1", "0") == "1"
            val g = json.optString("field2", "0") == "1"
            val b = json.optString("field3", "0") == "1"

            Triple(r, g, b)
        } catch (e: Exception) {
            Triple(false, false, false)
        }
    }
}
