# 🔌 DiscordLite API Dokümantasyonu

Bu dokümantasyon DiscordLite plugin'inin API'sini ve diğer plugin'lerle entegrasyon yöntemlerini açıklamaktadır.

## 📚 API Genel Bakış

DiscordLite, diğer plugin'lerin Discord entegrasyonu ve güvenlik özelliklerini kullanabilmesi için kapsamlı bir API sunar.

### Temel Erişim
```kotlin
// Plugin instance'ını alma
val discordLite = DiscordLite.instance

// Manager'lara erişim
val linkingManager = discordLite.linkingManager
val twoFAManager = discordLite.twoFAManager
val securityManager = discordLite.securityManager
```

## 🔗 Linking API

### Hesap Bağlama Yönetimi
```kotlin
class LinkingManager {
    
    // Oyuncu bağlantı durumunu kontrol et
    suspend fun isPlayerLinked(playerUUID: UUID): Boolean
    
    // Discord ID'yi al
    suspend fun getDiscordId(playerUUID: UUID): String?
    
    // Minecraft UUID'yi al
    suspend fun getMinecraftUUID(discordId: String): UUID?
    
    // Hesap bağla
    suspend fun linkAccount(playerUUID: UUID, discordId: String): LinkResult
    
    // Hesap bağlantısını kaldır
    suspend fun unlinkAccount(playerUUID: UUID): Boolean
    
    // Tüm bağlantıları al
    suspend fun getAllLinks(): List<PlayerLink>
    
    // Bağlantı geçmişini al
    suspend fun getLinkHistory(playerUUID: UUID): List<LinkHistory>
}

// Data sınıfları
data class PlayerLink(
    val playerUUID: UUID,
    val discordId: String,
    val linkedAt: Instant,
    val verifiedAt: Instant?
)

data class LinkResult(
    val success: Boolean,
    val message: String,
    val errorCode: String? = null
)

data class LinkHistory(
    val playerUUID: UUID,
    val discordId: String,
    val action: LinkAction, // LINK, UNLINK, VERIFY
    val timestamp: Instant,
    val reason: String?
)
```

### Kullanım Örnekleri
```kotlin
// Temel bağlantı kontrolü
val isLinked = discordLite.linkingManager.isPlayerLinked(player.uniqueId)
if (isLinked) {
    player.sendMessage("Discord hesabınız bağlı!")
}

// Discord ID alma
val discordId = discordLite.linkingManager.getDiscordId(player.uniqueId)
discordId?.let {
    player.sendMessage("Discord ID'niz: $it")
}

// Programatik hesap bağlama
val result = discordLite.linkingManager.linkAccount(
    playerUUID = player.uniqueId,
    discordId = "123456789012345678"
)
if (result.success) {
    player.sendMessage("Hesap başarıyla bağlandı!")
}
```

## 🔒 2FA API

### İki Faktörlü Kimlik Doğrulama
```kotlin
class TwoFAManager {
    
    // 2FA durumunu kontrol et
    fun is2FARequired(player: Player): Boolean
    
    // 2FA süreci başlat
    suspend fun start2FAProcess(player: Player): TwoFAResult
    
    // 2FA doğrulama
    suspend fun verify2FA(player: Player, verificationCode: String): VerificationResult
    
    // Bekleyen 2FA isteklerini al
    fun getPending2FARequests(): List<Pending2FA>
    
    // 2FA isteğini iptal et
    suspend fun cancel2FA(player: Player): Boolean
    
    // 2FA istatistikleri
    suspend fun get2FAStats(player: Player): TwoFAStats
}

// Data sınıfları
data class TwoFAResult(
    val success: Boolean,
    val message: String,
    val expiresAt: Instant?
)

data class VerificationResult(
    val success: Boolean,
    val message: String,
    val attempts: Int,
    val maxAttempts: Int
)

data class Pending2FA(
    val playerUUID: UUID,
    val discordId: String,
    val startedAt: Instant,
    val expiresAt: Instant,
    val attempts: Int
)

data class TwoFAStats(
    val totalAttempts: Int,
    val successfulAttempts: Int,
    val lastVerification: Instant?,
    val averageResponseTime: Long // milliseconds
)
```

