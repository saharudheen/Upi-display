package com.saha.upidisplay

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * Sends each payment as a small message on the shop Wi-Fi (UDP port 4210).
 * The ESP32 display, or another phone in display mode, listens on the same port.
 * Message: {"t":"pay","id":"…","amt":250.0,"name":"Ravi","app":"GPay","ts":…}
 */
object DisplayLink {
    const val PORT = 4210

    fun send(ctx: Context, p: Payment) {
        val data = p.toJson().put("t", "pay").toString().toByteArray(Charsets.UTF_8)
        val ip = Prefs.displayIp(ctx)
        thread(name = "upi-send") {
            try {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    val targets = mutableListOf(InetAddress.getByName("255.255.255.255"))
                    if (ip.isNotBlank()) {
                        try { targets.add(InetAddress.getByName(ip)) } catch (_: Exception) {}
                    }
                    // Sent twice; the receiver ignores the copy.
                    repeat(2) {
                        targets.forEach { t -> s.send(DatagramPacket(data, data.size, t, PORT)) }
                        Thread.sleep(200)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    class Receiver(private val ctx: Context) {
        @Volatile private var running = false
        @Volatile private var socket: DatagramSocket? = null
        private var lock: WifiManager.MulticastLock? = null

        fun start() {
            if (running) return
            running = true
            try {
                val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                lock = wifi.createMulticastLock("upi-display").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (_: Exception) {
            }
            thread(name = "upi-receive") {
                try {
                    val s = DatagramSocket(null)
                    s.reuseAddress = true
                    s.broadcast = true
                    s.soTimeout = 1000
                    s.bind(InetSocketAddress(PORT))
                    socket = s
                    val buf = ByteArray(2048)
                    while (running) {
                        try {
                            val pk = DatagramPacket(buf, buf.size)
                            s.receive(pk)
                            val o = JSONObject(String(pk.data, 0, pk.length, Charsets.UTF_8))
                            if (o.optString("t") == "pay") {
                                PaymentStore.add(ctx, Payment.fromJson(o).copy(verified = false))
                            }
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Exception) {
                        }
                    }
                    s.close()
                } catch (_: Exception) {
                }
            }
        }

        fun stop() {
            running = false
            try { lock?.release() } catch (_: Exception) {}
            lock = null
        }
    }
}
