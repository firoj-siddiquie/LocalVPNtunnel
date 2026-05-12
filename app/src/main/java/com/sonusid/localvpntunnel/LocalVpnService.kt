package com.sonusid.localvpntunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LocalVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var executorService: ExecutorService? = null

    companion object {
        private const val TAG = "LocalVpnService"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sonusid.localvpntunnel.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("LocalVPNTunnel")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)

        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopSelf()
            return
        }

        if (vpnInterface != null) {
            startForeground(NOTIFICATION_ID, createNotification())
            runVpnLoop()
        }
    }

    private fun runVpnLoop() {
        executorService = Executors.newSingleThreadExecutor()
        executorService?.execute {
            val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
            val packet = ByteBuffer.allocate(32767)

            try {
                while (!Thread.interrupted()) {
                    val length = inputStream.read(packet.array())
                    if (length > 0) {
                        // Logic for TCP forwarding to sandbox goes here.
                        // You would typically parse the IP packet, then the TCP segment,
                        // and use a Socket to forward the payload.
                        Log.d(TAG, "Received packet: $length bytes")
                        
                        // For a real implementation, consider using a library like
                        // 'tun2socks' or a Java-based TCP/IP stack.
                    }
                    packet.clear()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error in VPN loop", e)
            } finally {
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        executorService?.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, LocalVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN Tunnel Active")
            .setContentText("Forwarding TCP traffic to sandbox")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