### Kullanım Örnekleri
```kotlin
// 2FA gereksinimi kontrolü
if (discordLite.twoFAManager.is2FARequired(player)) {
    // 2FA süreci başlat
    val result = discordLite.twoFAManager.start2FAProcess(player)
    if (result.success) {
        player.sendMessage("2FA doğrulama kodunuz Discord'a gönderildi!")
    }
}

// Manuel 2FA doğrulama
val verification = discordLite.twoFAManager.verify2FA(player, "123456")
if (verification.success) {
    player.sendMessage("2FA başarıyla doğrulandı!")
} else {
    player.sendMessage("Geçersiz kod! ${verification.attempts}/${verification.maxAttempts}")
}
```

## 🛡️ Security API

### Güvenlik Yönetimi
```kotlin
class SecurityManager {
    
    // Güvenlik olayı kaydet
    suspend fun logSecurityEvent(
        event: SecurityEventType,
        player: Player?,
        details: Map<String, Any> = emptyMap()
    )
    
    // Oyuncu risk skorunu al
    suspend fun getPlayerRiskScore(player: Player): RiskScore
    
    // IP ban durumunu kontrol et
    suspend fun isIPBanned(ipAddress: String): Boolean
    
    // Şüpheli aktivite rapor et
    suspend fun reportSuspiciousActivity(
        player: Player,
        activity: SuspiciousActivity
    )
    
    // Güvenlik istatistikleri
    suspend fun getSecurityStats(): SecurityStats
    
    // Tehdit seviyesi al
    fun getCurrentThreatLevel(): ThreatLevel
}

// Enums ve Data sınıfları
enum class SecurityEventType {
    LOGIN_SUCCESS, LOGIN_FAILED, SUSPICIOUS_LOGIN,
    ACCOUNT_LINKED, ACCOUNT_UNLINKED,
    TWO_FA_SUCCESS, TWO_FA_FAILED,
    PERMISSION_VIOLATION, COMMAND_BLOCKED,
    IP_BAN_TRIGGERED, RATE_LIMIT_EXCEEDED,
    // ... diğer event türleri
}

data class RiskScore(
    val score: Int, // 0-100
    val level: RiskLevel, // LOW, MEDIUM, HIGH, CRITICAL
    val factors: List<RiskFactor>
)

data class RiskFactor(
    val type: String,
    val score: Int,
    val description: String
)

enum class ThreatLevel {
    NONE, LOW, ELEVATED, HIGH, SEVERE
}
```

### Kullanım Örnekleri
```kotlin
// Güvenlik olayı loglama
discordLite.securityManager.logSecurityEvent(
    event = SecurityEventType.LOGIN_SUCCESS,
    player = player,
    details = mapOf(
        "ip_address" to player.address?.address?.hostAddress,
        "login_time" to Instant.now().toString()
    )
)

// Risk skoru kontrolü
val riskScore = discordLite.securityManager.getPlayerRiskScore(player)
if (riskScore.level == RiskLevel.HIGH) {
    player.sendMessage("Hesabınızda şüpheli aktivite tespit edildi!")
    // Ek güvenlik önlemleri al
}

// Şüpheli aktivite raporlama
discordLite.securityManager.reportSuspiciousActivity(
    player = player,
    activity = SuspiciousActivity(
        type = "MULTIPLE_RAPID_COMMANDS",
        description = "Player executed 20 commands in 5 seconds",
        severity = ActivitySeverity.MEDIUM
    )
)
```

## 📊 Validation API

