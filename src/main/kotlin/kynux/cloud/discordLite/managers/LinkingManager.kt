package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.data.LinkingResult
import kynux.cloud.discordLite.database.models.PlayerData
import kynux.cloud.discordLite.database.models.SecurityEventType
import kynux.cloud.discordLite.database.models.SecurityLog
import kynux.cloud.discordLite.utils.EmbedUtils
import kotlinx.coroutines.*
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class LinkingManager(private val plugin: DiscordLite) {
    
    private val pendingLinks = ConcurrentHashMap<String, PendingLink>()
    private val cooldowns = ConcurrentHashMap<String, Long>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    data class PendingLink(
        val playerUUID: String,
        val playerName: String,
        val linkCode: String,
        val createdAt: LocalDateTime,
        val expiresAt: LocalDateTime
    ) {
        fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)
        
        fun getRemainingSeconds(): Long {
            val now = LocalDateTime.now()
            return if (now.isBefore(expiresAt)) {
                java.time.Duration.between(now, expiresAt).seconds
            } else 0
        }
    }
    
    fun startVerification(player: Player): Boolean {
        val uuid = player.uniqueId.toString()
        
        if (isOnCooldown(uuid)) {
            val remainingTime = getCooldownRemaining(uuid)
            player.sendMessage(plugin.messageManager.getModernVerifyCooldown(remainingTime))
            return false
        }
        
        plugin.databaseManager.executeAsync {
            val existingData = plugin.databaseManager.provider.getPlayerData(uuid)
            if (existingData != null && !existingData.discordId.isNullOrBlank()) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(plugin.messageManager.getModernVerifyAlreadyLinked())
                })
                return@executeAsync
            }
            
            val verificationCode = generateLinkCode()
            val pendingLink = PendingLink(
                playerUUID = uuid,
                playerName = player.name,
                linkCode = verificationCode,
                createdAt = LocalDateTime.now(),
                expiresAt = LocalDateTime.now().plusSeconds(300) // 5 dakika
            )
            
            pendingLinks[uuid] = pendingLink
            setCooldown(uuid)
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage(plugin.messageManager.getModernVerifyStarted(verificationCode))
            })
            
            plugin.databaseManager.provider.saveSecurityLog(SecurityLog.create(
                eventType = SecurityEventType.ACCOUNT_LINKED,
                description = "Hesap doğrulama başlatıldı",
                playerUUID = uuid,
                details = "Doğrulama kodu: $verificationCode"
            ))
            
            startTimeoutTask(uuid)
        }
        
        return true
    }
    
    suspend fun completeLinking(discordUserId: String, linkCode: String): LinkResult {
    val pendingLink = pendingLinks.values.find { it.linkCode == linkCode }
        ?: return LinkResult.InvalidCode
    
    if (pendingLink.isExpired()) {
        pendingLinks.remove(pendingLink.playerUUID)
        return LinkResult.Expired
    }
    
    val existingPlayer = plugin.databaseManager.provider.getPlayerDataByDiscordId(discordUserId)
    if (existingPlayer != null) {
        return LinkResult.DiscordAlreadyLinked
    }
        
        try {
            var playerData = plugin.databaseManager.provider.getPlayerData(pendingLink.playerUUID)
            if (playerData == null) {
                playerData = PlayerData(
                    uuid = pendingLink.playerUUID,
                    discordId = null,
                    minecraftUsername = pendingLink.playerName,
                    twoFAEnabled = false,
                    linkedAt = null,
                    lastLogin = null
                )
            }
            
            val linkedPlayerData = playerData.copy(
                discordId = discordUserId,
                linkedAt = LocalDateTime.now()
            )
            
            plugin.databaseManager.provider.savePlayerData(linkedPlayerData)
            
            pendingLinks.remove(pendingLink.playerUUID)
            
            plugin.databaseManager.provider.saveSecurityLog(SecurityLog.create(
                eventType = SecurityEventType.ACCOUNT_LINKED,
                description = "Hesap eşleme tamamlandı",
                playerUUID = pendingLink.playerUUID,
                details = "Discord ID: $discordUserId"
            ))
            
            val onlinePlayer = plugin.server.getPlayer(java.util.UUID.fromString(pendingLink.playerUUID))
            onlinePlayer?.let { player ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(plugin.messageManager.getModernVerifySuccess())
                })
            }
            
            // plugin.permissionManager.updatePlayerRoles(pendingLink.playerUUID, discordUserId)
            
            plugin.logger.info("Hesap doğrulama tamamlandı: ${pendingLink.playerName} (${pendingLink.playerUUID}) -> Discord: $discordUserId")
            
            return LinkResult.Success(linkedPlayerData)
            
        } catch (e: Exception) {
            plugin.logger.severe("Hesap eşleme hatası: ${e.message}")
            e.printStackTrace()
            return LinkResult.DatabaseError
        }
    }
    
    suspend fun unlinkAccount(playerUUID: String): Boolean {
        return try {
            val playerData = plugin.databaseManager.provider.getPlayerData(playerUUID)
                ?: return false
            
            if (playerData.discordId.isNullOrBlank()) {
                return false
            }
            
            val discordId = playerData.discordId!!
            
            // plugin.permissionManager.removePlayerRoles(discordId)
            
            val unlinkedData = playerData.copy(
                discordId = null,
                twoFAEnabled = false
            )
            plugin.databaseManager.provider.savePlayerData(unlinkedData)
            
            // Security log
            plugin.databaseManager.provider.saveSecurityLog(SecurityLog.create(
                eventType = SecurityEventType.ACCOUNT_UNLINKED,
                description = "Hesap eşleme kaldırıldı",
                playerUUID = playerUUID,
                details = "Discord ID: $discordId"
            ))
            
            val onlinePlayer = plugin.server.getPlayer(java.util.UUID.fromString(playerUUID))
            onlinePlayer?.let { player ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(plugin.messageManager.getModernUnlinkSuccess())
                })
            }
            
            plugin.logger.info("Hesap eşleme kaldırıldı: $playerUUID (Discord: $discordId)")
            true
            
        } catch (e: Exception) {
            plugin.logger.severe("Hesap eşleme kaldırma hatası: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    fun getPendingLink(playerUUID: String): PendingLink? {
        return pendingLinks[playerUUID]?.takeIf { !it.isExpired() }
    }
    
    fun cancelPendingLink(playerUUID: String): Boolean {
        return pendingLinks.remove(playerUUID) != null
    }
    
    fun completeLinkingSync(linkCode: String, discordUserId: String): LinkingResult {
        return try {
            var result: LinkingResult
            kotlinx.coroutines.runBlocking {
                val linkResult = completeLinking(discordUserId, linkCode)
                result = when (linkResult) {
                    is LinkResult.Success -> LinkingResult(true, "Hesap başarıyla eşleştirildi!", linkResult.playerData.minecraftUsername, linkResult.playerData)
                    LinkResult.InvalidCode -> LinkingResult(false, "Geçersiz veya süresi dolmuş kod!", null, null)
                    LinkResult.Expired -> LinkingResult(false, "Kod süresi dolmuş! Yeni bir kod oluşturun.", null, null)
                    LinkResult.DiscordAlreadyLinked -> LinkingResult(false, "Bu Discord hesabı zaten eşleştirilmiş!", null, null)
                    LinkResult.DatabaseError -> LinkingResult(false, "Veritabanı hatası! Eşleme kaydedilemedi.", null, null)
                }
            }
            result
        } catch (e: Exception) {
            plugin.logger.severe("completeLinking wrapper hatası: ${e.message}")
            LinkingResult(false, "İşlem sırasında hata oluştu!")
        }
    }
    
    fun cancelLinking(linkCode: String) {
        val pendingLink = pendingLinks.values.find { it.linkCode == linkCode }
        if (pendingLink != null) {
            pendingLinks.remove(pendingLink.playerUUID)
            plugin.logger.info("Linking iptal edildi: ${pendingLink.playerName} (kod: $linkCode)")
        }
    }
    
    
    private fun generateLinkCode(): String {
        var code: String
        do {
            code = Random.nextInt(100000, 999999).toString()
        } while (pendingLinks.values.any { it.linkCode == code })
        return code
    }
    
    private fun isOnCooldown(playerUUID: String): Boolean {
        val cooldownEnd = cooldowns[playerUUID] ?: return false
        return System.currentTimeMillis() < cooldownEnd
    }
    
    private fun setCooldown(playerUUID: String) {
        if (plugin.configManager.isLinkingCooldownEnabled()) {
            val cooldownMs = plugin.configManager.getLinkingCooldown() * 1000L
            cooldowns[playerUUID] = System.currentTimeMillis() + cooldownMs
        }
    }
    
    private fun getCooldownRemaining(playerUUID: String): Long {
        val cooldownEnd = cooldowns[playerUUID] ?: return 0
        val remaining = (cooldownEnd - System.currentTimeMillis()) / 1000
        return maxOf(0, remaining)
    }
    
    private fun startTimeoutTask(playerUUID: String) {
        coroutineScope.launch {
            delay(300_000) // 5dk
            
            val pendingLink = pendingLinks[playerUUID]
            if (pendingLink != null && pendingLink.isExpired()) {
                pendingLinks.remove(playerUUID)
                
                val onlinePlayer = plugin.server.getPlayer(java.util.UUID.fromString(playerUUID))
                onlinePlayer?.let { player ->
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.sendMessage(plugin.messageManager.getModernVerifyTimeout())
                    })
                }
                
                plugin.logger.info("Doğrulama kodu süresi doldu: ${pendingLink.playerName} (${pendingLink.linkCode})")
            }
        }
    }
    
    fun cleanup() {
        val expired = pendingLinks.filter { it.value.isExpired() }
        expired.forEach { (playerUUID, _) ->
            pendingLinks.remove(playerUUID)
        }
        
        val currentTime = System.currentTimeMillis()
        cooldowns.entries.removeIf { it.value < currentTime }
        
        if (expired.isNotEmpty()) {
            plugin.logger.info("${expired.size} süresi dolmuş eşleme kodu temizlendi")
        }
    }
    
    fun shutdown() {
        coroutineScope.cancel()
        pendingLinks.clear()
        cooldowns.clear()
    }
    
    fun getStats(): LinkingStats {
        return LinkingStats(
            activePendingLinks = pendingLinks.size,
            activeCooldowns = cooldowns.count { it.value > System.currentTimeMillis() }
        )
    }
    
    data class LinkingStats(
        val activePendingLinks: Int,
        val activeCooldowns: Int
    )
    
    sealed class LinkResult {
        object InvalidCode : LinkResult()
        object Expired : LinkResult()
        object DiscordAlreadyLinked : LinkResult()
        object DatabaseError : LinkResult()
        
        data class Success(val playerData: PlayerData) : LinkResult()
    }
}