package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import org.bukkit.configuration.file.FileConfiguration
import java.awt.Color

class ConfigManager(private val plugin: DiscordLite) {
    
    private lateinit var config: FileConfiguration
    
    fun loadConfig() {
        plugin.reloadConfig()
        config = plugin.config
        
        validateConfig()
        
        plugin.logger.info("Konfigürasyon başarıyla yüklendi")
    }
    
    private fun validateConfig() {
        val requiredKeys = listOf(
            "discord.bot_token",
            "discord.guild_id",
            "discord.channels.log_channel_id",
            "database.type"
        )
        
        for (key in requiredKeys) {
            if (!config.contains(key)) {
                plugin.logger.warning("Konfigürasyonda gerekli alan eksik: $key - Varsayılan değer kullanılacak")
            }
        }
        
        val botToken = config.getString("discord.bot_token")
        if (botToken == "BOT_TOKEN_BURAYA" || botToken.isNullOrBlank()) {
            plugin.logger.warning("Discord bot token ayarlanmamış!")
        }
        
        val dbType = config.getString("database.type")?.lowercase()
        if (dbType !in listOf("sqlite", "mysql", "yaml")) {
            plugin.logger.warning("Geçersiz veritabanı tipi: $dbType, SQLite kullanılacak")
        }
    }
    
    fun isDebugMode(): Boolean = config.getBoolean("general.debug_mode", false)
    fun getLanguage(): String = config.getString("general.language") ?: "tr"
    fun isAutoBackup(): Boolean = config.getBoolean("general.auto_backup", true)
    fun getBackupInterval(): Long = config.getLong("general.backup_interval", 86400)
    fun getMaxBackups(): Int = config.getInt("general.max_backups", 7)
    
    fun getBotToken(): String = config.getString("discord.bot_token") ?: ""
    fun getGuildId(): String = config.getString("discord.guild_id") ?: ""
    
    fun getLogChannelId(): String = config.getString("discord.channels.log_channel_id") ?: ""
    fun getAdminChannelId(): String = config.getString("discord.channels.admin_channel_id") ?: ""
    fun getSecurityChannelId(): String = config.getString("discord.channels.security_channel_id") ?: ""
    fun getAuditChannelId(): String = config.getString("discord.channels.audit_channel_id") ?: ""
    fun getVerificationChannelId(): String = config.getString("discord.channels.verification_channel_id") ?: ""
    
    fun isBotActivityEnabled(): Boolean = config.getBoolean("discord.activity.enabled", true)
    fun getBotActivityType(): String = config.getString("discord.activity.type") ?: "PLAYING"
    fun getBotActivityMessage(): String = config.getString("discord.activity.message") ?: "DiscordLite Security v1.0"
    fun getBotActivityUpdateInterval(): Int = config.getInt("discord.activity.update_interval", 300)
    
    fun isSlashCommandsEnabled(): Boolean = config.getBoolean("discord.slash_commands.enabled", true)
    fun isSlashCommandsGlobal(): Boolean = config.getBoolean("discord.slash_commands.global", false)
    fun isSlashCommandsAutoRegister(): Boolean = config.getBoolean("discord.slash_commands.auto_register", true)
    
    fun isPersistentVerificationEnabled(): Boolean = config.getBoolean("discord.persistent_verification.enabled", true)
    fun isPersistentVerificationAutoUpdate(): Boolean = config.getBoolean("discord.persistent_verification.auto_update_on_restart", true)
    fun getPersistentVerificationButtonLabel(): String = config.getString("discord.persistent_verification.button_label") ?: "🔐 Kodunu Gir"
    fun getPersistentVerificationRefreshInterval(): Int = config.getInt("discord.persistent_verification.refresh_interval", 300)
    
    fun getDatabaseType(): String = config.getString("database.type")?.lowercase() ?: "sqlite"
    