### Input Doğrulama
```kotlin
class ValidationManager {
    
    // Genel input doğrulama
    fun validateInput(
        input: String,
        type: InputType,
        maxLength: Int = 1000,
        allowEmpty: Boolean = false,
        sanitize: Boolean = true
    ): ValidationResult
    
    // Hızlı doğrulama metodları
    fun isValidUsername(username: String): Boolean
    fun isValidDiscordId(discordId: String): Boolean
    fun isValidIPAddress(ip: String): Boolean
    
    // Sanitization metodları
    fun sanitizeMessage(message: String): String
    fun sanitizeCommand(command: String): String
    
    // Oyuncu eylemi doğrulama
    fun validatePlayerAction(
        player: Player,
        action: String,
        context: Map<String, Any> = emptyMap()
    ): ValidationResult
}

// Data sınıfları
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val sanitizedValue: String? = null,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val detectedThreats: List<String> = emptyList()
)

enum class InputType {
    USERNAME, DISCORD_ID, IP_ADDRESS, COMMAND, 
    MESSAGE, URL, EMAIL, UUID, JSON, SQL
}
```

### Kullanım Örnekleri
```kotlin
// Input doğrulama
val validation = discordLite.validationManager.validateInput(
    input = userInput,
    type = InputType.MESSAGE,
    maxLength = 500,
    sanitize = true
)

if (validation.isValid) {
    val safeMessage = validation.sanitizedValue ?: userInput
    // Güvenli mesajı kullan
} else {
    player.sendMessage("Geçersiz input: ${validation.errorMessage}")
}

// Hızlı username kontrolü
if (!discordLite.validationManager.isValidUsername(playerName)) {
    player.sendMessage("Geçersiz kullanıcı adı!")
    return
}
```

## 🎯 Event API

### Custom Events
```kotlin
// Discord hesap bağlama eventi
class DiscordLinkEvent(
    val player: Player,
    val discordId: String,
    val linkType: LinkType // MANUAL, AUTOMATIC, ADMIN
) : PlayerEvent(player) {
    
    companion object {
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
        
        private val handlers = HandlerList()
    }
    
    override fun getHandlers(): HandlerList = getHandlerList()
}

// 2FA doğrulama eventi
class TwoFactorAuthEvent(
    val player: Player,
    val isSuccess: Boolean,
    val attempts: Int,
    val responseTime: Long
) : PlayerEvent(player) {
    
    companion object {
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
        
        private val handlers = HandlerList()
    }
    
    override fun getHandlers(): HandlerList = getHandlerList()
}

// Güvenlik eventi
class SecurityEvent(
    val eventType: SecurityEventType,
    val player: Player?,
    val riskLevel: RiskLevel,
    val details: Map<String, Any>
) : Event() {
    
    companion object {
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
        
        private val handlers = HandlerList()
    }
    
    override fun getHandlers(): HandlerList = getHandlerList()
}
```

### Event Handling Örnekleri
```kotlin
// Discord bağlama event listener
@EventHandler
fun onDiscordLink(event: DiscordLinkEvent) {
    val player = event.player
    val discordId = event.discordId
    
    // Discord rollerini senkronize et
    syncDiscordRoles(player, discordId)
    
    // Hoş geldin mesajı gönder
    player.sendMessage("Discord hesabınız başarıyla bağlandı!")
    
    // Başarı log'u
    logger.info("${player.name} linked Discord account: $discordId")
}

// 2FA doğrulama event listener
@EventHandler
fun onTwoFactorAuth(event: TwoFactorAuthEvent) {
    val player = event.player
    
    if (event.isSuccess) {
        // Başarılı 2FA sonrası işlemler
        giveWelcomeRewards(player)
        updateLastLoginTime(player)
    } else {
        // Başarısız 2FA işlemleri
        if (event.attempts >= 3) {
            player.kickPlayer("Çok fazla başarısız 2FA denemesi!")
        }
    }
}

// Güvenlik event listener
@EventHandler
fun onSecurityEvent(event: SecurityEvent) {
    when (event.riskLevel) {
        RiskLevel.HIGH, RiskLevel.CRITICAL -> {
            // Yüksek risk durumunda otomatik önlemler
            event.player?.let { player ->
                temporarilyRestrictPlayer(player)
                notifyAdmins("High risk activity detected: ${player.name}")
            }
        }
        else -> {
            // Normal risk seviyesi
        }
    }
}
```

