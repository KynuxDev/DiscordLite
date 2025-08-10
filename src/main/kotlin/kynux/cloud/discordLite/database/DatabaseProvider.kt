package kynux.cloud.discordLite.database

import kynux.cloud.discordLite.database.models.*

interface DatabaseProvider {
    
    suspend fun initialize()
    suspend fun close()
    suspend fun isConnected(): Boolean
    
    suspend fun getPlayerData(uuid: String): PlayerData?
    suspend fun getPlayerDataByDiscordId(discordId: String): PlayerData?
    suspend fun savePlayerData(playerData: PlayerData)
    suspend fun deletePlayerData(uuid: String)
    suspend fun unlinkPlayer(uuid: String)
    suspend fun updatePlayerLastLogin(uuid: String)
    suspend fun getAllLinkedPlayers(): List<PlayerData>
    
    suspend fun savePendingVerification(verification: PendingVerification)
    suspend fun getPendingVerification(playerUUID: String): PendingVerification?
    suspend fun getPendingVerificationByCode(code: String): PendingVerification?
    suspend fun deletePendingVerification(id: String)
    suspend fun deleteExpiredVerifications()
    
    suspend fun saveIPBan(ipBan: IPBan)
    suspend fun getIPBan(ipAddress: String): IPBan?
    suspend fun deleteIPBan(ipAddress: String)
    suspend fun getAllIPBans(): List<IPBan>
    suspend fun deleteExpiredIPBans()
    
    suspend fun saveSecurityLog(securityLog: SecurityLog)
    suspend fun getAllSecurityLogs(limit: Int = 50, offset: Int = 0): List<SecurityLog>
    suspend fun getSecurityLogs(limit: Int = 50, offset: Int = 0): List<SecurityLog>
    suspend fun getSecurityLogsByPlayer(playerUUID: String, limit: Int = 50, offset: Int = 0): List<SecurityLog>
    suspend fun getSecurityLogsByType(eventType: SecurityEventType, limit: Int = 50): List<SecurityLog>
    suspend fun getSecurityLogsByDateRange(startDate: java.time.LocalDateTime, endDate: java.time.LocalDateTime): List<SecurityLog>
    suspend fun getSecurityLogsByIP(ipAddress: String, limit: Int = 50): List<SecurityLog>
    suspend fun getSecurityLogsByEventAndIP(eventType: SecurityEventType, ipAddress: String, limit: Int = 50): List<SecurityLog>
    suspend fun getSecurityLogCountByPlayer(playerUUID: String): Int
    suspend fun getSecurityLogCountByIP(ipAddress: String): Int
    suspend fun getTotalSecurityLogCount(): Int
    suspend fun deleteOldSecurityLogs(daysOld: Int = 30)
    
    suspend fun getSystemSetting(key: String): String?
    suspend fun setSystemSetting(key: String, value: String)
    suspend fun deleteSystemSetting(key: String)
    
    suspend fun cleanup()
    suspend fun backup(): Boolean
    suspend fun getStats(): DatabaseStats
}

data class DatabaseStats(
    val totalPlayers: Int,
    val linkedPlayers: Int,
    val activeTwoFAUsers: Int,
    val pendingVerifications: Int,
    val activeBans: Int,
    val totalSecurityLogs: Int,
    val databaseSize: String? = null
)