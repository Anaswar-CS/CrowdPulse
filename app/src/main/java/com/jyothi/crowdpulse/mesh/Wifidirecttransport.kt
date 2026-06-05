package com.jyothi.crowdpulse.mesh

import android.util.Log
import java.net.ServerSocket
import java.net.Socket

class WifiDirectTransport {

    private val tag = "WifiDirectTransport"
    private val PORT = 8765
    private var serverSocket: ServerSocket? = null

    fun startServer(onPacketReceived: (ByteArray) -> Unit) {
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(tag, "Server listening on port $PORT")
                while (true) {
                    val client = serverSocket!!.accept()
                    Thread {
                        try {
                            val bytes = client.inputStream.readBytes()
                            if (bytes.isNotEmpty()) onPacketReceived(bytes)
                        } catch (e: Exception) {
                            Log.e(tag, "Read error: ${e.message}")
                        } finally { client.close() }
                    }.start()
                }
            } catch (e: Exception) {
                Log.e(tag, "Server error: ${e.message}")
            }
        }.start()
    }

    fun send(peerIp: String, data: ByteArray) {
        Thread {
            try {
                Socket(peerIp, PORT).use { socket ->
                    socket.outputStream.write(data)
                    socket.outputStream.flush()
                    Log.d(tag, "Sent ${data.size}B to $peerIp")
                }
            } catch (e: Exception) {
                Log.e(tag, "Send failed to $peerIp: ${e.message}")
            }
        }.start()
    }

    fun stop() {
        try { serverSocket?.close() } catch (e: Exception) { }
    }
}