## 🔧 Configuration API

### Konfigürasyon Erişimi
```kotlin
class ConfigManager {
    
    // Discord ayarları
    fun getBotToken(): String
    fun getGuildId(): String
    fun getLogChannelId(): String?
    
    // 2FA ayarları
    fun is2FAEnabled(): Boolean
    fun get2FATimeout(): Int
    fun is2FARequiredOnJoin(): Boolean
    
    // Güvenlik ayarları
    fun isIPBanEnabled(): Boolean
    fun isRateLimitingEnabled(): Boolean
    fun isThreatDetectionEnabled(): Boolean
    
    // Database ayarları
    fun getDatabaseType(): DatabaseType
    fun getDatabaseConfig(): DatabaseConfig
    
    // Permission ayarları
    fun isDiscordSyncEnabled(): Boolean
    fun getRoleMappings(): Map<String, String>
    
    // Logging ayarları
    fun isDiscordLoggingEnabled(): Boolean
    fun isFileLoggingEnabled(): Boolean
    fun getLogLevel(): LogLevel
}
```

### Kullanım Örnekleri
```kotlin
// Konfigürasyon kontrolü
if (discordLite.configManager.is2FAEnabled()) {
    // 2FA işlemleri
}

// Rol eşleştirme
val roleMappings = discordLite.configManager.getRoleMappings()
val minecraftGroup = roleMappings[discordRoleId]

// Database tipi kontrolü
when (discordLite.configManager.getDatabaseType()) {
    DatabaseType.SQLITE -> {
        // SQLite specific operations
    }
    DatabaseType.MYSQL -> {
        // MySQL specific operations
    }
    DatabaseType.YAML -> {
        // YAML specific operations
    }
}
```

## 💽 Database API

### Veritabanı İşlemleri
```kotlin
interface DatabaseProvider {
    
    // Player işlemleri
    suspend fun getPlayer(uuid: UUID): PlayerData?
    suspend fun savePlayer(playerData: PlayerData)
    suspend fun deletePlayer(uuid: UUID)
    
    // Link işlemleri
    suspend fun getLink(uuid: UUID): PlayerLink?
    suspend fun saveLink(link: PlayerLink)
    suspend fun deleteLink(uuid: UUID)
    
    // Security log işlemleri
    suspend fun saveSecurityLog(log: SecurityLog)
    suspend fun getSecurityLogs(
        player: UUID? = null,
        eventType: SecurityEventType? = null,
        limit: Int = 100
    ): List<SecurityLog>
    
    // Ban işlemleri
    suspend fun saveBan(ban: IPBan)
    suspend fun getBan(ipAddress: String): IPBan?
    suspend fun deleteBan(ipAddress: String)
    
    // İstatistik işlemleri
    suspend fun getStats(): DatabaseStats
}

// Custom database operations
class CustomDatabaseManager {
    
    suspend fun executeCustomQuery(query: String, params: List<Any>): List<Map<String, Any>>
    
    suspend fun executeCustomUpdate(query: String, params: List<Any>): Int
    
    suspend fun withTransaction(block: suspend (DatabaseProvider) -> Unit)
}
```

### Kullanım Örnekleri
```kotlin
// Player data alma
val playerData = discordLite.databaseManager.provider.getPlayer(player.uniqueId)
playerData?.let {
    player.sendMessage("Son giriş: ${it.lastLogin}")
}

// Custom query çalıştırma
val results = discordLite.databaseManager.custom.executeCustomQuery(
    query = "SELECT * FROM player_links WHERE created_at > ?",
    params = listOf(Instant.now().minus(Duration.ofDays(7)))
)

// Transaction kullanımı
discordLite.databaseManager.custom.withTransaction { provider ->
    provider.savePlayer(playerData)
    provider.saveLink(playerLink)
    provider.saveSecurityLog(securityLog)
}
```

