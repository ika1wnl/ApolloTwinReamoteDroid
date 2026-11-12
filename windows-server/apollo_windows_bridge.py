#!/usr/bin/env python3
"""
Apollo Twin Windows WLAN Bridge & Web-Remote
--------------------------------------------
Die 'UA Mixer Engine' von Universal Audio lauscht unter Windows standardmaessig
nur lokal auf 127.0.0.1:4710. Dieses Skript macht zwei Dinge:
1. TCP-Bridge (Port 47100 -> 127.0.0.1:4710) fuer die native Android-App.
2. Eingebauter HTTP/REST-Server (Port 8080), falls du ohne APK-Installation
   direkt im Android-Browser (oder als PWA-App) starten willst.
"""

import json
import socket
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

UAD_HOST = "127.0.0.1"
UAD_PORT = 4710
BRIDGE_TCP_PORT = 47100
WEB_HTTP_PORT = 8080


def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


# --- 1. RAW TCP PROXY FOR THE NATIVE ANDROID APP (47100 -> 127.0.0.1:4710) ---
def pipe_socket(src, dst):
    try:
        while True:
            data = src.recv(4096)
            if not data:
                break
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try:
            src.close()
        except Exception:
            pass
        try:
            dst.close()
        except Exception:
            pass


def handle_android_tcp_client(client_sock, addr):
    print(f"[Android-App verbunden] {addr[0]}:{addr[1]}")
    try:
        uad_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        uad_sock.connect((UAD_HOST, UAD_PORT))
        threading.Thread(target=pipe_socket, args=(client_sock, uad_sock), daemon=True).start()
        threading.Thread(target=pipe_socket, args=(uad_sock, client_sock), daemon=True).start()
    except Exception as e:
        print(f"[Warnung] Konnte UA Mixer Engine auf 127.0.0.1:4710 nicht erreichen: {e}")
        client_sock.close()


def start_tcp_bridge():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", BRIDGE_TCP_PORT))
    server.listen(5)
    while True:
        client_sock, addr = server.accept()
        threading.Thread(target=handle_android_tcp_client, args=(client_sock, addr), daemon=True).start()


# --- 2. PERSISTENT UAD CLIENT FOR THE INSTANT MOBILE WEB-UI ---
class UadWebClient:
    def __init__(self):
        self.lock = threading.Lock()
        self.sock = None

    def send_cmd(self, cmd: str):
        with self.lock:
            try:
                if self.sock is None:
                    self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    self.sock.settimeout(1.5)
                    self.sock.connect((UAD_HOST, UAD_PORT))
                self.sock.sendall((cmd + "\x00").encode("utf-8"))
                return True, "OK"
            except Exception as e:
                self.sock = None
                return False, str(e)


uad_web_client = UadWebClient()