    fun getConnectionPoolMinIdle(): Int = config.getInt("database.connection_pool.min_idle", 2)
    fun getConnectionPoolMaxSize(): Int = config.getInt("database.connection_pool.max_pool_size", 10)
    fun getConnectionTimeout(): Long = config.getLong("database.connection_pool.connection_timeout", 30000)
    fun getIdleTimeout(): Long = config.getLong("database.connection_pool.idle_timeout", 600000)
    fun getMaxLifetime(): Long = config.getLong("database.connection_pool.max_lifetime", 1800000)
    
    fun getMySQLHost(): String = config.getString("database.mysql.host") ?: "localhost"
    fun getMySQLPort(): Int = config.getInt("database.mysql.port", 3306)
    fun getMySQLDatabase(): String = config.getString("database.mysql.database") ?: "discordlite"
    fun getMySQLUsername(): String = config.getString("database.mysql.username") ?: ""
    fun getMySQLPassword(): String = config.getString("database.mysql.password") ?: ""
    fun getMySQLSSL(): Boolean = config.getBoolean("database.mysql.ssl", false)
    fun getMySQLCharset(): String = config.getString("database.mysql.charset") ?: "utf8mb4"
    fun getMySQLTimezone(): String = config.getString("database.mysql.timezone") ?: "Europe/Istanbul"
    
    fun getSQLiteFile(): String = config.getString("database.sqlite.file") ?: "discordlite.db"
    fun getSQLiteJournalMode(): String = config.getString("database.sqlite.journal_mode") ?: "WAL"
    fun getSQLiteSynchronous(): String = config.getString("database.sqlite.synchronous") ?: "NORMAL"
    
    fun getYamlFile(): String = config.getString("database.yaml.file") ?: "playerdata.yml"
    fun isYamlBackupOnSave(): Boolean = config.getBoolean("database.yaml.backup_on_save", true)

    fun getVerificationTimeout(): Int = config.getInt("security.twofa.verification_timeout", 300)
    fun getMaxVerificationAttempts(): Int = config.getInt("security.twofa.max_verification_attempts", 3)
    fun getAutoKickOnTimeout(): Boolean = config.getBoolean("security.twofa.auto_kick_on_timeout", true)
    fun getFreezePlayerOnVerification(): Boolean = config.getBoolean("security.twofa.freeze_player_on_verification", true)
    fun getAllowMovementWhileFrozen(): Boolean = config.getBoolean("security.twofa.allow_movement_while_frozen", false)

    fun getIPBanDuration(): Int = config.getInt("security.ip_ban.default_duration", -1)
    fun getMaxFailedAttempts(): Int = config.getInt("security.ip_ban.max_failed_attempts", 5)
    fun getFailedAttemptWindow(): Int = config.getInt("security.ip_ban.failed_attempt_window", 3600)
    fun isIPWhitelistEnabled(): Boolean = config.getBoolean("security.ip_ban.whitelist_enabled", false)
    fun getWhitelistedIPs(): List<String> = config.getStringList("security.ip_ban.whitelisted_ips")
    
    fun isAutoSecurityEnabled(): Boolean = config.getBoolean("security.auto_security.enabled", true)
    fun getRiskThreshold(): Int = config.getInt("security.auto_security.risk_threshold", 80)
    fun getBanDurationLow(): Int = config.getInt("security.auto_security.ban_duration_low", 1800)
    fun getBanDurationMedium(): Int = config.getInt("security.auto_security.ban_duration_medium", 3600)
    fun getBanDurationHigh(): Int = config.getInt("security.auto_security.ban_duration_high", 86400)
    
    fun isRateLimitingEnabled(): Boolean = config.getBoolean("security.rate_limiting.enabled", true)
    fun getMaxRequestsPerMinute(): Int = config.getInt("security.rate_limiting.max_requests_per_minute", 10)
    fun getMaxRequestsPerHour(): Int = config.getInt("security.rate_limiting.max_requests_per_hour", 100)
    fun isBanOnExceed(): Boolean = config.getBoolean("security.rate_limiting.ban_on_exceed", true)
    fun getRateLimitBanDuration(): Int = config.getInt("security.rate_limiting.ban_duration", 3600)
    
