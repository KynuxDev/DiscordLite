package kynux.cloud.discordLite.database.impl

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.DatabaseProvider
import kynux.cloud.discordLite.database.DatabaseStats
import kynux.cloud.discordLite.database.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class YamlDatabaseProvider(private val plugin: DiscordLite) : DatabaseProvider {
    
    private lateinit var dataFile: File
    private lateinit var config: YamlConfiguration
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        
        dataFile = File(plugin.dataFolder, plugin.configManager.getYamlFile())
        
        if (!dataFile.exists()) {
            dataFile.createNewFile()
        }
        
        config = YamlConfiguration.loadConfiguration(dataFile)
        
        if (!config.contains("players")) config.createSection("players")
        if (!config.contains("pending_verifications")) config.createSection("pending_verifications")
        if (!config.contains("ip_bans")) config.createSection("ip_bans")
        if (!config.contains("security_logs")) config.createSection("security_logs")
        if (!config.contains("system_settings")) config.createSection("system_settings")
        
        saveConfig()
        plugin.logger.info("YAML veritabanı başarıyla başlatıldı: ${dataFile.name}")
    }
    
    private suspend fun saveConfig() = withContext(Dispatchers.IO) {
        config.save(dataFile)
    }
    
    private suspend fun reloadConfig() = withContext(Dispatchers.IO) {
        config = YamlConfiguration.loadConfiguration(dataFile)
    }
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        saveConfig()
    }
    
    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        dataFile.exists() && dataFile.canRead() && dataFile.canWrite()
    }
    
    override suspend fun getPlayerData(uuid: String): PlayerData? = withContext(Dispatchers.IO) {
        reloadConfig()
        val section = config.getConfigurationSection("players.$uuid") ?: return@withContext null
        
        PlayerData(
            uuid = uuid,
            discordId = section.getString("discord_id"),
            minecraftUsername = section.getString("minecraft_username") ?: "",
            twoFAEnabled = section.getBoolean("twofa_enabled", false),
            linkedAt = section.getString("linked_at")?.let { LocalDateTime.parse(it, dateFormatter) },
            lastLogin = section.getString("last_login")?.let { LocalDateTime.parse(it, dateFormatter) }
        )
    }
    
    override suspend fun getPlayerDataByDiscordId(discordId: String): PlayerData? = withContext(Dispatchers.IO) {
        reloadConfig()
        val playersSection = config.getConfigurationSection("players") ?: return@withContext null
        
        for (uuid in playersSection.getKeys(false)) {
            val playerSection = playersSection.getConfigurationSection(uuid)
            if (playerSection?.getString("discord_id") == discordId) {
                return@withContext PlayerData(
                    uuid = uuid,
                    discordId = discordId,
                    minecraftUsername = playerSection.getString("minecraft_username") ?: "",
                    twoFAEnabled = playerSection.getBoolean("twofa_enabled", false),
                    linkedAt = playerSection.getString("linked_at")?.let { LocalDateTime.parse(it, dateFormatter) },
                    lastLogin = playerSection.getString("last_login")?.let { LocalDateTime.parse(it, dateFormatter) }
                )
            }
        }
        null
    }
    
    override suspend fun savePlayerData(playerData: PlayerData): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val path = "players.${playerData.uuid}"
        
        config.set("$path.discord_id", playerData.discordId)
        config.set("$path.minecraft_username", playerData.minecraftUsername)
        config.set("$path.twofa_enabled", playerData.twoFAEnabled)
        config.set("$path.linked_at", playerData.linkedAt?.format(dateFormatter))
        config.set("$path.last_login", playerData.lastLogin?.format(dateFormatter))
        
        saveConfig()
    }
    
    override suspend fun deletePlayerData(uuid: String): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        config.set("players.$uuid", null)
        saveConfig()
    }
    
    override suspend fun getAllLinkedPlayers(): List<PlayerData> = withContext(Dispatchers.IO) {
        reloadConfig()
        val playersSection = config.getConfigurationSection("players") ?: return@withContext emptyList()
        val linkedPlayers = mutableListOf<PlayerData>()
        
        for (uuid in playersSection.getKeys(false)) {
            val playerSection = playersSection.getConfigurationSection(uuid)
            val discordId = playerSection?.getString("discord_id")
            
            if (!discordId.isNullOrBlank()) {
                linkedPlayers.add(
                    PlayerData(
                        uuid = uuid,
                        discordId = discordId,
                        minecraftUsername = playerSection.getString("minecraft_username") ?: "",
                        twoFAEnabled = playerSection.getBoolean("twofa_enabled", false),
                        linkedAt = playerSection.getString("linked_at")?.let { LocalDateTime.parse(it, dateFormatter) },
                        lastLogin = playerSection.getString("last_login")?.let { LocalDateTime.parse(it, dateFormatter) }
                    )
                )
            }
        }
        linkedPlayers
    }
    
    override suspend fun unlinkPlayer(uuid: String): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        config.set("players.$uuid.discord_id", null)
        config.set("players.$uuid.twofa_enabled", false)
        saveConfig()
    }
    
    override suspend fun updatePlayerLastLogin(uuid: String): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        config.set("players.$uuid.last_login", LocalDateTime.now().format(dateFormatter))
        saveConfig()
    }
    
    override suspend fun savePendingVerification(verification: PendingVerification): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val path = "pending_verifications.${verification.id}"
        
        config.set("$path.player_uuid", verification.playerUUID)
        config.set("$path.verification_code", verification.verificationCode)
        config.set("$path.ip_address", verification.ipAddress)
        config.set("$path.discord_message_id", verification.discordMessageId)
        config.set("$path.created_at", verification.createdAt.format(dateFormatter))
        config.set("$path.expires_at", verification.expiresAt.format(dateFormatter))
        
        saveConfig()
    }
    
    override suspend fun getPendingVerification(playerUUID: String): PendingVerification? = withContext(Dispatchers.IO) {
        reloadConfig()
        val verificationsSection = config.getConfigurationSection("pending_verifications") ?: return@withContext null
        
        for (id in verificationsSection.getKeys(false)) {
            val verSection = verificationsSection.getConfigurationSection(id)
            if (verSection?.getString("player_uuid") == playerUUID) {
                return@withContext PendingVerification(
                    id = id,
                    playerUUID = playerUUID,
                    verificationCode = verSection.getString("verification_code") ?: "",
                    ipAddress = verSection.getString("ip_address") ?: "",
                    discordMessageId = verSection.getString("discord_message_id"),
                    createdAt = LocalDateTime.parse(verSection.getString("created_at"), dateFormatter),
                    expiresAt = LocalDateTime.parse(verSection.getString("expires_at"), dateFormatter)
                )
            }
        }
        null
    }
    
    override suspend fun getPendingVerificationByCode(code: String): PendingVerification? = withContext(Dispatchers.IO) {
        reloadConfig()
        val verificationsSection = config.getConfigurationSection("pending_verifications") ?: return@withContext null
        
        for (id in verificationsSection.getKeys(false)) {
            val verSection = verificationsSection.getConfigurationSection(id)
            if (verSection?.getString("verification_code") == code) {
                return@withContext PendingVerification(
                    id = id,
                    playerUUID = verSection.getString("player_uuid") ?: "",
                    verificationCode = code,
                    ipAddress = verSection.getString("ip_address") ?: "",
                    discordMessageId = verSection.getString("discord_message_id"),
                    createdAt = LocalDateTime.parse(verSection.getString("created_at"), dateFormatter),
                    expiresAt = LocalDateTime.parse(verSection.getString("expires_at"), dateFormatter)
                )
            }
        }
        null
    }
    
    override suspend fun deletePendingVerification(id: String): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        config.set("pending_verifications.$id", null)
        saveConfig()
    }
    
    override suspend fun deleteExpiredVerifications(): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val verificationsSection = config.getConfigurationSection("pending_verifications") ?: return@withContext
        val currentTime = LocalDateTime.now()
        val toDelete = mutableListOf<String>()
        
        for (id in verificationsSection.getKeys(false)) {
            val verSection = verificationsSection.getConfigurationSection(id)
            val expiresAt = verSection?.getString("expires_at")?.let { LocalDateTime.parse(it, dateFormatter) }
            
            if (expiresAt != null && currentTime.isAfter(expiresAt)) {
                toDelete.add(id)
            }
        }
        
        toDelete.forEach { id ->
            config.set("pending_verifications.$id", null)
        }
        
        if (toDelete.isNotEmpty()) {
            saveConfig()
            plugin.logger.info("${toDelete.size} süresi dolmuş doğrulama silindi")
        }
    }
    
    override suspend fun saveIPBan(ipBan: IPBan): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val path = "ip_bans.${ipBan.ipAddress.replace(".", "_")}"
        
        config.set("$path.banned_at", ipBan.bannedAt.format(dateFormatter))
        config.set("$path.reason", ipBan.reason)
        config.set("$path.banned_by", ipBan.bannedBy)
        config.set("$path.expires_at", ipBan.expiresAt?.format(dateFormatter))
        
        saveConfig()
    }
    
    override suspend fun getIPBan(ipAddress: String): IPBan? = withContext(Dispatchers.IO) {
        reloadConfig()
        val key = ipAddress.replace(".", "_")
        val section = config.getConfigurationSection("ip_bans.$key") ?: return@withContext null
        
        IPBan(
            ipAddress = ipAddress,
            bannedAt = LocalDateTime.parse(section.getString("banned_at"), dateFormatter),
            reason = section.getString("reason") ?: "",
            bannedBy = section.getString("banned_by") ?: "",
            expiresAt = section.getString("expires_at")?.let { LocalDateTime.parse(it, dateFormatter) }
        )
    }
    
    override suspend fun deleteIPBan(ipAddress: String): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val key = ipAddress.replace(".", "_")
        config.set("ip_bans.$key", null)
        saveConfig()
    }
    
    override suspend fun getAllIPBans(): List<IPBan> = withContext(Dispatchers.IO) {
        reloadConfig()
        val bansSection = config.getConfigurationSection("ip_bans") ?: return@withContext emptyList()
        val bans = mutableListOf<IPBan>()
        
        for (key in bansSection.getKeys(false)) {
            val banSection = bansSection.getConfigurationSection(key)
            if (banSection != null) {
                val ipAddress = key.replace("_", ".")
                bans.add(
                    IPBan(
                        ipAddress = ipAddress,
                        bannedAt = LocalDateTime.parse(banSection.getString("banned_at"), dateFormatter),
                        reason = banSection.getString("reason") ?: "",
                        bannedBy = banSection.getString("banned_by") ?: "",
                        expiresAt = banSection.getString("expires_at")?.let { LocalDateTime.parse(it, dateFormatter) }
                    )
                )
            }
        }
        bans
    }
    
    override suspend fun deleteExpiredIPBans(): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val bansSection = config.getConfigurationSection("ip_bans") ?: return@withContext
        val currentTime = LocalDateTime.now()
        val toDelete = mutableListOf<String>()
        
        for (key in bansSection.getKeys(false)) {
            val banSection = bansSection.getConfigurationSection(key)
            val expiresAt = banSection?.getString("expires_at")?.let { LocalDateTime.parse(it, dateFormatter) }
            
            if (expiresAt != null && currentTime.isAfter(expiresAt)) {
                toDelete.add(key)
            }
        }
        
        toDelete.forEach { key ->
            config.set("ip_bans.$key", null)
        }
        
        if (toDelete.isNotEmpty()) {
            saveConfig()
            plugin.logger.info("${toDelete.size} süresi dolmuş IP ban silindi")
        }
    }
    
    override suspend fun saveSecurityLog(securityLog: SecurityLog): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val path = "security_logs.${securityLog.id}"
        
        config.set("$path.event_type", securityLog.eventType.name)
        config.set("$path.player_uuid", securityLog.playerUUID)
        config.set("$path.ip_address", securityLog.ipAddress)
        config.set("$path.description", securityLog.description)
        config.set("$path.details", securityLog.details)
        config.set("$path.timestamp", securityLog.timestamp.format(dateFormatter))
        
        saveConfig()
    }
    
    override suspend fun getAllSecurityLogs(limit: Int, offset: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext emptyList()
        val logs = mutableListOf<SecurityLog>()
        
        for (id in logsSection.getKeys(false)) {
            val logSection = logsSection.getConfigurationSection(id)
            if (logSection != null) {
                logs.add(
                    SecurityLog(
                        id = id,
                        eventType = SecurityEventType.valueOf(logSection.getString("event_type") ?: "UNKNOWN"),
                        playerUUID = logSection.getString("player_uuid"),
                        ipAddress = logSection.getString("ip_address"),
                        description = logSection.getString("description") ?: "",
                        details = logSection.getString("details"),
                        timestamp = LocalDateTime.parse(logSection.getString("timestamp"), dateFormatter)
                    )
                )
            }
        }
        
        logs.sortByDescending { it.timestamp }
        return@withContext logs.drop(offset).take(limit)
    }
    
    override suspend fun getSecurityLogs(limit: Int, offset: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        getAllSecurityLogs(limit, offset)
    }
    
    override suspend fun getSecurityLogsByPlayer(playerUUID: String, limit: Int, offset: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext emptyList()
        val logs = mutableListOf<SecurityLog>()
        
        for (id in logsSection.getKeys(false)) {
            val logSection = logsSection.getConfigurationSection(id)
            if (logSection?.getString("player_uuid") == playerUUID) {
                logs.add(
                    SecurityLog(
                        id = id,
                        eventType = SecurityEventType.valueOf(logSection.getString("event_type") ?: "UNKNOWN"),
                        playerUUID = playerUUID,
                        ipAddress = logSection.getString("ip_address"),
                        description = logSection.getString("description") ?: "",
                        details = logSection.getString("details"),
                        timestamp = LocalDateTime.parse(logSection.getString("timestamp"), dateFormatter)
                    )
                )
            }
        }
        
        logs.sortByDescending { it.timestamp }
        return@withContext logs.drop(offset).take(limit)
    }
    
    override suspend fun getSecurityLogsByType(eventType: SecurityEventType, limit: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext emptyList()
        val logs = mutableListOf<SecurityLog>()
        
        for (id in logsSection.getKeys(false)) {
            val logSection = logsSection.getConfigurationSection(id)
            if (logSection?.getString("event_type") == eventType.name) {
                logs.add(
                    SecurityLog(
                        id = id,
                        eventType = eventType,
                        playerUUID = logSection.getString("player_uuid"),
                        ipAddress = logSection.getString("ip_address"),
                        description = logSection.getString("description") ?: "",
                        details = logSection.getString("details"),
                        timestamp = LocalDateTime.parse(logSection.getString("timestamp"), dateFormatter)
                    )
                )
            }
        }
        
        logs.sortByDescending { it.timestamp }
        return@withContext logs.take(limit)
    }
    
    override suspend fun getSecurityLogsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): List<SecurityLog> = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext emptyList()
        val logs = mutableListOf<SecurityLog>()
        
        for (id in logsSection.getKeys(false)) {
            val logSection = logsSection.getConfigurationSection(id)
            if (logSection != null) {
                val timestamp = LocalDateTime.parse(logSection.getString("timestamp"), dateFormatter)
                if (!timestamp.isBefore(startDate) && !timestamp.isAfter(endDate)) {
                    logs.add(
                        SecurityLog(
                            id = id,
                            eventType = SecurityEventType.valueOf(logSection.getString("event_type") ?: "UNKNOWN"),
                            playerUUID = logSection.getString("player_uuid"),
                            ipAddress = logSection.getString("ip_address"),
                            description = logSection.getString("description") ?: "",
                            details = logSection.getString("details"),
                            timestamp = timestamp
                        )
                    )
                }
            }
        }
        
        logs.sortByDescending { it.timestamp }
        return@withContext logs
    }
    
    override suspend fun getSecurityLogsByIP(ipAddress: String, limit: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext emptyList()
        val logs = mutableListOf<SecurityLog>()
        
        for (id in logsSection.getKeys(false)) {
            val logSection = logsSection.getConfigurationSection(id)
            if (logSection?.getString("ip_address") == ipAddress) {
                logs.add(
                    SecurityLog(
                        id = id,
                        eventType = SecurityEventType.valueOf(logSection.getString("event_type") ?: "UNKNOWN"),
                        playerUUID = logSection.getString("player_uuid"),
                        ipAddress = ipAddress,
                        description = logSection.getString("description") ?: "",
                        details = logSection.getString("details"),
                        timestamp = LocalDateTime.parse(logSection.getString("timestamp"), dateFormatter)
                    )
                )
            }
        }
        
        logs.sortByDescending { it.timestamp }
        return@withContext logs.take(limit)
    }
    
    override suspend fun getSecurityLogsByEventAndIP(eventType: SecurityEventType, ipAddress: String, limit: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext emptyList()
        val logs = mutableListOf<SecurityLog>()
        
        for (id in logsSection.getKeys(false)) {
            val logSection = logsSection.getConfigurationSection(id)
            if (logSection?.getString("event_type") == eventType.name && logSection.getString("ip_address") == ipAddress) {
                logs.add(
                    SecurityLog(
                        id = id,
                        eventType = eventType,
                        playerUUID = logSection.getString("player_uuid"),
                        ipAddress = ipAddress,
                        description = logSection.getString("description") ?: "",
                        details = logSection.getString("details"),
                        timestamp = LocalDateTime.parse(logSection.getString("timestamp"), dateFormatter)
                    )
                )
            }
        }
        
        logs.sortByDescending { it.timestamp }
        return@withContext logs.take(limit)
    }
    
    override suspend fun getSecurityLogCountByPlayer(playerUUID: String): Int = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext 0
        
        return@withContext logsSection.getKeys(false).count { id ->
            logsSection.getConfigurationSection(id)?.getString("player_uuid") == playerUUID
        }
    }
    
    override suspend fun getSecurityLogCountByIP(ipAddress: String): Int = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext 0
        
        return@withContext logsSection.getKeys(false).count { id ->
            logsSection.getConfigurationSection(id)?.getString("ip_address") == ipAddress
        }
    }
    
    override suspend fun getTotalSecurityLogCount(): Int = withContext(Dispatchers.IO) {
        reloadConfig()
        return@withContext config.getConfigurationSection("security_logs")?.getKeys(false)?.size ?: 0
    }
    
    override suspend fun deleteOldSecurityLogs(daysOld: Int): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        val logsSection = config.getConfigurationSection("security_logs") ?: return@withContext
        val cutoffDate = LocalDateTime.now().minusDays(daysOld.toLong())
        val toDelete = mutableListOf<String>()
        
        for (id in logsSection.getKeys(false)) {
            val logSection = logsSection.getConfigurationSection(id)
            val timestamp = logSection?.getString("timestamp")?.let { LocalDateTime.parse(it, dateFormatter) }
            
            if (timestamp != null && timestamp.isBefore(cutoffDate)) {
                toDelete.add(id)
            }
        }
        
        toDelete.forEach { id ->
            config.set("security_logs.$id", null)
        }
        
        if (toDelete.isNotEmpty()) {
            saveConfig()
            plugin.logger.info("${toDelete.size} eski güvenlik logu silindi")
        }
    }
    
    override suspend fun cleanup(): Unit = withContext(Dispatchers.IO) {
        deleteExpiredVerifications()
        deleteExpiredIPBans()
    }
    
    override suspend fun backup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupFile = File(plugin.dataFolder, "backup_${System.currentTimeMillis()}_${dataFile.name}")
            dataFile.copyTo(backupFile, overwrite = true)
            plugin.logger.info("YAML veritabanı yedeği oluşturuldu: ${backupFile.name}")
            true
        } catch (e: Exception) {
            plugin.logger.severe("YAML veritabanı yedekleme hatası: ${e.message}")
            false
        }
    }
    
    override suspend fun getStats(): DatabaseStats = withContext(Dispatchers.IO) {
        reloadConfig()
        
        val playersSection = config.getConfigurationSection("players")
        val totalPlayers = playersSection?.getKeys(false)?.size ?: 0
        val linkedPlayers = playersSection?.getKeys(false)?.count { uuid ->
            playersSection.getConfigurationSection(uuid)?.getString("discord_id") != null
        } ?: 0
        val activeTwoFAUsers = playersSection?.getKeys(false)?.count { uuid ->
            playersSection.getConfigurationSection(uuid)?.getBoolean("twofa_enabled") == true
        } ?: 0
        
        val pendingVerifications = config.getConfigurationSection("pending_verifications")?.getKeys(false)?.size ?: 0
        val activeBans = config.getConfigurationSection("ip_bans")?.getKeys(false)?.size ?: 0
        val totalSecurityLogs = config.getConfigurationSection("security_logs")?.getKeys(false)?.size ?: 0
        
        val sizeInMB = String.format("%.2f MB", dataFile.length() / 1024.0 / 1024.0)
        
        DatabaseStats(
            totalPlayers = totalPlayers,
            linkedPlayers = linkedPlayers,
            activeTwoFAUsers = activeTwoFAUsers,
            pendingVerifications = pendingVerifications,
            activeBans = activeBans,
            totalSecurityLogs = totalSecurityLogs,
            databaseSize = sizeInMB
        )
    }
    
    override suspend fun getSystemSetting(key: String): String? = withContext(Dispatchers.IO) {
        reloadConfig()
        return@withContext config.getString("system_settings.$key.value")
    }
    
    override suspend fun setSystemSetting(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        config.set("system_settings.$key.value", value)
        config.set("system_settings.$key.updated_at", LocalDateTime.now().format(dateFormatter))
        saveConfig()
    }
    
    override suspend fun deleteSystemSetting(key: String): Unit = withContext(Dispatchers.IO) {
        reloadConfig()
        config.set("system_settings.$key", null)
        saveConfig()
    }
}