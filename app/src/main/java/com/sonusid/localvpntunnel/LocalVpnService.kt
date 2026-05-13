package com.sonusid.localvpntunnel

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LocalVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4)
    private var isRunning = false
    
    // Tracks active TCP sessions: Key is "SourcePort:DestIP:DestPort"
    private val tcpSessions = ConcurrentHashMap<String, SocketChannel>()

    companion object {
        const val ACTION_STOP = "com.sonusid.localvpntunnel.STOP"
        private const val TAG = "LocalVpnService"
        private const val MTU = 32767 // Increased MTU for better throughput
        private const val GATEWAY_IP = "10.0.0.2"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            establishVpn()
            isRunning = true
            executorService.execute { runVpnLoop() }
        }
        return START_STICKY
    }

    private fun establishVpn() {
        val builder = Builder()
        builder.setMtu(MTU)
            .addAddress("10.0.0.1", 24)
            // ONLY route the sandbox subnet to keep the rest of the internet fast!
            .addRoute("10.0.0.0", 24) 
            .addDnsServer("8.8.8.8")
            .setSession("TunnelCraft")
            .setBlocking(true)

        vpnInterface = builder.establish()
        Log.i(TAG, "VPN established. Split tunneling active for 10.0.0.0/24")
    }

    private fun runVpnLoop() {
        val input = FileInputStream(vpnInterface?.fileDescriptor)
        val output = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(MTU)

        try {
            while (isRunning) {
                val readLength = input.read(buffer.array())
                if (readLength > 0) {
                    buffer.limit(readLength)
                    // Process packet in a separate thread to keep the TUN interface clear
                    val packetCopy = ByteBuffer.allocate(readLength)
                    packetCopy.put(buffer.array(), 0, readLength)
                    packetCopy.flip()
                    
                    executorService.execute { 
                        processPacket(packetCopy, output) 
                    }
                    buffer.clear()
                }
            }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "VPN Loop Error", e)
        } finally {
            stopVpn()
        }
    }

    private fun processPacket(buffer: ByteBuffer, output: FileOutputStream) {
        val protocol = buffer.get(9).toInt() and 0xFF
        val destIp = getIpAddress(buffer, 16)

        if (protocol == 6) { // TCP
            handleTcpPacket(buffer, destIp, output)
        } else {
            // Log.v(TAG, "Dropping non-TCP packet to $destIp")
        }
    }

    private fun handleTcpPacket(buffer: ByteBuffer, destIp: String, output: FileOutputStream) {
        val sourcePort = getPort(buffer, 20)
        val destPort = getPort(buffer, 22)
        val sessionKey = "$sourcePort:$destIp:$destPort"

        // Check if we have an active relay for this session
        if (!tcpSessions.containsKey(sessionKey)) {
            initiateRelay(sessionKey, destIp, destPort)
        }
        
        // In a real implementation, we would extract the TCP payload here 
        // and send it through the SocketChannel. For the "Expressive" demo,
        // we log the interception and 'touch' the gateway.
        Log.d(TAG, "Relaying $sessionKey to Sandbox Gateway ($GATEWAY_IP)")
    }

    private fun initiateRelay(key: String, destIp: String, destPort: Int) {
        try {
            val tunnel = SocketChannel.open()
            // CRITICAL: Protect the socket so it doesn't loop back into the VPN
            protect(tunnel.socket()) 
            
            tunnel.configureBlocking(false)
            // Forwarding all traffic to the sandbox gateway
            tunnel.connect(InetSocketAddress(GATEWAY_IP, 8080)) 
            
            tcpSessions[key] = tunnel
            Log.i(TAG, "Started relay for $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start relay for $destIp", e)
        }
    }

    private fun getIpAddress(buffer: ByteBuffer, offset: Int): String {
        return "${buffer.get(offset).toInt() and 0xFF}.${buffer.get(offset + 1).toInt() and 0xFF}." +
                "${buffer.get(offset + 2).toInt() and 0xFF}.${buffer.get(offset + 3).toInt() and 0xFF}"
    }

    private fun getPort(buffer: ByteBuffer, offset: Int): Int {
        return ((buffer.get(offset).toInt() and 0xFF) shl 8) or (buffer.get(offset + 1).toInt() and 0xFF)
    }

    private fun stopVpn() {
        isRunning = false
        tcpSessions.values.forEach { try { it.close() } catch (e: Exception) {} }
        tcpSessions.clear()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        executorService.shutdownNow()
        super.onDestroy()
    }
}
