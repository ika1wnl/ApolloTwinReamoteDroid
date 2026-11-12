package com.apollo.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

data class PluginParam(
    val index: Int,
    val name: String,
    val value: Float = 0.5f,
    val valueText: String = ""
)

data class ChannelState(
    val index: Int,
    val name: String = "CH ${index + 1}",
    val gainTapered: Float = 0.5f,
    val faderTapered: Float = 0.75f,
    val pad: Boolean = false,
    val phase: Boolean = false,
    val lowCut: Boolean = false,
    val phantom48V: Boolean = false,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val pluginName: String = "Neve 1073 Preamp & EQ",
    val unisonPluginParams: Map<Int, PluginParam> = defaultNeve1073Params()
)

fun defaultNeve1073Params(): Map<Int, PluginParam> = mapOf(
    0 to PluginParam(0, "Preamp Gain (Red Knob)", 0.5f, "-30 dB"),
    1 to PluginParam(1, "Output Trim (Level)", 0.75f, "0.0 dB"),
    2 to PluginParam(2, "High Shelf (12 kHz Gain)", 0.5f, "0.0 dB"),
    3 to PluginParam(3, "Mid Band Freq Selector", 0.4f, "1.6 kHz"),
    4 to PluginParam(4, "Mid Band Gain", 0.5f, "0.0 dB"),
    5 to PluginParam(5, "Low Shelf Freq Selector", 0.3f, "60 Hz"),
    6 to PluginParam(6, "Low Shelf Gain", 0.5f, "0.0 dB"),
    7 to PluginParam(7, "High Pass Filter (HPF)", 0.0f, "Off"),
    8 to PluginParam(8, "EQ In / Out", 1.0f, "In"),
    9 to PluginParam(9, "Mic / Line Impedance (Z)", 0.0f, "Hi-Z (1200 Ω)")
)

class UadTcpClient(private val scope: CoroutineScope) {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _statusMessage = MutableStateFlow("Nicht verbunden")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _channels = MutableStateFlow(
        listOf(
            ChannelState(0, "MIC / LINE 1"),
            ChannelState(1, "MIC / LINE 2")
        )
    )
    val channels: StateFlow<List<ChannelState>> = _channels

