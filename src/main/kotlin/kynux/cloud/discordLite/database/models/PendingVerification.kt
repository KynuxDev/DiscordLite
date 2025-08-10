package kynux.cloud.discordLite.database.models

import java.time.LocalDateTime

data class PendingVerification(
    val id: String,
    val playerUUID: String,
    val verificationCode: String,
    val ipAddress: String,
    val discordMessageId: String?,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime
) {
    companion object {
        fun create(
            playerUUID: String,
            verificationCode: String,
            ipAddress: String,
            timeoutSeconds: Int = 300
        ): PendingVerification {
            val now = LocalDateTime.now()
            return PendingVerification(
                id = generateId(),
                playerUUID = playerUUID,
                verificationCode = verificationCode,
                ipAddress = ipAddress,
                discordMessageId = null,
                createdAt = now,
                expiresAt = now.plusSeconds(timeoutSeconds.toLong())
            )
        }
        
        private fun generateId(): String {
            return java.util.UUID.randomUUID().toString()
        }
    }
    
    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expiresAt)
    }
    
    fun withDiscordMessageId(messageId: String): PendingVerification {
        return copy(discordMessageId = messageId)
    }
    
    fun getRemainingSeconds(): Long {
        val now = LocalDateTime.now()
        return if (now.isBefore(expiresAt)) {
            java.time.Duration.between(now, expiresAt).seconds
        } else {
            0
        }
    }
}