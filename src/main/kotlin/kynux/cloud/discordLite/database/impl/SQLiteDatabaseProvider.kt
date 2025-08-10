package kynux.cloud.discordLite.database.impl

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.DatabaseProvider
import kynux.cloud.discordLite.database.DatabaseStats
import kynux.cloud.discordLite.database.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SQLiteDatabaseProvider(private val plugin: DiscordLite) : DatabaseProvider {
    
    private var connection: Connection? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        val databaseFile = File(plugin.dataFolder, plugin.configManager.getSQLiteFile())
        
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"
        
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount < maxRetries) {
            try {
                connection?.close()
                connection = DriverManager.getConnection(url)
                
                connection?.createStatement()?.use { stmt ->
                    stmt.execute("PRAGMA journal_mode=WAL;")
                    stmt.execute("PRAGMA synchronous=NORMAL;")
                    stmt.execute("PRAGMA cache_size=10000;")
                    stmt.execute("PRAGMA foreign_keys=ON;")
                    stmt.execute("PRAGMA busy_timeout=5000;")
                }
                
                createTables()
                plugin.logger.info("SQLite veritabanı başarıyla başlatıldı: ${databaseFile.name}")
                return@withContext
                
            } catch (e: SQLException) {
                retryCount++
                plugin.logger.warning("Veritabanı bağlantı denemesi $retryCount/$maxRetries başarısız: ${e.message}")
                
                if (retryCount >= maxRetries) {
                    throw e
                }
                
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    private suspend fun createTables() = withContext(Dispatchers.IO) {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                discord_id TEXT UNIQUE,
                minecraft_username TEXT NOT NULL,
                twofa_enabled INTEGER DEFAULT 0,
                linked_at TEXT,
                last_login TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS pending_verifications (
                id TEXT PRIMARY KEY,
                player_uuid TEXT NOT NULL,
                verification_code TEXT NOT NULL,
                ip_address TEXT NOT NULL,
                discord_message_id TEXT,
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS ip_bans (
                ip_address TEXT PRIMARY KEY,
                banned_at TEXT NOT NULL,
                reason TEXT NOT NULL,
                banned_by TEXT NOT NULL,
                expires_at TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS security_logs (
                id TEXT PRIMARY KEY,
                event_type TEXT NOT NULL,
                player_uuid TEXT,
                ip_address TEXT,
                description TEXT NOT NULL,
                details TEXT,
                timestamp TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS system_settings (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_players_discord_id ON players(discord_id)",
            "CREATE INDEX IF NOT EXISTS idx_pending_verifications_uuid ON pending_verifications(player_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_pending_verifications_code ON pending_verifications(verification_code)",
            "CREATE INDEX IF NOT EXISTS idx_security_logs_type ON security_logs(event_type)",
            "CREATE INDEX IF NOT EXISTS idx_security_logs_player ON security_logs(player_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_security_logs_timestamp ON security_logs(timestamp)"
        )
        
        connection?.let { conn ->
            conn.autoCommit = false
            try {
                statements.forEach { sql ->
                    conn.createStatement().use { stmt ->
                        stmt.execute(sql)
                    }
                }
                conn.commit()
                plugin.logger.info("Veritabanı tabloları başarıyla oluşturuldu")
            } catch (e: Exception) {
                conn.rollback()
                plugin.logger.severe("Tablo oluşturma hatası: ${e.message}")
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        connection?.close()
        connection = null
    }
    
    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        connection?.isValid(5) ?: false
    }
    
    override suspend fun getPlayerData(uuid: String): PlayerData? = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM players WHERE uuid = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, uuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext mapResultSetToPlayerData(rs)
                }
            }
        }
        null
    }
    
    override suspend fun getPlayerDataByDiscordId(discordId: String): PlayerData? = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM players WHERE discord_id = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, discordId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext mapResultSetToPlayerData(rs)
                }
            }
        }
        null
    }
    
    override suspend fun savePlayerData(playerData: PlayerData): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT OR REPLACE INTO players 
            (uuid, discord_id, minecraft_username, twofa_enabled, linked_at, last_login)
            VALUES (?, ?, ?, ?, ?, ?)
        """
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, playerData.uuid)
            stmt.setString(2, playerData.discordId)
            stmt.setString(3, playerData.minecraftUsername)
            stmt.setInt(4, if (playerData.twoFAEnabled) 1 else 0)
            stmt.setString(5, playerData.linkedAt?.format(dateFormatter))
            stmt.setString(6, playerData.lastLogin?.format(dateFormatter))
            stmt.executeUpdate()
        }
    }
    
    override suspend fun deletePlayerData(uuid: String): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM players WHERE uuid = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, uuid)
            stmt.executeUpdate()
        }
    }
    
    override suspend fun unlinkPlayer(uuid: String): Unit = withContext(Dispatchers.IO) {
        val sql = "UPDATE players SET discord_id = NULL, twofa_enabled = 0 WHERE uuid = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, uuid)
            stmt.executeUpdate()
        }
    }
    
    override suspend fun updatePlayerLastLogin(uuid: String): Unit = withContext(Dispatchers.IO) {
        val sql = "UPDATE players SET last_login = ? WHERE uuid = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, LocalDateTime.now().format(dateFormatter))
            stmt.setString(2, uuid)
            stmt.executeUpdate()
        }
    }
    
    override suspend fun getAllLinkedPlayers(): List<PlayerData> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM players WHERE discord_id IS NOT NULL"
        val players = mutableListOf<PlayerData>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    players.add(mapResultSetToPlayerData(rs))
                }
            }
        }
        players
    }

    override suspend fun savePendingVerification(verification: PendingVerification): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT OR REPLACE INTO pending_verifications
            (id, player_uuid, verification_code, ip_address, discord_message_id, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, verification.id)
            stmt.setString(2, verification.playerUUID)
            stmt.setString(3, verification.verificationCode)
            stmt.setString(4, verification.ipAddress)
            stmt.setString(5, verification.discordMessageId)
            stmt.setString(6, verification.createdAt.format(dateFormatter))
            stmt.setString(7, verification.expiresAt.format(dateFormatter))
            stmt.executeUpdate()
        }
    }
    
    override suspend fun getPendingVerification(playerUUID: String): PendingVerification? = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM pending_verifications WHERE player_uuid = ? ORDER BY created_at DESC LIMIT 1"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, playerUUID)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext mapResultSetToPendingVerification(rs)
                }
            }
        }
        null
    }
    
    override suspend fun getPendingVerificationByCode(code: String): PendingVerification? = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM pending_verifications WHERE verification_code = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, code)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext mapResultSetToPendingVerification(rs)
                }
            }
        }
        null
    }
    
    override suspend fun deletePendingVerification(id: String): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM pending_verifications WHERE id = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, id)
            stmt.executeUpdate()
        }
    }
    
    override suspend fun deleteExpiredVerifications(): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM pending_verifications WHERE expires_at < ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, LocalDateTime.now().format(dateFormatter))
            val deleted = stmt.executeUpdate()
            if (deleted > 0) {
                plugin.logger.info("$deleted süresi dolmuş doğrulama silindi")
            }
        }
    }
    

    override suspend fun saveIPBan(ipBan: IPBan): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT OR REPLACE INTO ip_bans
            (ip_address, banned_at, reason, banned_by, expires_at)
            VALUES (?, ?, ?, ?, ?)
        """
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, ipBan.ipAddress)
            stmt.setString(2, ipBan.bannedAt.format(dateFormatter))
            stmt.setString(3, ipBan.reason)
            stmt.setString(4, ipBan.bannedBy)
            stmt.setString(5, ipBan.expiresAt?.format(dateFormatter))
            stmt.executeUpdate()
        }
    }
    
    override suspend fun getIPBan(ipAddress: String): IPBan? = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM ip_bans WHERE ip_address = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, ipAddress)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext mapResultSetToIPBan(rs)
                }
            }
        }
        null
    }
    
    override suspend fun deleteIPBan(ipAddress: String): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM ip_bans WHERE ip_address = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, ipAddress)
            stmt.executeUpdate()
        }
    }
    
    override suspend fun getAllIPBans(): List<IPBan> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM ip_bans ORDER BY banned_at DESC"
        val bans = mutableListOf<IPBan>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    bans.add(mapResultSetToIPBan(rs))
                }
            }
        }
        bans
    }
    
    override suspend fun deleteExpiredIPBans(): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM ip_bans WHERE expires_at IS NOT NULL AND expires_at < ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, LocalDateTime.now().format(dateFormatter))
            val deleted = stmt.executeUpdate()
            if (deleted > 0) {
                plugin.logger.info("$deleted süresi dolmuş IP ban silindi")
            }
        }
    }

    override suspend fun saveSecurityLog(securityLog: SecurityLog): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT INTO security_logs
            (id, event_type, player_uuid, ip_address, description, details, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, securityLog.id)
            stmt.setString(2, securityLog.eventType.name)
            stmt.setString(3, securityLog.playerUUID)
            stmt.setString(4, securityLog.ipAddress)
            stmt.setString(5, securityLog.description)
            stmt.setString(6, securityLog.details)
            stmt.setString(7, securityLog.timestamp.format(dateFormatter))
            stmt.executeUpdate()
        }
    }
    
    override suspend fun getAllSecurityLogs(limit: Int, offset: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM security_logs ORDER BY timestamp DESC LIMIT ? OFFSET ?"
        val logs = mutableListOf<SecurityLog>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setInt(1, limit)
            stmt.setInt(2, offset)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    logs.add(mapResultSetToSecurityLog(rs))
                }
            }
        }
        logs
    }
    
    override suspend fun getSecurityLogs(limit: Int, offset: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        getAllSecurityLogs(limit, offset)
    }
    
    override suspend fun getSecurityLogsByPlayer(playerUUID: String, limit: Int, offset: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM security_logs WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?"
        val logs = mutableListOf<SecurityLog>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, playerUUID)
            stmt.setInt(2, limit)
            stmt.setInt(3, offset)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    logs.add(mapResultSetToSecurityLog(rs))
                }
            }
        }
        logs
    }
    
    override suspend fun getSecurityLogCountByPlayer(playerUUID: String): Int = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM security_logs WHERE player_uuid = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, playerUUID)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext rs.getInt(1)
                }
            }
        }
        0
    }
    
    override suspend fun getTotalSecurityLogCount(): Int = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM security_logs"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext rs.getInt(1)
                }
            }
        }
        0
    }
    
    override suspend fun getSecurityLogsByType(eventType: SecurityEventType, limit: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM security_logs WHERE event_type = ? ORDER BY timestamp DESC LIMIT ?"
        val logs = mutableListOf<SecurityLog>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, eventType.name)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    logs.add(mapResultSetToSecurityLog(rs))
                }
            }
        }
        logs
    }
    
    override suspend fun deleteOldSecurityLogs(daysOld: Int): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM security_logs WHERE timestamp < ?"
        val cutoffDate = LocalDateTime.now().minusDays(daysOld.toLong())
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, cutoffDate.format(dateFormatter))
            val deleted = stmt.executeUpdate()
            if (deleted > 0) {
                plugin.logger.info("$deleted eski güvenlik logu silindi")
            }
        }
    }
    
    override suspend fun cleanup(): Unit = withContext(Dispatchers.IO) {
        deleteExpiredVerifications()
        deleteExpiredIPBans()
        deleteOldSecurityLogs(30)
        connection?.createStatement()?.execute("VACUUM;")
    }
    
    override suspend fun backup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(plugin.dataFolder, plugin.configManager.getSQLiteFile())
            val backupFile = File(plugin.dataFolder, "backup_${System.currentTimeMillis()}_${sourceFile.name}")
            
            sourceFile.copyTo(backupFile, overwrite = true)
            plugin.logger.info("Veritabanı yedeği oluşturuldu: ${backupFile.name}")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Veritabanı yedekleme hatası: ${e.message}")
            false
        }
    }
    
    override suspend fun getStats(): DatabaseStats = withContext(Dispatchers.IO) {
        val stats = mutableMapOf<String, Int>()
        
        val queries = mapOf(
            "totalPlayers" to "SELECT COUNT(*) FROM players",
            "linkedPlayers" to "SELECT COUNT(*) FROM players WHERE discord_id IS NOT NULL",
            "activeTwoFAUsers" to "SELECT COUNT(*) FROM players WHERE twofa_enabled = 1",
            "pendingVerifications" to "SELECT COUNT(*) FROM pending_verifications WHERE expires_at > ?",
            "activeBans" to "SELECT COUNT(*) FROM ip_bans WHERE expires_at IS NULL OR expires_at > ?",
            "totalSecurityLogs" to "SELECT COUNT(*) FROM security_logs"
        )
        
        val currentTime = LocalDateTime.now().format(dateFormatter)
        
        queries.forEach { (key, sql) ->
            connection?.prepareStatement(sql)?.use { stmt ->
                if (sql.contains("?")) {
                    stmt.setString(1, currentTime)
                    if (sql.contains("expires_at IS NULL OR")) {
                        stmt.setString(2, currentTime)
                    }
                }
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        stats[key] = rs.getInt(1)
                    }
                }
            }
        }

        val databaseFile = File(plugin.dataFolder, plugin.configManager.getSQLiteFile())
        val sizeInMB = if (databaseFile.exists()) {
            String.format("%.2f MB", databaseFile.length() / 1024.0 / 1024.0)
        } else "0 MB"
        
        DatabaseStats(
            totalPlayers = stats["totalPlayers"] ?: 0,
            linkedPlayers = stats["linkedPlayers"] ?: 0,
            activeTwoFAUsers = stats["activeTwoFAUsers"] ?: 0,
            pendingVerifications = stats["pendingVerifications"] ?: 0,
            activeBans = stats["activeBans"] ?: 0,
            totalSecurityLogs = stats["totalSecurityLogs"] ?: 0,
            databaseSize = sizeInMB
        )
    }
    
    override suspend fun getSecurityLogsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): List<SecurityLog> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM security_logs WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC"
        val logs = mutableListOf<SecurityLog>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, startDate.format(dateFormatter))
            stmt.setString(2, endDate.format(dateFormatter))
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    logs.add(mapResultSetToSecurityLog(rs))
                }
            }
        }
        logs
    }
    
    override suspend fun getSecurityLogsByIP(ipAddress: String, limit: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM security_logs WHERE ip_address = ? ORDER BY timestamp DESC LIMIT ?"
        val logs = mutableListOf<SecurityLog>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, ipAddress)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    logs.add(mapResultSetToSecurityLog(rs))
                }
            }
        }
        logs
    }
    
    override suspend fun getSecurityLogsByEventAndIP(eventType: SecurityEventType, ipAddress: String, limit: Int): List<SecurityLog> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM security_logs WHERE event_type = ? AND ip_address = ? ORDER BY timestamp DESC LIMIT ?"
        val logs = mutableListOf<SecurityLog>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, eventType.name)
            stmt.setString(2, ipAddress)
            stmt.setInt(3, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    logs.add(mapResultSetToSecurityLog(rs))
                }
            }
        }
        logs
    }
    
    override suspend fun getSecurityLogCountByIP(ipAddress: String): Int = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM security_logs WHERE ip_address = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, ipAddress)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext rs.getInt(1)
                }
            }
        }
        0
    }
    
    override suspend fun getSystemSetting(key: String): String? = withContext(Dispatchers.IO) {
        val sql = "SELECT setting_value FROM system_settings WHERE setting_key = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext rs.getString("setting_value")
                }
            }
        }
        null
    }
    
    override suspend fun setSystemSetting(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT OR REPLACE INTO system_settings
            (setting_key, setting_value, updated_at)
            VALUES (?, ?, ?)
        """
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.setString(3, LocalDateTime.now().format(dateFormatter))
            stmt.executeUpdate()
        }
    }
    
    override suspend fun deleteSystemSetting(key: String): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM system_settings WHERE setting_key = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, key)
            stmt.executeUpdate()
        }
    }
    
    private fun mapResultSetToPlayerData(rs: ResultSet): PlayerData {
        return PlayerData(
            uuid = rs.getString("uuid"),
            discordId = rs.getString("discord_id"),
            minecraftUsername = rs.getString("minecraft_username"),
            twoFAEnabled = rs.getInt("twofa_enabled") == 1,
            linkedAt = rs.getString("linked_at")?.let { LocalDateTime.parse(it, dateFormatter) },
            lastLogin = rs.getString("last_login")?.let { LocalDateTime.parse(it, dateFormatter) }
        )
    }
    
    private fun mapResultSetToPendingVerification(rs: ResultSet): PendingVerification {
        return PendingVerification(
            id = rs.getString("id"),
            playerUUID = rs.getString("player_uuid"),
            verificationCode = rs.getString("verification_code"),
            ipAddress = rs.getString("ip_address"),
            discordMessageId = rs.getString("discord_message_id"),
            createdAt = LocalDateTime.parse(rs.getString("created_at"), dateFormatter),
            expiresAt = LocalDateTime.parse(rs.getString("expires_at"), dateFormatter)
        )
    }
    
    private fun mapResultSetToIPBan(rs: ResultSet): IPBan {
        return IPBan(
            ipAddress = rs.getString("ip_address"),
            bannedAt = LocalDateTime.parse(rs.getString("banned_at"), dateFormatter),
            reason = rs.getString("reason"),
            bannedBy = rs.getString("banned_by"),
            expiresAt = rs.getString("expires_at")?.let { LocalDateTime.parse(it, dateFormatter) }
        )
    }
    
    private fun mapResultSetToSecurityLog(rs: ResultSet): SecurityLog {
        return SecurityLog(
            id = rs.getString("id"),
            eventType = SecurityEventType.valueOf(rs.getString("event_type")),
            playerUUID = rs.getString("player_uuid"),
            ipAddress = rs.getString("ip_address"),
            description = rs.getString("description"),
            details = rs.getString("details"),
            timestamp = LocalDateTime.parse(rs.getString("timestamp"), dateFormatter)
        )
    }
}
