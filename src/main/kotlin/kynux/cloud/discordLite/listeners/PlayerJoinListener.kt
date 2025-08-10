package kynux.cloud.discordLite.listeners

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.models.SecurityEventType
import kynux.cloud.discordLite.database.models.SecurityLog
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerJoinListener(private val plugin: DiscordLite) : Listener {
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player
        val playerIP = event.address?.hostAddress ?: "Unknown"
        
        plugin.logger.info("Oyuncu giriş denemesi: ${player.name} (${player.uniqueId}) - IP: $playerIP")
        
        if (plugin.ipBanManager.isIPBanned(playerIP)) {
            val ipBanInfo = plugin.ipBanManager.getIPBanInfo(playerIP)
            
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                "§c§lIP BAN\n§7IP adresiniz güvenlik nedeniyle banlanmıştır!\n§7Sebep: §f${ipBanInfo?.reason ?: "Bilinmiyor"}\n§7Süre: §f${if (ipBanInfo?.expiresAt == null) "Kalıcı" else "Süreli"}")
            
            plugin.ipBanManager.recordFailedAttempt(playerIP, "Banlı IP giriş denemesi")
            
            plugin.logger.warning("Banlı IP adresi giriş denemesi: $playerIP - ${player.name}")
            return
        }
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(player.uniqueId.toString())
                if (playerData != null && playerData.twoFAEnabled && !playerData.discordId.isNullOrBlank()) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (!player.hasPermission("discordlite.admin.bypass")) {
                            val verificationStarted = plugin.twoFAManager.startVerification(player, playerIP)
                            
                            if (verificationStarted) {
                                plugin.twoFAManager.freezePlayer(player)
                                
                                player.sendMessage("§e§l⚠️ 2FA Doğrulaması Gerekli")
                                player.sendMessage("§7Discord'unuza gelen mesajdaki onay butonuna basın!")
                                player.sendMessage("§7Bu işlem güvenliğiniz için gereklidir.")
                                player.sendMessage("§c60 saniye içinde onaylamazsanız sunucudan atılırsınız!")
                                
                                plugin.logger.info("${player.name} için 2FA doğrulaması başlatıldı")
                            } else {
                                player.kickPlayer("§c§l2FA Doğrulaması Hatası\n§7Discord hesabınızla iletişim kurulamadı!\n§7Lütfen daha sonra tekrar deneyin.")
                                
                                plugin.logger.warning("${player.name} için 2FA doğrulaması başlatılamadı")
                            }
                        } else {
                            plugin.logger.info("${player.name} için 2FA bypass (admin permission)")
                        }
                    })
                }
                
            } catch (e: Exception) {
                plugin.logger.severe("Oyuncu giriş kontrolü hatası: ${e.message}")
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        plugin.permissionManager.loadPlayerPermissions(player)
        
        plugin.databaseManager.executeAsync {
            try {
                val playerIP = player.address?.address?.hostAddress ?: "Unknown"
                
                val playerData = plugin.databaseManager.provider.getPlayerData(player.uniqueId.toString())
                if (playerData != null) {
                    plugin.databaseManager.provider.updatePlayerLastLogin(player.uniqueId.toString())
                }
                
                plugin.databaseManager.provider.saveSecurityLog(SecurityLog.create(
                    eventType = SecurityEventType.LOGIN,
                    description = "Oyuncu sunucuya giriş yaptı",
                    playerUUID = player.uniqueId.toString(),
                    ipAddress = playerIP,
                    details = "Başarılı giriş"
                ))
                
            } catch (e: Exception) {
                plugin.logger.warning("Oyuncu giriş log hatası: ${e.message}")
            }
        }
        
        plugin.logger.info("Oyuncu sunucuya katıldı: ${player.name}")
        
        player.sendMessage("§a§lDiscordLite §7» §fHoş geldin §b${player.name}§f!")
        player.sendMessage("§7Discord hesabınızı eşlemek için §e/discordlite link §7komutunu kullanın.")
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(player.uniqueId.toString())
                
                if (playerData == null || playerData.discordId.isNullOrBlank()) {
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        player.sendMessage("§e§l💡 Hatırlatma: §7Discord hesabınızı eşlemek için §e/discordlite link §7komutunu kullanabilirsiniz!")
                    }, 60L)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Discord eşleme kontrolü hatası: ${e.message}")
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        plugin.permissionManager.onPlayerQuit(player)
        
        plugin.linkingManager.cancelPendingLink(player.uniqueId.toString())
        
        plugin.logger.info("Oyuncu sunucudan ayrıldı: ${player.name}")
    }
}