## 🎨 Message API

### Mesaj Sistemi
```kotlin
class MessageManager {
    
    // Mesaj alma
    fun getMessage(key: String, player: Player? = null): String
    fun getMessage(key: String, language: String): String
    
    // Placeholder ile mesaj
    fun getMessageWithPlaceholders(
        key: String,
        placeholders: Map<String, String>,
        player: Player? = null
    ): String
    
    // Formatted mesaj gönderme
    fun sendMessage(player: Player, key: String, placeholders: Map<String, String> = emptyMap())
    fun sendMessage(sender: CommandSender, key: String, placeholders: Map<String, String> = emptyMap())
    
    // Broadcast mesaj
    fun broadcast(key: String, placeholders: Map<String, String> = emptyMap())
    fun broadcastToPermission(permission: String, key: String, placeholders: Map<String, String> = emptyMap())
}
```

### Kullanım Örnekleri
```kotlin
// Basit mesaj gönderme
discordLite.messageManager.sendMessage(player, "link.success")

// Placeholder ile mesaj
discordLite.messageManager.sendMessage(
    player = player,
    key = "admin.player_unlinked",
    placeholders = mapOf("player" to targetPlayer.name)
)

// Özel dilde mesaj alma
val message = discordLite.messageManager.getMessage("errors.database_error", "en")

// Broadcast mesaj
discordLite.messageManager.broadcastToPermission(
    permission = "discordlite.admin",
    key = "admin.security_alert",
    placeholders = mapOf(
        "player" to player.name,
        "threat" to "HIGH_RISK_ACTIVITY"
    )
)
```

## 🔌 Plugin Integration Examples

### Diğer Plugin'lerle Entegrasyon

#### LuckPerms Entegrasyonu
```kotlin
// LuckPerms ile grup senkronizasyonu
class LuckPermsIntegration {
    
    @EventHandler
    fun onDiscordLink(event: DiscordLinkEvent) {
        val luckPerms = LuckPermsProvider.get()
        val user = luckPerms.userManager.getUser(event.player.uniqueId)
        
        user?.let {
            // Discord rollerini LuckPerms gruplarına çevir
            val discordRoles = getDiscordRoles(event.discordId)
            syncGroupsWithDiscordRoles(it, discordRoles)
        }
    }
    
    private fun syncGroupsWithDiscordRoles(user: User, discordRoles: List<String>) {
        val roleMappings = discordLite.configManager.getRoleMappings()
        
        discordRoles.forEach { roleId ->
            val group = roleMappings[roleId]
            if (group != null && !user.inheritanceGroupMembership.contains(group)) {
                user.data().add(InheritanceNode.builder(group).build())
            }
        }
    }
}
```

#### PlaceholderAPI Entegrasyonu
```kotlin
// PlaceholderAPI expansion
class DiscordLitePlaceholders : PlaceholderExpansion() {
    
    override fun getIdentifier(): String = "discordlite"
    override fun getAuthor(): String = "YourName"
    override fun getVersion(): String = "1.0.0"
    
    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) return null
        
        return when (params) {
            "is_linked" -> {
                if (discordLite.linkingManager.isPlayerLinked(player.uniqueId)) "Yes" else "No"
            }
            "discord_id" -> {
                discordLite.linkingManager.getDiscordId(player.uniqueId) ?: "Not Linked"
            }
            "2fa_status" -> {
                if (discordLite.twoFAManager.is2FARequired(player)) "Required" else "Not Required"
            }
            "risk_score" -> {
                val riskScore = discordLite.securityManager.getPlayerRiskScore(player)
                riskScore.score.toString()
            }
            else -> null
        }
    }
}
```

