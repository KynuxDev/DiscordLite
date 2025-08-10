package kynux.cloud.discordLite.database.models

import java.time.LocalDateTime
import java.util.*

data class SecurityLog(
    val id: String = generateId(),
    val eventType: SecurityEventType,
    val description: String,
    val playerUUID: String? = null,
    val playerName: String? = null,
    val ipAddress: String? = null,
    val details: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val serverId: String? = null,
    val additionalData: String = ""
) {
    
    companion object {
        /**
         * Benzersiz ID oluşturur
         */
        private fun generateId(): String {
            return UUID.randomUUID().toString()
        }
        
        /**
         * SecurityLog oluşturmak için factory method
         */
        fun create(
            eventType: SecurityEventType,
            description: String,
            playerUUID: String? = null,
            playerName: String? = null,
            ipAddress: String? = null,
            details: String? = null,
            additionalData: Map<String, Any> = emptyMap()
        ): SecurityLog {
            return SecurityLog(
                eventType = eventType,
                description = description,
                playerUUID = playerUUID,
                playerName = playerName,
                ipAddress = ipAddress,
                details = details,
                timestamp = LocalDateTime.now(),
                serverId = getServerIdentifier(),
                additionalData = additionalData.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            )
        }
        
        /**
         * Başarısız giriş log'u oluşturur
         */
        fun createFailedLogin(
            playerName: String?,
            ipAddress: String?,
            reason: String,
            additionalDetails: Map<String, Any> = emptyMap()
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.FAILED_LOGIN,
                description = "Başarısız giriş denemesi: $reason",
                playerName = playerName,
                ipAddress = ipAddress,
                details = "Reason: $reason",
                additionalData = additionalDetails
            )
        }
        
        /**
         * Başarılı giriş log'u oluşturur
         */
        fun createSuccessfulLogin(
            playerUUID: String,
            playerName: String,
            ipAddress: String?,
            twoFAUsed: Boolean = false
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.LOGIN,
                description = "Başarılı giriş${if (twoFAUsed) " (2FA ile)" else ""}",
                playerUUID = playerUUID,
                playerName = playerName,
                ipAddress = ipAddress,
                details = "2FA: $twoFAUsed",
                additionalData = mapOf("twoFAUsed" to twoFAUsed)
            )
        }
        
        /**
         * Hesap bağlantısı log'u oluşturur
         */
        fun createAccountLinked(
            playerUUID: String,
            playerName: String,
            discordUserId: String,
            discordUsername: String,
            ipAddress: String?
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.ACCOUNT_LINKED,
                description = "Discord hesabı bağlandı: $discordUsername",
                playerUUID = playerUUID,
                playerName = playerName,
                ipAddress = ipAddress,
                details = "Discord User ID: $discordUserId",
                additionalData = mapOf("discordUserId" to discordUserId, "discordUsername" to discordUsername)
            )
        }
        
        /**
         * Hesap bağlantısı kesilmesi log'u oluşturur
         */
        fun createAccountUnlinked(
            playerUUID: String,
            playerName: String,
            discordUserId: String,
            reason: String,
            unlinkedBy: String
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.ACCOUNT_UNLINKED,
                description = "Discord hesap bağlantısı kesildi: $reason",
                playerUUID = playerUUID,
                playerName = playerName,
                details = "Reason: $reason, Unlinked by: $unlinkedBy",
                additionalData = mapOf("discordUserId" to discordUserId, "reason" to reason, "unlinkedBy" to unlinkedBy)
            )
        }
        
        /**
         * IP ban log'u oluşturur
         */
        fun createIPBan(
            ipAddress: String,
            reason: String,
            bannedBy: String,
            duration: Long? = null,
            playerName: String? = null
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.IP_BAN,
                description = "IP banlandı: $reason",
                playerName = playerName,
                ipAddress = ipAddress,
                details = "Banned by: $bannedBy${duration?.let { ", Duration: ${it}s" } ?: ", Permanent"}",
                additionalData = mapOf("bannedBy" to bannedBy, "duration" to (duration ?: -1), "permanent" to (duration == null))
            )
        }
        
        /**
         * IP ban kaldırma log'u oluşturur
         */
        fun createIPUnban(
            ipAddress: String,
            unbannedBy: String,
            reason: String = "Manual unban"
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.IP_UNBAN,
                description = "IP ban kaldırıldı: $reason",
                ipAddress = ipAddress,
                details = "Unbanned by: $unbannedBy",
                additionalData = mapOf("unbannedBy" to unbannedBy, "reason" to reason)
            )
        }
        
        /**
         * Şüpheli aktivite log'u oluşturur
         */
        fun createSuspiciousActivity(
            description: String,
            ipAddress: String?,
            playerName: String? = null,
            activityType: String,
            riskScore: Int,
            details: Map<String, Any> = emptyMap()
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.SUSPICIOUS_ACTIVITY,
                description = "Şüpheli aktivite: $description",
                playerName = playerName,
                ipAddress = ipAddress,
                details = "Activity Type: $activityType, Risk Score: $riskScore",
                additionalData = details + mapOf("activityType" to activityType, "riskScore" to riskScore)
            )
        }
        
        /**
         * Admin işlemi log'u oluşturur
         */
        fun createAdminAction(
            adminPlayerName: String,
            adminPlayerUUID: String,
            action: String,
            target: String?,
            ipAddress: String?,
            details: String = ""
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.ADMIN_ACTION,
                description = "Admin işlemi: $action${target?.let { " -> $it" } ?: ""}",
                playerUUID = adminPlayerUUID,
                playerName = adminPlayerName,
                ipAddress = ipAddress,
                details = "Action: $action, Target: ${target ?: "N/A"}${if (details.isNotEmpty()) ", Details: $details" else ""}",
                additionalData = mapOf("action" to action, "target" to (target ?: ""), "adminAction" to true)
            )
        }
        
        /**
         * Sistem hatası log'u oluşturur
         */
        fun createSystemError(
            errorMessage: String,
            stackTrace: String? = null,
            component: String,
            severity: String = "ERROR"
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.SYSTEM_ERROR,
                description = "Sistem hatası: $errorMessage",
                details = "Component: $component, Severity: $severity",
                additionalData = mapOf("component" to component, "severity" to severity, "stackTrace" to (stackTrace ?: ""), "errorMessage" to errorMessage)
            )
        }
        
        /**
         * Yetkisiz erişim log'u oluşturur
         */
        fun createUnauthorizedAccess(
            playerName: String?,
            playerUUID: String?,
            ipAddress: String?,
            attemptedAction: String,
            requiredPermission: String
        ): SecurityLog {
            return create(
                eventType = SecurityEventType.UNAUTHORIZED_ACCESS,
                description = "Yetkisiz erişim denemesi: $attemptedAction",
                playerUUID = playerUUID,
                playerName = playerName,
                ipAddress = ipAddress,
                details = "Attempted: $attemptedAction, Required: $requiredPermission",
                additionalData = mapOf("attemptedAction" to attemptedAction, "requiredPermission" to requiredPermission)
            )
        }
        
        /**
         * Sunucu tanımlayıcısını alır
         */
        private fun getServerIdentifier(): String {
            return try {
                val hostname = java.net.InetAddress.getLocalHost().hostName
                val port = System.getProperty("server.port", "25565")
                "$hostname:$port"
            } catch (e: Exception) {
                "unknown-server"
            }
        }
    }
    
    /**
     * Log'un kritik olup olmadığını kontrol eder
     */
    fun isCritical(): Boolean {
        return eventType.severity >= 4
    }
    
    /**
     * Log'un yüksek risk içerip içermediğini kontrol eder
     */
    fun isHighRisk(): Boolean {
        return eventType.severity >= 3
    }
    
    /**
     * Log'u JSON formatında string'e çevirir
     */
    fun toJsonString(): String {
        return buildString {
            append("{")
            append("\"id\":$id,")
            append("\"eventType\":\"${eventType.name}\",")
            append("\"description\":\"${description.replace("\"", "\\\"")}\",")
            append("\"playerUUID\":${playerUUID?.let { "\"$it\"" } ?: "null"},")
            append("\"playerName\":${playerName?.let { "\"$it\"" } ?: "null"},")
            append("\"ipAddress\":${ipAddress?.let { "\"$it\"" } ?: "null"},")
            append("\"timestamp\":\"$timestamp\",")
            append("\"severity\":${eventType.severity}")
            append("}")
        }
    }
    
    /**
     * Log'u human-readable formatta string'e çevirir
     */
    fun toReadableString(): String {
        return buildString {
            append("[${timestamp}] ")
            append("${eventType.displayName}: ")
            append(description)
            
            if (playerName != null) {
                append(" | Oyuncu: $playerName")
            }
            
            if (ipAddress != null) {
                append(" | IP: $ipAddress")
            }
            
            if (!details.isNullOrBlank()) {
                append(" | Detaylar: $details")
            }
        }
    }
    
    /**
     * Formatlanmış timestamp döndürür
     */
    fun getFormattedTimestamp(): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        return timestamp.format(formatter)
    }
    
    /**
     * Log'un hash'ini hesaplar (benzersizlik kontrolü için)
     */
    fun calculateHash(): String {
        val content = "$eventType$description$playerUUID$ipAddress$timestamp"
        return content.hashCode().toString()
    }
}