    fun getLogLevel(): String = config.getString("logging.level") ?: "INFO"
    
    fun isDiscordLoggingEnabled(): Boolean = config.getBoolean("logging.discord_logging.enabled", true)
    fun getDiscordLogRateLimit(): Int = config.getInt("logging.discord_logging.rate_limit", 30)
    fun getLogQueueSize(): Int = config.getInt("logging.discord_logging.queue_size", 100)
    fun getLogBatchSize(): Int = config.getInt("logging.discord_logging.batch_size", 5)
    fun getEmergencyThreshold(): Int = config.getInt("logging.discord_logging.emergency_threshold", 5)
    
    fun isFileLoggingEnabled(): Boolean = config.getBoolean("logging.file_logging.enabled", true)
    fun getLogFilePath(): String = config.getString("logging.file_logging.file_path") ?: "logs/discordlite.log"
    fun getMaxFileSize(): String = config.getString("logging.file_logging.max_file_size") ?: "10MB"
    fun getMaxFiles(): Int = config.getInt("logging.file_logging.max_files", 10)
    
    fun isSecurityLogsEnabled(): Boolean = config.getBoolean("logging.security_logs.enabled", true)
    fun getLogRetentionDays(): Int = config.getInt("logging.security_logs.retention_days", 30)
    fun isDetailedLogging(): Boolean = config.getBoolean("logging.security_logs.detailed_logging", true)
    fun isIncludeStackTraces(): Boolean = config.getBoolean("logging.security_logs.include_stack_traces", false)
    
    fun isTwoFARequiredForOps(): Boolean = config.getBoolean("features.twofa.required_for_ops", true)
    fun getTwoFARequiredPermissions(): List<String> = config.getStringList("features.twofa.required_for_permissions")
    fun getTwoFABypassPermissions(): List<String> = config.getStringList("features.twofa.bypass_permissions")
    
    fun isRoleSyncEnabled(): Boolean = config.getBoolean("features.role_sync.enabled", true)
    fun isAutoRoleSync(): Boolean = config.getBoolean("features.role_sync.auto_sync", true)
    fun getPermissionSyncInterval(): Int = config.getInt("features.role_sync.sync_interval", 300)
    fun isSyncOnJoin(): Boolean = config.getBoolean("features.role_sync.sync_on_join", true)
    fun isSyncOnPermissionChange(): Boolean = config.getBoolean("features.role_sync.sync_on_permission_change", true)
    fun isReverseSyncEnabled(): Boolean = config.getBoolean("features.role_sync.reverse_sync", false)
    
    fun isLinkingCooldownEnabled(): Boolean = config.getBoolean("features.linking.cooldown_enabled", true)
    fun getLinkingCooldown(): Int = config.getInt("features.linking.cooldown_duration", 60)
    fun getMaxAttemptsPerDay(): Int = config.getInt("features.linking.max_attempts_per_day", 5)
    fun isRequireConfirmation(): Boolean = config.getBoolean("features.linking.require_confirmation", true)
    fun isAllowRelinking(): Boolean = config.getBoolean("features.linking.allow_relinking", true)
    
    fun isJoinNotifications(): Boolean = config.getBoolean("features.notifications.join_notifications", true)
    fun isLeaveNotifications(): Boolean = config.getBoolean("features.notifications.leave_notifications", true)
    fun isLinkNotifications(): Boolean = config.getBoolean("features.notifications.link_notifications", true)
    fun isAdminNotifications(): Boolean = config.getBoolean("features.notifications.admin_notifications", true)
    fun isSecurityNotifications(): Boolean = config.getBoolean("features.notifications.security_notifications", true)
    
    fun getMessage(key: String): String {
        return config.getString("messages.$key") ?: "Mesaj bulunamadı: $key"
    }
    
    fun getFormattedMessage(key: String, vararg args: Any): String {
        var message = getMessage(key)
        args.forEachIndexed { index, arg ->
            message = message.replace("{$index}", arg.toString())
        }
        return message
    }
    
