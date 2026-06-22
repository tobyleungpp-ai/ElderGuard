package com.elderguard.safe

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketUtils {

    // Helper to build an IPv4 header
    fun buildIPv4Header(
        sourceAddress: InetAddress,
        destinationAddress: InetAddress,
        payloadLength: Int,
        protocol: Int // e.g., 17 for UDP
    ): ByteBuffer {
        val header = ByteBuffer.allocate(20) // IPv4 header is 20 bytes without options
        header.order(ByteOrder.BIG_ENDIAN)

        // Version (4) + IHL (5 words = 20 bytes)
        header.put(0x45) // 4 bits Version, 4 bits IHL
        // DSCP (0) + ECN (0)
        header.put(0x00)
        // Total Length (header + payload)
        header.putShort((20 + payloadLength).toShort())
        // Identification
        header.putShort(0x0000) // Can be 0 for simple cases, or unique ID
        // Flags (0) + Fragment Offset (0)
        header.putShort(0x4000) // Don't Fragment
        // Time to Live
        header.put(64) // Typical TTL
        // Protocol
        header.put(protocol.toByte())
        // Header Checksum (calculated later)
        header.putShort(0x0000)
        // Source IP Address
        header.put(sourceAddress.address)
        // Destination IP Address
        header.put(destinationAddress.address)

        header.flip()
        // Calculate and set checksum
        val checksum = calculateChecksum(header.array(), 0, 20)
        header.putShort(10, checksum)
        header.position(0)

        return header
    }

    // Helper to build a UDP header
    fun buildUdpHeader(
        sourcePort: Int,
        destinationPort: Int,
        payloadLength: Int
    ): ByteBuffer {
        val header = ByteBuffer.allocate(8) // UDP header is 8 bytes
        header.order(ByteOrder.BIG_ENDIAN)

        // Source Port
        header.putShort(sourcePort.toShort())
        // Destination Port
        header.putShort(destinationPort.toShort())
        // Length (header + payload)
        header.putShort((8 + payloadLength).toShort())
        // Checksum (can be 0 for UDP if not used, or calculated later)
        header.putShort(0x0000)

        header.flip()
        return header
    }

    // Simple checksum calculation (for IPv4 header)
    private fun calculateChecksum(buffer: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            sum += ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    // Helper to build a full DNS response packet (IP + UDP + DNS)
    fun buildDnsResponsePacket(
        sourceIp: InetAddress,
        destinationIp: InetAddress,
        sourcePort: Int,
        destinationPort: Int,
        dnsResponsePayload: ByteBuffer
    ): ByteBuffer {
        val udpPayloadLength = dnsResponsePayload.remaining()
        val udpHeader = buildUdpHeader(sourcePort, destinationPort, udpPayloadLength)
        val ipPayloadLength = udpHeader.remaining() + udpPayloadLength
        val ipHeader = buildIPv4Header(sourceIp, destinationIp, ipPayloadLength, 17) // 17 for UDP

        val totalLength = ipHeader.remaining() + udpHeader.remaining() + dnsResponsePayload.remaining()
        val fullPacket = ByteBuffer.allocate(totalLength)
        fullPacket.put(ipHeader)
        fullPacket.put(udpHeader)
        fullPacket.put(dnsResponsePayload)
        fullPacket.flip()
        return fullPacket
    }
}
