package com.example.espcontroller

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
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.example.led_switch.R



class MainActivity : ComponentActivity() {

    private lateinit var mqttClient: MqttClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Connect to MQTT broker
        connectMqtt()

        setContent { ControllerUI(mqttClient) }
    }

    private fun connectMqtt() {
        val brokerUrl = "tcp://broker.hivemq.com:1883" // Replace with your broker URL
        val clientId = MqttClient.generateClientId()
        mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            userName = "esp32" // if needed
            password = "123456789sS".toCharArray() // if needed
        }

        mqttClient.connect(options)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerUI(mqttClient: MqttClient) {

    var red by remember { mutableStateOf(false) }
    var green by remember { mutableStateOf(false) }
    var blue by remember { mutableStateOf(false) }

    // Switch availability (optional, since MQTT has no rate limit like ThingSpeak)
    val canSend = true

    // Subscribe to LED topics to get initial states
    LaunchedEffect(Unit) {
        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {}
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message.toString()
                when (topic) {
                    "esp32/red" -> red = payload == "1"
                    "esp32/green" -> green = payload == "1"
                    "esp32/blue" -> blue = payload == "1"
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        mqttClient.subscribe("esp32/red")
        mqttClient.subscribe("esp32/green")
        mqttClient.subscribe("esp32/blue")
    }

    fun sendMqtt(topic: String, state: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val message = MqttMessage((if (state) "1" else "0").toByteArray())
            mqttClient.publish(topic, message)
        }
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
                painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(350.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            LEDControl("Red", red, canSend) {
                red = it
                sendMqtt("esp32/red", it)
            }
            LEDControl("Yellow", blue, canSend) {
                blue = it
                sendMqtt("esp32/blue", it)
            }
            LEDControl("Green", green, canSend) {
                green = it
                sendMqtt("esp32/green", it)
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
                    checked = state,
                    enabled = enabled,
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
