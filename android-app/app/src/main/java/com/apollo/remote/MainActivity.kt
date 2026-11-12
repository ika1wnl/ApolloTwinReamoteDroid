package com.apollo.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {
    private val scope = MainScope()
    private lateinit var uadClient: UadTcpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uadClient = UadTcpClient(scope)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD9822B),
                    background = Color(0xFF141619),
                    surface = Color(0xFF1E2229)
                )
            ) {
                ApolloRemoteScreen(uadClient)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uadClient.disconnect()
    }
}

@Composable
fun ApolloRemoteScreen(client: UadTcpClient) {
    val connected by client.connected.collectAsState()
    val status by client.statusMessage.collectAsState()
    val channels by client.channels.collectAsState()

    var ipAddress by remember { mutableStateOf("192.168.178.") }
    var selectedChannel by remember { mutableIntStateOf(0) }
    var ask48VConfirm by remember { mutableStateOf(false) }

    val channel = channels[selectedChannel]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141619))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "UAD APOLLO TWIN • NEVE 1073 REMOTE",
            color = Color(0xFFD9822B),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        // Connection Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2229)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("Windows-PC IP-Adresse") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (connected) client.disconnect() else client.connect(ipAddress.trim(), 47100)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (connected) Color(0xFFC93B3B) else Color(0xFFD9822B)
                        )
                    ) {
                        Text(if (connected) "Trennen" else "Verbinden")
                    }
                }
                Text(
                    text = status,
                    color = if (connected) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    fontSize = 12.sp
                )
            }
        }

        // Channel Selector (Input 1 / Input 2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            channels.forEachIndexed { idx, ch ->
                val isSelected = idx == selectedChannel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFFD9822B) else Color(0xFF282C34))
                        .clickable { selectedChannel = idx }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ch.name,
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. APOLLO HARDWARE UNISON PREAMP SECTION
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2229)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UNISON HARDWARE PREAMP",
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${(channel.gainTapered * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Slider(
                    value = channel.gainTapered,
                    onValueChange = { client.setPreampGain(selectedChannel, it) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE53935),
                        activeTrackColor = Color(0xFFE53935)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioToggleButton(
                        label = "+48V",
                        active = channel.phantom48V,
                        activeColor = Color(0xFFE53935),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!channel.phantom48V) ask48VConfirm = true
                        else client.togglePreampOption(selectedChannel, "48V", false)
                    }

                    StudioToggleButton(
                        label = "PAD",
                        active = channel.pad,
                        activeColor = Color(0xFFD9822B),
                        modifier = Modifier.weight(1f)
                    ) {
                        client.togglePreampOption(selectedChannel, "Pad", !channel.pad)
                    }

                    StudioToggleButton(
                        label = "LOW CUT",
                        active = channel.lowCut,
                        activeColor = Color(0xFFD9822B),
                        modifier = Modifier.weight(1f)
                    ) {
                        client.togglePreampOption(selectedChannel, "LowCut", !channel.lowCut)
                    }

                    StudioToggleButton(
                        label = "Ø PHASE",
                        active = channel.phase,
                        activeColor = Color(0xFF29B6F6),
                        modifier = Modifier.weight(1f)
                    ) {
                        client.togglePreampOption(selectedChannel, "Phase", !channel.phase)
                    }
                }
            }
        }

        // 2. DEDICATED NEVE 1073 UNISON STRIP (Red Gain, Output Trim, 3-Band EQ & HPF)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF232936)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF3B465E), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEVE 1073 PREAMP & EQ (UNISON)",
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "CLASS-A",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Quick switches for Neve 1073 EQ In/Out (Param 8) & Impedance Hi/Lo-Z (Param 9)
                val eqInParam = channel.unisonPluginParams[8]
                val impParam = channel.unisonPluginParams[9]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val eqActive = (eqInParam?.value ?: 1f) >= 0.5f
                    StudioToggleButton(
                        label = if (eqActive) "EQ: IN" else "EQ: BYPASS",
                        active = eqActive,
                        activeColor = Color(0xFF1E88E5),
                        modifier = Modifier.weight(1f)
                    ) {
                        client.setUnisonPluginParam(selectedChannel, 8, if (eqActive) 0f else 1f)
                    }

                    val loZActive = (impParam?.value ?: 0f) >= 0.5f
                    StudioToggleButton(
                        label = if (loZActive) "Z: LO (300 Ω)" else "Z: HI (1200 Ω)",
                        active = loZActive,
                        activeColor = Color(0xFF8E24AA),
                        modifier = Modifier.weight(1f)
                    ) {
                        client.setUnisonPluginParam(selectedChannel, 9, if (loZActive) 0f else 1f)
                    }
                }

                Divider(color = Color(0xFF37404F), thickness = 1.dp)

                // Render all Neve 1073 Sliders with real-time ValueText feedback from UAD Console
                val sliderOrder = listOf(0, 1, 2, 3, 4, 5, 6, 7)
                sliderOrder.forEach { paramIdx ->
                    val param = channel.unisonPluginParams[paramIdx]
                        ?: PluginParam(paramIdx, "Parameter ${paramIdx + 1}", 0.5f)
                    val isRedKnob = paramIdx == 0
                    val isBlueEq = paramIdx in 2..7

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = param.name,
                                color = when {
                                    isRedKnob -> Color(0xFFFF8A80)
                                    isBlueEq -> Color(0xFF90CAF9)
                                    else -> Color.White
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            val displayVal = if (param.valueText.isNotEmpty()) {
                                "${param.valueText} (${(param.value * 100).toInt()}%)"
                            } else {
                                "${(param.value * 100).toInt()}%"
                            }
                            Text(text = displayVal, color = Color.LightGray, fontSize = 12.sp)
                        }
                        Slider(
                            value = param.value,
                            onValueChange = { client.setUnisonPluginParam(selectedChannel, paramIdx, it) },
                            colors = SliderDefaults.colors(
                                thumbColor = when {
                                    isRedKnob -> Color(0xFFE53935)
                                    isBlueEq -> Color(0xFF42A5F5)
                                    else -> Color(0xFFFFB300)
                                },
                                activeTrackColor = when {
                                    isRedKnob -> Color(0xFFE53935)
                                    isBlueEq -> Color(0xFF42A5F5)
                                    else -> Color(0xFFFFB300)
                                }
                            )
                        )
                    }
                }
            }
        }

        // 3. CONSOLE CHANNEL FADER, MUTE & SOLO
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2229)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CONSOLE MONITOR FADER",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${(channel.faderTapered * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = channel.faderTapered,
                    onValueChange = { client.setFaderLevel(selectedChannel, it) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioToggleButton(
                        label = "MUTE",
                        active = channel.mute,
                        activeColor = Color(0xFFE53935),
                        modifier = Modifier.weight(1f)
                    ) {
                        client.toggleChannelOption(selectedChannel, "Mute", !channel.mute)
                    }

                    StudioToggleButton(
                        label = "SOLO",
                        active = channel.solo,
                        activeColor = Color(0xFFFFEB3B),
                        modifier = Modifier.weight(1f)
                    ) {
                        client.toggleChannelOption(selectedChannel, "Solo", !channel.solo)
                    }
                }
            }
        }
    }

    if (ask48VConfirm) {
        AlertDialog(
            onDismissRequest = { ask48VConfirm = false },
            title = { Text("+48V Phantomspeisung aktivieren?") },
            text = { Text("Bitte stelle sicher, dass kein empfindliches Bändchenmikrofon (Ribbon Mic) angeschlossen ist.") },
            confirmButton = {
                Button(
                    onClick = {
                        client.togglePreampOption(selectedChannel, "48V", true)
                        ask48VConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("48V Einschalten")
                }
            },
            dismissButton = {
                TextButton(onClick = { ask48VConfirm = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun StudioToggleButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) activeColor else Color(0xFF2C3038))
            .border(1.dp, if (active) activeColor else Color(0xFF424752), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active && activeColor == Color(0xFFFFEB3B)) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