    fun getEmbedTitle(type: String): String {
        return config.getString("discord_embeds.$type.title") ?: "DiscordLite"
    }
    
    fun getEmbedDescription(type: String): String {
        return config.getString("discord_embeds.$type.description") ?: ""
    }
    
    fun getEmbedColor(type: String): Color {
        val colorString = config.getString("discord_embeds.$type.color") ?: "#00ff00"
        return try {
            Color.decode(colorString)
        } catch (e: NumberFormatException) {
            Color.GREEN
        }
    }
    
    fun getEmbedFooter(type: String): String {
        return config.getString("discord_embeds.$type.footer") ?: "DiscordLite Security System"
    }
    
    fun isEmbedThumbnail(type: String): Boolean {
        return config.getBoolean("discord_embeds.$type.thumbnail", false)
    }
    
    fun isEmbedTimestamp(type: String): Boolean {
        return config.getBoolean("discord_embeds.$type.timestamp", true)
    }
    
    fun getEmbedPingRoles(type: String): List<String> {
        return config.getStringList("discord_embeds.$type.ping_roles")
    }
    
    fun getRoleMappings(): List<Map<String, Any>> {
        val mapList = config.getMapList("role_mappings")
        return mapList?.map { map ->
            map.filterValues { v -> v != null }
                .filterKeys { k -> k is String }
                .mapKeys { (k, _) -> k as String }
                .mapValues { (_, v) -> v as Any }
        } ?: emptyList()
    }
    
    fun getPermissionToRoleMapping(): Map<String, String> {
        val mappings = mutableMapOf<String, String>()
        val roleMappings = getRoleMappings()
        
        roleMappings.forEach { mapping ->
            val permission = mapping["permission"] as? String
            val roleId = mapping["discord_role_id"] as? String
            
            if (permission != null && roleId != null) {
                mappings[permission] = roleId
            }
        }
        
        return mappings
    }
    
    fun getRoleToPermissionMapping(): Map<String, List<String>> {
        val mappings = mutableMapOf<String, MutableList<String>>()
        val roleMappings = getRoleMappings()
        
        roleMappings.forEach { mapping ->
            val permission = mapping["permission"] as? String
            val roleId = mapping["discord_role_id"] as? String
            
            if (roleId != null && permission != null) {
                mappings.computeIfAbsent(roleId) { mutableListOf() }.add(permission)
            }
        }
        
        return mappings.mapValues { it.value.toList() }
    }
    
    fun isWebhooksEnabled(): Boolean = config.getBoolean("webhooks.enabled", false)
    fun getSecurityWebhook(): String = config.getString("webhooks.security_webhook") ?: ""
    fun getAuditWebhook(): String = config.getString("webhooks.audit_webhook") ?: ""
    
    fun isAPIEnabled(): Boolean = config.getBoolean("api.enabled", false)
    fun getAPIPort(): Int = config.getInt("api.port", 8080)
    fun getAPIHost(): String = config.getString("api.host") ?: "localhost"
    fun isAPIAuthRequired(): Boolean = config.getBoolean("api.auth_required", true)
    fun getAPIKey(): String = config.getString("api.api_key") ?: ""
    
    fun getThreadPoolCoreSize(): Int = config.getInt("performance.thread_pool.core_size", 2)
    fun getThreadPoolMaxSize(): Int = config.getInt("performance.thread_pool.max_size", 10)
    fun getThreadPoolQueueCapacity(): Int = config.getInt("performance.thread_pool.queue_capacity", 100)
    fun getThreadPoolKeepAlive(): Int = config.getInt("performance.thread_pool.keep_alive", 60)
    
    fun isCacheEnabled(): Boolean = config.getBoolean("performance.cache.enabled", true)
    fun getCacheMaxSize(): Int = config.getInt("performance.cache.max_size", 1000)
    fun getCacheExpireAfterWrite(): Int = config.getInt("performance.cache.expire_after_write", 300)
    fun getCacheExpireAfterAccess(): Int = config.getInt("performance.cache.expire_after_access", 600)
    
