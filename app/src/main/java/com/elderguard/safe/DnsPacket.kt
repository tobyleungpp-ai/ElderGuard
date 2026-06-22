package com.elderguard.safe

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Simplified DNS Packet structure for parsing queries
class DnsPacket(val buffer: ByteBuffer) {

    init {
        buffer.order(ByteOrder.BIG_ENDIAN)
    }

    fun getId(): Short = buffer.getShort(0)

    fun getFlags(): Short = buffer.getShort(2)

    fun getQuestionCount(): Short = buffer.getShort(4)

    fun getAnswerCount(): Short = buffer.getShort(6)

    fun getAuthorityCount(): Short = buffer.getShort(8)

    fun getAdditionalCount(): Short = buffer.getShort(10)

    fun isQuery(): Boolean = (getFlags().toInt() and 0x8000) == 0

    fun getQueryDomain(): String? {
        if (!isQuery() || getQuestionCount().toInt() == 0) return null

        // Skip DNS header (12 bytes)
        buffer.position(12)

        val domainBuilder = StringBuilder()
        var length = buffer.get().toInt() and 0xFF
        while (length > 0) {
            val bytes = ByteArray(length)
            buffer.get(bytes)
            domainBuilder.append(String(bytes))
            length = buffer.get().toInt() and 0xFF
            if (length > 0) {
                domainBuilder.append(".")
            }
        }
        return domainBuilder.toString()
    }

    // Method to create a DNS response for a blocked query
    fun createBlockedResponse(originalId: Short): ByteBuffer {
        val responseBuffer = ByteBuffer.allocate(512)
        responseBuffer.order(ByteOrder.BIG_ENDIAN)

        // Copy original ID
        responseBuffer.putShort(originalId)

        // Flags: QR=1 (response), Opcode=0 (query), AA=0, TC=0, RD=1, RA=1, Z=0, RCODE=0 (NoError)
        // For blocked, we might want to return NXDOMAIN (RCODE=3) or just NoError with no answers
        // Let's use NoError for now, and return no answers
        responseBuffer.putShort(0x8180) // Standard query response, recursion desired, recursion available

        // QDCOUNT: 0 (no questions in response, as we are not forwarding)
        responseBuffer.putShort(0)
        // ANCOUNT: 0 (no answers)
        responseBuffer.putShort(0)
        // NSCOUNT: 0
        responseBuffer.putShort(0)
        // ARCOUNT: 0
        responseBuffer.putShort(0)

        responseBuffer.flip()
        return responseBuffer
    }

    // Method to create a DNS response for a blocked query (NXDOMAIN)
    fun createNxDomainResponse(originalId: Short): ByteBuffer {
        val responseBuffer = ByteBuffer.allocate(512)
        responseBuffer.order(ByteOrder.BIG_ENDIAN)

        // Copy original ID
        responseBuffer.putShort(originalId)

        // Flags: QR=1 (response), Opcode=0 (query), AA=0, TC=0, RD=1, RA=1, Z=0, RCODE=3 (NXDOMAIN)
        responseBuffer.putShort(0x8183) // Standard query response, recursion desired, recursion available, NXDOMAIN

        // QDCOUNT: 0 (no questions in response, as we are not forwarding)
        responseBuffer.putShort(0)
        // ANCOUNT: 0 (no answers)
        responseBuffer.putShort(0)
        // NSCOUNT: 0
        responseBuffer.putShort(0)
        // ARCOUNT: 0
        responseBuffer.putShort(0)

        responseBuffer.flip()
        return responseBuffer
    }
}
