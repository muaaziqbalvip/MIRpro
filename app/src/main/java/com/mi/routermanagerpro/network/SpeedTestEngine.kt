package com.mi.routermanagerpro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.roundToInt

data class SpeedTestResult(
    val pingMs: Int,
    val downloadMbps: Double,
    val uploadMbps: Double
)

object SpeedTestEngine {

    // Public test endpoints commonly used for lightweight speed testing
    private const val DOWNLOAD_TEST_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
    private const val UPLOAD_TEST_URL = "https://speed.cloudflare.com/__up"
    private const val PING_HOST = "speed.cloudflare.com"
    private const val PING_PORT = 443

    suspend fun measurePing(): Int = withContext(Dispatchers.IO) {
        try {
            val samples = mutableListOf<Long>()
            repeat(4) {
                val start = System.nanoTime()
                val socket = Socket()
                socket.connect(InetSocketAddress(PING_HOST, PING_PORT), 2000)
                socket.close()
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                samples.add(elapsedMs)
            }
            samples.sorted()[samples.size / 2].toInt()
        } catch (e: Exception) {
            -1
        }
    }

    suspend fun measureDownload(onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        try {
            val url = URL(DOWNLOAD_TEST_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.connect()

            val startTime = System.nanoTime()
            val input = conn.inputStream
            val buffer = ByteArray(65536)
            var totalBytes = 0L
            var lastReportTime = startTime

            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                totalBytes += read

                val now = System.nanoTime()
                if (now - lastReportTime > 200_000_000) { // report every 200ms
                    val elapsedSec = (now - startTime) / 1_000_000_000.0
                    if (elapsedSec > 0) {
                        val mbps = (totalBytes * 8.0 / 1_000_000.0) / elapsedSec
                        onProgress(mbps)
                    }
                    lastReportTime = now
                }
            }
            input.close()
            conn.disconnect()

            val totalElapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0
            if (totalElapsedSec <= 0) return@withContext 0.0
            (totalBytes * 8.0 / 1_000_000.0) / totalElapsedSec
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun measureUpload(onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        try {
            val payloadSize = 8_000_000 // 8MB
            val chunk = ByteArray(65536) { 1 }

            val url = URL(UPLOAD_TEST_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(payloadSize)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.connect()

            val out: OutputStream = conn.outputStream
            val startTime = System.nanoTime()
            var sent = 0L
            var lastReportTime = startTime

            while (sent < payloadSize) {
                val toWrite = minOf(chunk.size, (payloadSize - sent).toInt())
                out.write(chunk, 0, toWrite)
                sent += toWrite

                val now = System.nanoTime()
                if (now - lastReportTime > 200_000_000) {
                    val elapsedSec = (now - startTime) / 1_000_000_000.0
                    if (elapsedSec > 0) {
                        val mbps = (sent * 8.0 / 1_000_000.0) / elapsedSec
                        onProgress(mbps)
                    }
                    lastReportTime = now
                }
            }
            out.flush()
            out.close()
            conn.responseCode // triggers the request to complete
            conn.disconnect()

            val totalElapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0
            if (totalElapsedSec <= 0) return@withContext 0.0
            (sent * 8.0 / 1_000_000.0) / totalElapsedSec
        } catch (e: Exception) {
            0.0
        }
    }

    fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