    fun getCleanupInterval(): Int = config.getInt("performance.cleanup.interval", 3600)
    fun getMaxOldLogs(): Int = config.getInt("performance.cleanup.max_old_logs", 1000)
    fun getMaxExpiredVerifications(): Int = config.getInt("performance.cleanup.max_expired_verifications", 100)
    
    fun isBruteForceProtectionEnabled(): Boolean = config.getBoolean("advanced_security.brute_force_protection.enabled", true)
    fun getBruteForceMaxAttempts(): Int = config.getInt("advanced_security.brute_force_protection.max_attempts", 5)
    fun getBruteForceWindowMinutes(): Int = config.getInt("advanced_security.brute_force_protection.window_minutes", 15)
    fun getBruteForceBanDuration(): Int = config.getInt("advanced_security.brute_force_protection.ban_duration", 3600)
    
    fun isAnomalyDetectionEnabled(): Boolean = config.getBoolean("advanced_security.anomaly_detection.enabled", true)
    fun isUnusualLoginTimesDetection(): Boolean = config.getBoolean("advanced_security.anomaly_detection.unusual_login_times", true)
    fun isMultipleIPDetection(): Boolean = config.getBoolean("advanced_security.anomaly_detection.multiple_ip_detection", true)
    fun isRapidActionDetection(): Boolean = config.getBoolean("advanced_security.anomaly_detection.rapid_action_detection", true)
    
    fun isHoneypotEnabled(): Boolean = config.getBoolean("advanced_security.honeypot.enabled", false)
    fun getHoneypotFakeEndpoints(): List<String> = config.getStringList("advanced_security.honeypot.fake_endpoints")
    
    fun getEncryptionAlgorithm(): String = config.getString("advanced_security.encryption.algorithm") ?: "AES-256"
    fun getSaltLength(): Int = config.getInt("advanced_security.encryption.salt_length", 16)
    fun getEncryptionIterations(): Int = config.getInt("advanced_security.encryption.iterations", 10000)
    
    fun isBackupEnabled(): Boolean = config.getBoolean("backup.enabled", true)
    fun isAutomaticBackup(): Boolean = config.getBoolean("backup.automatic", true)
    fun getBackupIntervalSeconds(): Int = config.getInt("backup.interval", 86400)
    fun getMaxBackupFiles(): Int = config.getInt("backup.max_backups", 7)
    fun isBackupCompression(): Boolean = config.getBoolean("backup.compression", true)
    fun isIncludeLogsInBackup(): Boolean = config.getBoolean("backup.include_logs", false)
    fun getBackupPath(): String = config.getString("backup.backup_path") ?: "backups/"
    
    fun isMaintenanceEnabled(): Boolean = config.getBoolean("maintenance.enabled", false)
    fun getMaintenanceMessage(): String = config.getString("maintenance.message") ?: "&cSunucu bakımda. Lütfen daha sonra tekrar deneyin."
    fun getAllowedMaintenancePlayers(): List<String> = config.getStringList("maintenance.allowed_players")
    fun getMaintenanceBypassPermission(): String = config.getString("maintenance.bypass_permission") ?: "discordlite.maintenance.bypass"
    
    fun getConfig(): FileConfiguration = config
    
    fun saveConfig() {
        plugin.saveConfig()
    }
    
    fun reloadConfig() {
        loadConfig()
    }
    
    fun setConfigValue(path: String, value: Any) {
        config.set(path, value)
        saveConfig()
    }
    
    fun hasConfigValue(path: String): Boolean {
        return config.contains(path)
    }
    
    fun getAllConfigPaths(): Set<String> {
        return config.getKeys(true)
    }
    
    fun getConfigSection(path: String): Map<String, Any> {
        val section = config.getConfigurationSection(path)
        return section?.getValues(false) ?: emptyMap()
    }
}