WEB_HTML = """<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Apollo Twin Unison Remote</title>
<style>
  :root { --bg: #141619; --card: #1F2227; --accent: #D9822B; --orange: #FF9800; --red: #E53935; --blue: #29B6F6; }
  * { box-sizing: border-box; font-family: system-ui, -apple-system, sans-serif; user-select: none; }
  body { margin: 0; padding: 14px; background: var(--bg); color: #fff; }
  h1 { font-size: 18px; color: var(--accent); margin: 0 0 12px 0; text-align: center; letter-spacing: 1px; }
  .tabs { display: flex; gap: 10px; margin-bottom: 14px; }
  .tab { flex: 1; padding: 12px; text-align: center; background: #282C34; border-radius: 8px; font-weight: 700; cursor: pointer; }
  .tab.active { background: var(--accent); color: #000; }
  .card { background: var(--card); border-radius: 12px; padding: 15px; margin-bottom: 14px; }
  .card-title { font-size: 14px; font-weight: 700; color: var(--orange); margin-bottom: 12px; display: flex; justify-content: space-between; }
  input[type=range] { width: 100%; height: 34px; accent-color: var(--orange); margin: 6px 0 12px 0; }
  .btn-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
  .btn { padding: 13px 6px; text-align: center; background: #2C3038; border: 1px solid #424752; border-radius: 8px; font-weight: 700; font-size: 13px; cursor: pointer; }
  .btn.active-orange { background: var(--accent); border-color: var(--accent); }
  .btn.active-red { background: var(--red); border-color: var(--red); }
  .btn.active-blue { background: var(--blue); border-color: var(--blue); }
  .status { text-align: center; font-size: 12px; color: #9E9E9E; margin-top: 6px; }
</style>
</head>
<body>
  <h1>UAD APOLLO TWIN REMOTE</h1>
  <div class="tabs">
    <div class="tab active" id="tab0" onclick="selectChannel(0)">MIC / LINE 1</div>
    <div class="tab" id="tab1" onclick="selectChannel(1)">MIC / LINE 2</div>
  </div>

  <div class="card">
    <div class="card-title">
      <span>UNISON PREAMP GAIN</span>
      <span id="gainLabel">50%</span>
    </div>
    <input type="range" min="0" max="100" value="50" id="gainSlider" oninput="onGain(this.value)">
    <div class="btn-row">
      <div class="btn" id="btn48V" onclick="togglePreamp('48V')">+48V</div>
      <div class="btn" id="btnPad" onclick="togglePreamp('Pad')">PAD</div>
      <div class="btn" id="btnLowCut" onclick="togglePreamp('LowCut')">LOW CUT</div>
      <div class="btn" id="btnPhase" onclick="togglePreamp('Phase')">Ø PHASE</div>
    </div>
  </div>

  <div class="card" style="background:#232936; border:1px solid #3B465E;">
    <div class="card-title" style="color:#64B5F6;">
      <span>NEVE 1073 PREAMP & EQ (UNISON)</span>
      <span style="color:#E53935;">CLASS-A</span>
    </div>
    <div class="btn-row" style="grid-template-columns: repeat(2, 1fr); margin-bottom:12px;">
      <div class="btn active-blue" id="btnEqIn" onclick="togglePluginSwitch(8, 'btnEqIn', 'EQ: IN', 'EQ: BYPASS')">EQ: IN</div>
      <div class="btn" id="btnImp" onclick="togglePluginSwitch(9, 'btnImp', 'Z: LO (300 Ω)', 'Z: HI (1200 Ω)')">Z: HI (1200 Ω)</div>
    </div>
    <label style="font-size:12px;color:#FF8A80;font-weight:bold;">Preamp Gain (Red Knob)</label>
    <input type="range" min="0" max="100" value="50" oninput="onPluginParam(0, this.value)">
    <label style="font-size:12px;color:#FFE082;font-weight:bold;">Output Trim (Level)</label>
    <input type="range" min="0" max="100" value="75" oninput="onPluginParam(1, this.value)">
    <label style="font-size:12px;color:#90CAF9;">High Shelf (12 kHz Gain)</label>
    <input type="range" min="0" max="100" value="50" oninput="onPluginParam(2, this.value)">
    <label style="font-size:12px;color:#90CAF9;">Mid Band Frequenz (0.36 - 7.2 kHz)</label>
    <input type="range" min="0" max="100" value="40" oninput="onPluginParam(3, this.value)">
    <label style="font-size:12px;color:#90CAF9;">Mid Band Gain (-18 bis +18 dB)</label>
    <input type="range" min="0" max="100" value="50" oninput="onPluginParam(4, this.value)">
    <label style="font-size:12px;color:#90CAF9;">Low Shelf Frequenz (35 / 60 / 110 / 220 Hz)</label>
    <input type="range" min="0" max="100" value="30" oninput="onPluginParam(5, this.value)">
    <label style="font-size:12px;color:#90CAF9;">Low Shelf Gain (-16 bis +16 dB)</label>
    <input type="range" min="0" max="100" value="50" oninput="onPluginParam(6, this.value)">
    <label style="font-size:12px;color:#90CAF9;">High Pass Filter (Off / 50 / 80 / 160 / 300 Hz)</label>
    <input type="range" min="0" max="100" value="0" oninput="onPluginParam(7, this.value)">
  </div>

  <div class="card">
    <div class="card-title" style="color:#fff;">
      <span>CONSOLE MONITOR FADER</span>
      <span id="faderLabel">75%</span>
    </div>
    <input type="range" min="0" max="100" value="75" id="faderSlider" oninput="onFader(this.value)">
    <div class="btn-row" style="grid-template-columns: repeat(2, 1fr);">
      <div class="btn" id="btnMute" onclick="toggleChan('Mute')">MUTE</div>
      <div class="btn" id="btnSolo" onclick="toggleChan('Solo')">SOLO</div>
    </div>
  </div>

  <div class="status" id="statusText">Bereit</div>

<script>
let ch = 0;
const state = [
  { gain: 50, fader: 75, '48V': false, Pad: false, LowCut: false, Phase: false, Mute: false, Solo: false },
  { gain: 50, fader: 75, '48V': false, Pad: false, LowCut: false, Phase: false, Mute: false, Solo: false }
];

function sendUad(cmd) {
  fetch('/api?cmd=' + encodeURIComponent(cmd))
    .then(r => r.json())
    .then(d => {
      document.getElementById('statusText').innerText = d.ok ? 'Gesendet: ' + cmd : 'Fehler: ' + d.msg;
    });
}

function selectChannel(c) {
  ch = c;
  document.getElementById('tab0').className = c === 0 ? 'tab active' : 'tab';
  document.getElementById('tab1').className = c === 1 ? 'tab active' : 'tab';
  render();
}

function onGain(val) {
  state[ch].gain = val;
  document.getElementById('gainLabel').innerText = val + '%';
  sendUad(`set /devices/0/inputs/${ch}/preamps/0/GainTapered/value ${(val/100).toFixed(3)}`);
}

function onFader(val) {
  state[ch].fader = val;
  document.getElementById('faderLabel').innerText = val + '%';
  sendUad(`set /devices/0/inputs/${ch}/FaderLevelTapered/value ${(val/100).toFixed(3)}`);
}

const pSwitch = {8: true, 9: false};
function togglePluginSwitch(idx, btnId, onTxt, offTxt) {
  pSwitch[idx] = !pSwitch[idx];
  const el = document.getElementById(btnId);
  el.innerText = pSwitch[idx] ? onTxt : offTxt;
  el.className = pSwitch[idx] ? 'btn active-blue' : 'btn';
  onPluginParam(idx, pSwitch[idx] ? 100 : 0);
}
function onPluginParam(idx, val) {
  const f = (val/100).toFixed(3);
  sendUad(`set /devices/0/inputs/${ch}/preamps/0/plugins/0/parameters/${idx}/Value/value ${f}`);
  sendUad(`set /devices/0/inputs/${ch}/plugins/0/parameters/${idx}/Value/value ${f}`);
}

function togglePreamp(prop) {
  if (prop === '48V' && !state[ch]['48V']) {
    if (!confirm('+48V Phantomspeisung wirklich einschalten?')) return;
  }
  state[ch][prop] = !state[ch][prop];
  render();
  sendUad(`set /devices/0/inputs/${ch}/preamps/0/${prop}/value ${state[ch][prop]}`);
}

function toggleChan(prop) {
  state[ch][prop] = !state[ch][prop];
  render();
  sendUad(`set /devices/0/inputs/${ch}/${prop}/value ${state[ch][prop]}`);
}

function render() {
  const s = state[ch];
  document.getElementById('gainSlider').value = s.gain;
  document.getElementById('gainLabel').innerText = s.gain + '%';
  document.getElementById('faderSlider').value = s.fader;
  document.getElementById('faderLabel').innerText = s.fader + '%';
  document.getElementById('btn48V').className = s['48V'] ? 'btn active-red' : 'btn';
  document.getElementById('btnPad').className = s.Pad ? 'btn active-orange' : 'btn';
  document.getElementById('btnLowCut').className = s.LowCut ? 'btn active-orange' : 'btn';
  document.getElementById('btnPhase').className = s.Phase ? 'btn active-blue' : 'btn';
  document.getElementById('btnMute').className = s.Mute ? 'btn active-red' : 'btn';
  document.getElementById('btnSolo').className = s.Solo ? 'btn active-orange' : 'btn';
}
</script>
</body>
</html>
"""


class WebHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api":
            qs = parse_qs(parsed.query)
            cmd = qs.get("cmd", [""])[0]
            ok, msg = uad_web_client.send_cmd(cmd)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"ok": ok, "msg": msg}).encode("utf-8"))
        else:
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(WEB_HTML.encode("utf-8"))


if __name__ == "__main__":
    ip = get_local_ip()
    print("=" * 62)
    print("  UAD APOLLO TWIN - WINDOWS WLAN BRIDGE GESTARTET")
    print("=" * 62)
    print(f"  Deine Windows-IP im WLAN : {ip}")
    print(f"  1) Native Android-App    : Verbinde auf IP {ip} (Port {BRIDGE_TCP_PORT})")
    print(f"  2) Direkt im Handy-Browser: http://{ip}:{WEB_HTTP_PORT}")
    print("=" * 62)
    print("  Stelle sicher, dass die UAD Console (UA Mixer Engine) laeuft.")
    print("  Druecke STRG+C zum Beenden.\n")

    threading.Thread(target=start_tcp_bridge, daemon=True).start()
    server = HTTPServer(("0.0.0.0", WEB_HTTP_PORT), WebHandler)
    server.serve_forever()
