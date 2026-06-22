package com.elderguard.safe

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap

class ElderVpnService : VpnService() {

    override fun onCreate() {
        super.onCreate()
        DnsBlacklist.initialize(applicationContext)
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var dnsProxyThread: Thread? = null
    private var dnsProxySocket: DatagramSocket? = null

    // Map to store DNS query IDs and their original source addresses/ports
    private val dnsQueryMap = ConcurrentHashMap<Short, DnsQueryInfo>()

    data class DnsQueryInfo(
        val originalSourceIp: InetAddress,
        val originalSourcePort: Int,
        val originalDestinationIp: InetAddress
    )

    companion object {
        private const val TAG = "ElderVpnService"
        private const val VPN_ADDRESS = "10.0.0.2" // A fake IP address for the VPN interface
        private const val VPN_ROUTE = "0.0.0.0" // Intercept all traffic
        private const val DNS_SERVER_PRIMARY = "8.8.8.8" // Google DNS
        private const val DNS_SERVER_SECONDARY = "1.1.1.1" // Cloudflare DNS
        private const val DNS_PORT = 53
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            SettingsManager.isVpnEnabled = false
            stopVpn()
            return START_NOT_STICKY
        }

        if (!SettingsManager.isVpnEnabled) {
            Log.d(TAG, "VPN service is disabled by user settings.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (vpnInterface == null) {
            setupVpn()
        }
        return START_STICKY
    }

    private fun setupVpn() {
        val builder = Builder()
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_ROUTE, 0)
            .addDnsServer(VPN_ADDRESS) // Use our own VPN address as the DNS server
            .setSession(getString(R.string.app_name))

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setConfigureIntent(pendingIntent)

        try {
            vpnInterface = builder.establish()
            Log.d(TAG, "VPN Established")
            SettingsManager.isVpnEnabled = true
            startDnsProxyThread()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopSelf()
        }
    }