#### Vault Economy Entegrasyonu
```kotlin
// Economy ile ödül sistemi
class EconomyIntegration {
    
    @EventHandler
    fun onDiscordLink(event: DiscordLinkEvent) {
        val economy = getEconomy() // Vault economy
        
        // Discord bağlama ödülü
        val reward = discordLite.configManager.getLinkReward()
        economy.depositPlayer(event.player, reward)
        
        discordLite.messageManager.sendMessage(
            player = event.player,
            key = "rewards.link_bonus",
            placeholders = mapOf("amount" to reward.toString())
        )
    }
    
    @EventHandler
    fun onTwoFactorAuth(event: TwoFactorAuthEvent) {
        if (event.isSuccess) {
            val economy = getEconomy()
            val reward = discordLite.configManager.get2FAReward()
            economy.depositPlayer(event.player, reward)
        }
    }
}
```

## 📊 Statistics API

### İstatistik Toplama
```kotlin
class StatisticsManager {
    
    // Genel istatistikler
    suspend fun getGeneralStats(): GeneralStats
    
    // Oyuncu istatistikleri
    suspend fun getPlayerStats(player: Player): PlayerStats
    
    // Güvenlik istatistikleri
    suspend fun getSecurityStats(): SecurityStats
    
    // Performance istatistikleri
    suspend fun getPerformanceStats(): PerformanceStats
}

data class GeneralStats(
    val totalPlayers: Int,
    val linkedPlayers: Int,
    val activeLinks: Int,
    val totalLogins: Int,
    val successful2FA: Int,
    val failed2FA: Int
)

data class PlayerStats(
    val playerUUID: UUID,
    val totalLogins: Int,
    val lastLogin: Instant?,
    val linkStatus: LinkStatus,
    val twoFAStats: TwoFAStats,
    val riskScore: RiskScore
)
```

## 🔒 Security Best Practices

### API Güvenliği
```kotlin
// API kullanırken güvenlik kontrolleri
class SecureAPIUsage {
    
    fun safePlayerLookup(playerInput: String): Player? {
        // Input doğrulama
        val validation = discordLite.validationManager.validateInput(
            input = playerInput,
            type = InputType.USERNAME
        )
        
        if (!validation.isValid) {
            logger.warning("Invalid player input: ${validation.errorMessage}")
            return null
        }
        
        return Bukkit.getPlayer(validation.sanitizedValue!!)
    }
    
    fun secureCommandExecution(player: Player, command: String) {
        // Komut doğrulama
        val validation = discordLite.validationManager.validateInput(
            input = command,
            type = InputType.COMMAND
        )
        
        if (!validation.isValid) {
            discordLite.securityManager.logSecurityEvent(
                event = SecurityEventType.COMMAND_BLOCKED,
                player = player,
                details = mapOf(
                    "command" to command,
                    "reason" to validation.errorMessage!!
                )
            )
            return
        }
        
        // Güvenli komut çalıştırma
        Bukkit.dispatchCommand(player, validation.sanitizedValue!!)
    }
}
```

## 📞 Destek ve Kaynak Linkler

### Dökümantasyon Linkleri
- [Setup Guide](SETUP.md) - Kurulum kılavuzu
- [Configuration](CONFIG.md) - Konfigürasyon rehberi
- [Troubleshooting](TROUBLESHOOTING.md) - Sorun giderme
- [Contributing](CONTRIBUTING.md) - Katkıda bulunma

### Örnek Projeler
```kotlin
// GitHub'da örnek entegrasyon projeleri:
// - DiscordLite-LuckPerms-Integration
// - DiscordLite-PlaceholderAPI-Expansion  
// - DiscordLite-Economy-Rewards
// - DiscordLite-Custom-Commands
```

### Community Resources
- [Discord Sunucusu](https://discord.gg/bgHexr9rk5)
- [GitHub Discussions](https://github.com/KynuxDev/DiscordLite/discussions)
- [Wiki](https://github.com/KynuxDev/DiscordLite/wiki)
- [Issues](https://github.com/KynuxDev/DiscordLite/issues)

Bu API dokümantasyonu, DiscordLite'ın sunduğu tüm programatik özellikler için kapsamlı bir rehber sağlar. Herhangi bir sorunuz varsa community kaynaklarımıza başvurabilirsiniz.