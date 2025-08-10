package kynux.cloud.discordLite.database.models

import java.time.LocalDateTime

data class IPBan(
    val ipAddress: String,
    val bannedAt: LocalDateTime,
    val reason: String,
    val bannedBy: String,
    val expiresAt: LocalDateTime?
) {
    companion object {
        fun create(
            ipAddress: String,
            reason: String,
            bannedBy: String,
            durationSeconds: Int? = null
        ): IPBan {
            val now = LocalDateTime.now()
            val expiresAt = if (durationSeconds != null && durationSeconds > 0) {
                now.plusSeconds(durationSeconds.toLong())
            } else null
            
            return IPBan(
                ipAddress = ipAddress,
                bannedAt = now,
                reason = reason,
                bannedBy = bannedBy,
                expiresAt = expiresAt
            )
        }
    }
    
    fun isExpired(): Boolean {
        return expiresAt?.let { LocalDateTime.now().isAfter(it) } ?: false
    }
    
    fun isPermanent(): Boolean = expiresAt == null
    
    fun getRemainingTime(): String {
        return if (isPermanent()) {
            "Kalıcı"
        } else if (isExpired()) {
            "Süresi dolmuş"
        } else {
            val duration = java.time.Duration.between(LocalDateTime.now(), expiresAt)
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            val minutes = duration.toMinutes() % 60
            
            when {
                days > 0 -> "${days}g ${hours}s ${minutes}d"
                hours > 0 -> "${hours}s ${minutes}d"
                else -> "${minutes}d"
            }
        }
    }
}