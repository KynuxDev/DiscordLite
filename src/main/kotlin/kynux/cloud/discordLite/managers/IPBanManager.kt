package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.models.IPBan
import kynux.cloud.discordLite.database.models.SecurityEventType
import kynux.cloud.discordLite.database.models.SecurityLog
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class IPBanManager(private val plugin: DiscordLite) {
    
    private val failedAttempts = ConcurrentHashMap<String, FailedAttempt>()
    private val whitelistedIPs = mutableSetOf<String>()
    
    companion object {
        private const val MAX_FAILED_ATTEMPTS = 3
        private const val ATTEMPT_RESET_TIME = 300000L // 5dk
        private const val AUTO_BAN_DURATION = 3600 // 1 saat
    }
    
    data class FailedAttempt(
        val count: Int,
        val lastAttempt: Long,
        val firstAttempt: Long
    )
    
    fun initialize() {
        plugin.logger.info("IPBanManager başlatılıyor...")
        
        loadWhitelistedIPs()
        
        plugin.databaseManager.executeAsync {
            try {
                plugin.databaseManager.provider.deleteExpiredIPBans()
            } catch (e: Exception) {
                plugin.logger.warning("Süresi dolmuş IP ban temizleme hatası: ${e.message}")
            }
        }
        
        startCleanupTask()
        
        plugin.logger.info("IPBanManager başarıyla başlatıldı!")
    }
    
    fun shutdown() {
        plugin.logger.info("IPBanManager kapatılıyor...")
        failedAttempts.clear()
        plugin.logger.info("IPBanManager kapatıldı!")
    }

    fun banIP(ipAddress: String, reason: String, bannedBy: String, durationSeconds: Int? = null): Boolean {
        if (isWhitelisted(ipAddress)) {
            plugin.logger.warning("Whitelist'te olan IP ban edilemez: $ipAddress")
            return false
        }

        return plugin.databaseManager.executeSync {
            try {
                val existingBan = plugin.databaseManager.provider.getIPBan(ipAddress)
                if (existingBan != null && !existingBan.isExpired()) {
                    plugin.logger.warning("IP zaten banli: $ipAddress")
                    return@executeSync false
                }

                val ipBan = IPBan.create(ipAddress, reason, bannedBy, durationSeconds)
                plugin.databaseManager.provider.saveIPBan(ipBan)

                kickPlayersWithIP(ipAddress, "IP adresiniz banlandı: $reason")

                plugin.databaseManager.provider.saveSecurityLog(
                    SecurityLog.createIPBan(
                        ipAddress = ipAddress,
                        reason = reason,
                        bannedBy = bannedBy,
                        duration = durationSeconds?.toLong()
                    )
                )

                plugin.discordManager.sendSecurityAlert(
                    "🚫 IP Ban İşlemi",
                    "Yeni IP adresi banlandı",
                    "**IP:** `$ipAddress`\n**Sebep:** $reason\n**Ban Veren:** $bannedBy\n**Süre:** ${durationSeconds?.let { "$it saniye" } ?: "Kalıcı"}\n**Zaman:** ${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
                )

                plugin.logger.info("IP banlandı: $ipAddress - Sebep: $reason")
                true
            } catch (e: Exception) {
                plugin.logger.severe("IP ban hatası: ${e.message}")
                false
            }
        } ?: false
    }

    fun unbanIP(ipAddress: String, unbannedBy: String): Boolean {
        return plugin.databaseManager.executeSync {
            try {
                val ipBan = plugin.databaseManager.provider.getIPBan(ipAddress)
                if (ipBan == null || ipBan.isExpired()) {
                    plugin.logger.warning("Aktif IP ban bulunamadı: $ipAddress")
                    return@executeSync false
                }

                plugin.databaseManager.provider.deleteIPBan(ipAddress)

                plugin.databaseManager.provider.saveSecurityLog(
                    SecurityLog.createIPUnban(
                        ipAddress = ipAddress,
                        unbannedBy = unbannedBy
                    )
                )

                plugin.discordManager.sendLogMessage(
                    "✅ IP Ban Kaldırıldı",
                    "**IP:** `$ipAddress`\n**Ban Kaldıran:** $unbannedBy\n**Zaman:** ${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
                )

                plugin.logger.info("IP ban kaldırıldı: $ipAddress")
                true
            } catch (e: Exception) {
                plugin.logger.severe("IP unban hatası: ${e.message}")
                false
            }
        } ?: false
    }

    fun isIPBanned(ipAddress: String): Boolean {
        if (isWhitelisted(ipAddress)) {
            return false
        }

        return plugin.databaseManager.executeSync {
            try {
                val ipBan = plugin.databaseManager.provider.getIPBan(ipAddress)
                ipBan != null && !ipBan.isExpired()
            } catch (e: Exception) {
                plugin.logger.warning("IP ban kontrolü hatası: ${e.message}")
                false
            }
        } ?: false
    }

    fun getIPBanInfo(ipAddress: String): IPBan? {
        return plugin.databaseManager.executeSync {
            try {
                val ipBan = plugin.databaseManager.provider.getIPBan(ipAddress)
                if (ipBan != null && !ipBan.isExpired()) ipBan else null
            } catch (e: Exception) {
                plugin.logger.warning("IP ban bilgi hatası: ${e.message}")
                null
            }
        }
    }

    fun recordFailedAttempt(ipAddress: String, reason: String) {
        if (isWhitelisted(ipAddress)) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val existing = failedAttempts[ipAddress]
        
        val newAttempt = if (existing != null && (currentTime - existing.lastAttempt) < ATTEMPT_RESET_TIME) {
            existing.copy(
                count = existing.count + 1,
                lastAttempt = currentTime
            )
        } else {
            FailedAttempt(1, currentTime, currentTime)
        }
        
        failedAttempts[ipAddress] = newAttempt
        
        plugin.logger.warning("Başarısız giriş denemesi: $ipAddress - ${newAttempt.count}/$MAX_FAILED_ATTEMPTS - Sebep: $reason")
        
        if (newAttempt.count >= MAX_FAILED_ATTEMPTS) {
            val success = banIP(
                ipAddress = ipAddress,
                reason = "Çoklu başarısız giriş denemesi (${newAttempt.count} deneme)",
                bannedBy = "DiscordLite-AutoBan",
                durationSeconds = AUTO_BAN_DURATION
            )
            
            if (success) {
                failedAttempts.remove(ipAddress)
                
                plugin.discordManager.sendSecurityAlert(
                    "🤖 Otomatik IP Ban",
                    "Çoklu başarısız giriş denemesi tespit edildi",
                    "**IP:** `$ipAddress`\n**Deneme Sayısı:** ${newAttempt.count}\n**İlk Deneme:** ${java.time.Instant.ofEpochMilli(newAttempt.firstAttempt)}\n**Son Deneme:** ${java.time.Instant.ofEpochMilli(newAttempt.lastAttempt)}\n**Ban Süresi:** ${AUTO_BAN_DURATION} saniye"
                )
            }
        }
    }

    fun clearFailedAttempts(ipAddress: String) {
        failedAttempts.remove(ipAddress)
    }

    fun addToWhitelist(ipAddress: String): Boolean {
        return if (whitelistedIPs.add(ipAddress)) {
            plugin.logger.info("IP whitelist'e eklendi: $ipAddress")
            saveWhitelistToConfig()
            true
        } else {
            false
        }
    }

    fun removeFromWhitelist(ipAddress: String): Boolean {
        return if (whitelistedIPs.remove(ipAddress)) {
            plugin.logger.info("IP whitelist'ten çıkarıldı: $ipAddress")
            saveWhitelistToConfig()
            true
        } else {
            false
        }
    }

    fun isWhitelisted(ipAddress: String): Boolean {
        return whitelistedIPs.contains(ipAddress)
    }

    fun listActiveBans(): List<IPBan> {
        return plugin.databaseManager.executeSync {
            try {
                plugin.databaseManager.provider.getAllIPBans()?.filter { !it.isExpired() }
            } catch (e: Exception) {
                plugin.logger.warning("IP ban listesi hatası: ${e.message}")
                null
            }
        } ?: emptyList()
    }

    fun getStats(): Map<String, Any> {
        return plugin.databaseManager.executeSync {
            try {
                val allBans = plugin.databaseManager.provider.getAllIPBans() ?: emptyList()
                val activeBans = allBans.filter { !it.isExpired() }

                mapOf(
                    "total_bans" to allBans.size,
                    "active_bans" to activeBans.size,
                    "expired_bans" to (allBans.size - activeBans.size),
                    "failed_attempts" to failedAttempts.size,
                    "whitelisted_ips" to whitelistedIPs.size
                )
            } catch (e: Exception) {
                plugin.logger.warning("IP ban istatistik hatası: ${e.message}")
                null
            }
        } ?: emptyMap()
    }
    
    private fun kickPlayersWithIP(ipAddress: String, message: String) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val playersToKick = plugin.server.onlinePlayers.filter { player ->
                player.address?.address?.hostAddress == ipAddress
            }
            
            playersToKick.forEach { player ->
                player.kickPlayer(message)
                plugin.logger.info("Oyuncu banlı IP nedeniyle atıldı: ${player.name} ($ipAddress)")
            }
        })
    }
    
    private fun loadWhitelistedIPs() {
        try {
            val whitelistConfig = plugin.configManager.getConfig().getStringList("ip_whitelist")
            whitelistedIPs.clear()
            whitelistedIPs.addAll(whitelistConfig)
            plugin.logger.info("${whitelistedIPs.size} IP whitelist'e yüklendi")
        } catch (e: Exception) {
            plugin.logger.warning("IP whitelist yükleme hatası: ${e.message}")
        }
    }
    
    private fun saveWhitelistToConfig() {
        try {
            plugin.configManager.getConfig().set("ip_whitelist", whitelistedIPs.toList())
            plugin.configManager.saveConfig()
        } catch (e: Exception) {
            plugin.logger.warning("IP whitelist kaydetme hatası: ${e.message}")
        }
    }
    
    private fun startCleanupTask() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            plugin.databaseManager.executeAsync {
                try {
                    plugin.databaseManager.provider.deleteExpiredIPBans()
                } catch (e: Exception) {
                    plugin.logger.warning("Periyodik IP ban temizleme hatası: ${e.message}")
                }
            }
            
            val currentTime = System.currentTimeMillis()
            failedAttempts.entries.removeIf { (_, attempt) ->
                (currentTime - attempt.lastAttempt) > ATTEMPT_RESET_TIME
            }
            
        }, 1200L, 1200L) //herdk
    }
    
    private fun cleanupExpiredBans() {
        plugin.databaseManager.executeAsync {
            try {
                plugin.databaseManager.provider.deleteExpiredIPBans()
                plugin.logger.info("Süresi dolmuş IP ban'lar temizlendi")
            } catch (e: Exception) {
                plugin.logger.warning("IP ban temizleme hatası: ${e.message}")
            }
        }
    }
}