    fun connect(host: String, port: Int = 47100) {
        scope.launch(Dispatchers.IO) {
            try {
                disconnect()
                _statusMessage.value = "Verbinde mit $host:$port..."
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 3500)
                socket = s
                outputStream = s.getOutputStream()
                _connected.value = true
                _statusMessage.value = "Verbunden mit UAD Mixer Engine ($host)"

                // Subscribe to Apollo Twin inputs 0 and 1 + Unison Plugin Slot 0 (Neve 1073)
                for (ch in 0..1) {
                    sendRaw("get /devices/0/inputs/$ch")
                    sendRaw("subscribe /devices/0/inputs/$ch/FaderLevelTapered")
                    sendRaw("subscribe /devices/0/inputs/$ch/Mute")
                    sendRaw("subscribe /devices/0/inputs/$ch/Solo")
                    sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/GainTapered")
                    sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/Pad")
                    sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/Phase")
                    sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/LowCut")
                    sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/48V")

                    // Query & subscribe to Unison Preamp Plugin (Slot 0) parameters (0..13 for Neve 1073)
                    sendRaw("get /devices/0/inputs/$ch/preamps/0/plugins/0")
                    for (p in 0..13) {
                        sendRaw("get /devices/0/inputs/$ch/preamps/0/plugins/0/parameters/$p")
                        sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/plugins/0/parameters/$p/Value")
                        sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/plugins/0/parameters/$p/ValueText")
                        sendRaw("subscribe /devices/0/inputs/$ch/preamps/0/plugins/0/parameters/$p/Name")
                    }
                }

                listenLoop(s.getInputStream())
            } catch (e: Exception) {
                _connected.value = false
                _statusMessage.value = "Fehler: ${e.localizedMessage ?: "Verbindung fehlgeschlagen"}"
            }
        }
    }

    private fun listenLoop(inputStream: InputStream) {
        val buffer = ByteArray(4096)
        val sb = StringBuilder()
        try {
            while (_connected.value) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                for (i in 0 until read) {
                    val b = buffer[i]
                    if (b == 0.toByte()) {
                        val msg = sb.toString().trim()
                        sb.clear()
                        if (msg.isNotEmpty()) {
                            parseEngineMessage(msg)
                        }
                    } else {
                        sb.append(b.toInt().toChar())
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            _connected.value = false
            _statusMessage.value = "Verbindung getrennt"
        }
    }

    private fun parseEngineMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val path = json.optString("path", "")
            val data = json.opt("data") ?: return

            for (ch in 0..1) {
                val prefix = "/devices/0/inputs/$ch/"
                if (path.startsWith(prefix)) {
                    val subPath = path.removePrefix(prefix)
                    updateChannelState(ch, subPath, data)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun updateChannelState(ch: Int, subPath: String, data: Any) {
        val currentList = _channels.value.toMutableList()
        var state = currentList[ch]

        fun extractBool(v: Any): Boolean = when (v) {
            is Boolean -> v
            is JSONObject -> v.optJSONObject("properties")?.optJSONObject("value")?.optBoolean("value") ?: false
            else -> false
        }

        fun extractFloat(v: Any): Float = when (v) {
            is Number -> v.toFloat()
            is JSONObject -> v.optJSONObject("properties")?.optJSONObject("value")?.optDouble("value")?.toFloat() ?: 0.5f
            else -> 0.5f
        }

        fun extractString(v: Any): String = when (v) {
            is String -> v
            is JSONObject -> v.optJSONObject("properties")?.optJSONObject("value")?.optString("value") ?: ""
            else -> ""
        }

        when {
            subPath.startsWith("preamps/0/GainTapered") -> state = state.copy(gainTapered = extractFloat(data))
            subPath.startsWith("FaderLevelTapered") -> state = state.copy(faderTapered = extractFloat(data))
            subPath.startsWith("preamps/0/Pad") -> state = state.copy(pad = extractBool(data))
            subPath.startsWith("preamps/0/Phase") -> state = state.copy(phase = extractBool(data))
            subPath.startsWith("preamps/0/LowCut") -> state = state.copy(lowCut = extractBool(data))
            subPath.startsWith("preamps/0/48V") -> state = state.copy(phantom48V = extractBool(data))
            subPath.startsWith("Mute") -> state = state.copy(mute = extractBool(data))
            subPath.startsWith("Solo") -> state = state.copy(solo = extractBool(data))
            subPath.contains("plugins/0/parameters/") -> {
                val afterParams = subPath.substringAfter("plugins/0/parameters/")
                val paramIdx = afterParams.substringBefore("/").toIntOrNull()
                if (paramIdx != null) {
                    val map = state.unisonPluginParams.toMutableMap()
                    val existing = map[paramIdx] ?: PluginParam(paramIdx, "Param ${paramIdx + 1}")
                    when {
                        afterParams.endsWith("/Value") -> map[paramIdx] = existing.copy(value = extractFloat(data))
                        afterParams.endsWith("/ValueText") -> map[paramIdx] = existing.copy(valueText = extractString(data))
                        afterParams.endsWith("/Name") -> {
                            val n = extractString(data)
                            if (n.isNotEmpty()) map[paramIdx] = existing.copy(name = n)
                        }
                    }
                    state = state.copy(unisonPluginParams = map)
                }
            }
        }
        currentList[ch] = state
        _channels.value = currentList
    }

    fun sendRaw(command: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = (command + "\u0000").toByteArray(Charsets.UTF_8)
                outputStream?.write(bytes)
                outputStream?.flush()
            } catch (_: Exception) {
                _connected.value = false
            }
        }
    }

    fun setPreampGain(ch: Int, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        updateLocal(ch) { it.copy(gainTapered = clamped) }
        sendRaw("set /devices/0/inputs/$ch/preamps/0/GainTapered/value $clamped")
    }

    fun setFaderLevel(ch: Int, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        updateLocal(ch) { it.copy(faderTapered = clamped) }
        sendRaw("set /devices/0/inputs/$ch/FaderLevelTapered/value $clamped")
    }

    fun togglePreampOption(ch: Int, prop: String, newValue: Boolean) {
        updateLocal(ch) {
            when (prop) {
                "Pad" -> it.copy(pad = newValue)
                "Phase" -> it.copy(phase = newValue)
                "LowCut" -> it.copy(lowCut = newValue)
                "48V" -> it.copy(phantom48V = newValue)
                else -> it
            }
        }
        sendRaw("set /devices/0/inputs/$ch/preamps/0/$prop/value $newValue")
    }

    fun toggleChannelOption(ch: Int, prop: String, newValue: Boolean) {
        updateLocal(ch) {
            when (prop) {
                "Mute" -> it.copy(mute = newValue)
                "Solo" -> it.copy(solo = newValue)
                else -> it
            }
        }
        sendRaw("set /devices/0/inputs/$ch/$prop/value $newValue")
    }

    fun setUnisonPluginParam(ch: Int, paramIndex: Int, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        updateLocal(ch) {
            val map = it.unisonPluginParams.toMutableMap()
            val cur = map[paramIndex] ?: PluginParam(paramIndex, "Param ${paramIndex + 1}")
            map[paramIndex] = cur.copy(value = clamped)
            it.copy(unisonPluginParams = map)
        }
        sendRaw("set /devices/0/inputs/$ch/preamps/0/plugins/0/parameters/$paramIndex/Value/value $clamped")
        sendRaw("set /devices/0/inputs/$ch/plugins/0/parameters/$paramIndex/Value/value $clamped")
    }

    private fun updateLocal(ch: Int, block: (ChannelState) -> ChannelState) {
        val list = _channels.value.toMutableList()
        list[ch] = block(list[ch])
        _channels.value = list
    }

    fun disconnect() {
        try {
            _connected.value = false
            socket?.close()
        } catch (_: Exception) {
        }
    }
}