    private fun startVpnThread() {
        vpnThread = Thread { 
            Log.d(TAG, "VPN Thread Started")
            vpnInterface?.fileDescriptor?.let {
                val vpnInput = FileInputStream(it)
                val vpnOutput = FileOutputStream(it)
                val buffer = ByteBuffer.allocate(32767)

                while (vpnInterface != null && !Thread.interrupted()) {
                    try {
                        val length = vpnInput.read(buffer.array())
                        if (length > 0) {
                            buffer.limit(length)
                            buffer.position(0)

                            // Basic IP packet parsing to identify UDP and DNS
                            // Assuming IPv4 for simplicity
                            if (length >= 20) { // Minimum IPv4 header length
                                val protocol = buffer.get(9).toInt() and 0xFF
                                if (protocol == 17) { // UDP protocol
                                    val ipHeaderLength = (buffer.get(0).toInt() and 0x0F) * 4
                                    if (length >= ipHeaderLength + 8) { // Minimum UDP header length
                                        val udpSourcePort = buffer.getShort(ipHeaderLength).toInt() and 0xFFFF
                                        val udpDestPort = buffer.getShort(ipHeaderLength + 2).toInt() and 0xFFFF

                                        if (udpDestPort == DNS_PORT) { // DNS query
                                            val originalSourceIpBytes = ByteArray(4) { i -> buffer.get(12 + i) }
                                            val originalSourceIp = InetAddress.getByAddress(originalSourceIpBytes)
                                            val originalDestinationIpBytes = ByteArray(4) { i -> buffer.get(16 + i) }
                                            val originalDestinationIp = InetAddress.getByAddress(originalDestinationIpBytes)

                                            val dnsPacketBuffer = ByteBuffer.allocate(length - ipHeaderLength - 8)
                                            buffer.position(ipHeaderLength + 8)
                                            dnsPacketBuffer.put(buffer)
                                            dnsPacketBuffer.flip()

                                            val dnsPacket = DnsPacket(dnsPacketBuffer)
                                            if (dnsPacket.isQuery()) {
                                                val domain = dnsPacket.getQueryDomain()
                                                Log.d(TAG, "DNS Query for: $domain")

                                                if (domain != null && DnsBlacklist.isAdDomain(domain)) {
                                                    Log.d(TAG, "Blocking ad domain: $domain")
                                                    // Construct NXDOMAIN response and send back
                                                    val blockedResponsePayload = dnsPacket.createNxDomainResponse(dnsPacket.getId())
                                                    val fullBlockedPacket = PacketUtils.buildDnsResponsePacket(
                                                        originalDestinationIp, // VPN acts as destination for original query
                                                        originalSourceIp, // Original source is now destination
                                                        DNS_PORT, // Source port is DNS port
                                                        udpSourcePort, // Original source port is now destination port
                                                        blockedResponsePayload
                                                    )
                                                    vpnOutput.write(fullBlockedPacket.array(), 0, fullBlockedPacket.limit())
                                                } else {
                                                    // Forward to DNS proxy
                                                    dnsProxySocket?.let {
                                                        dnsQueryMap[dnsPacket.getId()] = DnsQueryInfo(originalSourceIp, udpSourcePort, originalDestinationIp)

                                                        val dnsQueryBytes = ByteArray(dnsPacketBuffer.remaining())
                                                        dnsPacketBuffer.get(dnsQueryBytes)
                                                        val packet = DatagramPacket(dnsQueryBytes, dnsQueryBytes.size, InetAddress.getByName(DNS_SERVER_PRIMARY), DNS_PORT)
                                                        it.send(packet)
                                                        Log.d(TAG, "Forwarding DNS query for $domain to external DNS")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in VPN thread", e)
                        break
                    }
                }
            }
            Log.d(TAG, "VPN Thread Stopped")
        }
        vpnThread?.start()
    }

    private fun startDnsProxyThread() {
        dnsProxyThread = Thread { 
            Log.d(TAG, "DNS Proxy Thread Started")
            try {
                dnsProxySocket = DatagramSocket(0) // Bind to an ephemeral port
                dnsProxySocket?.connect(InetAddress.getByName(DNS_SERVER_PRIMARY), DNS_PORT)
                dnsProxySocket?.let { socket ->
                    val responseBuffer = ByteArray(512)
                    while (!Thread.interrupted()) {
                        val packet = DatagramPacket(responseBuffer, responseBuffer.size)
                        socket.receive(packet)

                        val dnsResponseBuffer = ByteBuffer.wrap(packet.data, 0, packet.length)
                        val dnsPacket = DnsPacket(dnsResponseBuffer)
                        val queryId = dnsPacket.getId()

                        dnsQueryMap.remove(queryId)?.let { (originalSourceIp, originalSourcePort, originalDestinationIp) ->
                            Log.d(TAG, "Received DNS response for ID $queryId from external DNS. Sending back to $originalSourceIp:$originalSourcePort")
                            // Construct full IP/UDP/DNS response packet and write to vpnOutput
                            val fullResponsePacket = PacketUtils.buildDnsResponsePacket(
                                originalDestinationIp, // VPN acts as destination for original query
                                originalSourceIp, // Original source is now destination
                                DNS_PORT, // Source port is DNS port
                                originalSourcePort, // Original source port is now destination port
                                dnsResponseBuffer
                            )
                            vpnInterface?.fileDescriptor?.let {
                                val vpnOutput = FileOutputStream(it)
                                vpnOutput.write(fullResponsePacket.array(), 0, fullResponsePacket.limit())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in DNS proxy thread", e)
            }
            Log.d(TAG, "DNS Proxy Thread Stopped")
        }
        dnsProxyThread?.start()
    }

    private fun stopVpn() {
        vpnThread?.interrupt()
        vpnThread = null
        dnsProxyThread?.interrupt()
        dnsProxyThread = null
        dnsProxySocket?.close()
        dnsProxySocket = null
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN Stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
        stopSelf()
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.d(TAG, "VPN Revoked")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        Log.d(TAG, "ElderVpnService destroyed")
    }
}
