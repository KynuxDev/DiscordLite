package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.models.PendingVerification
import kynux.cloud.discordLite.database.models.SecurityEventType
import kynux.cloud.discordLite.database.models.SecurityLog
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerLoginEvent
import java.awt.Color
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.UUID

class TwoFAManager(private val plugin: DiscordLite) {
    
    private val pendingLogins = ConcurrentHashMap<String, PlayerLoginEvent>()
    private val verificationAttempts = ConcurrentHashMap<String, Int>()
    private val frozenPlayers = ConcurrentHashMap<String, Long>() // playerUUID -> freeze timestamp
    
    fun initialize() {
        plugin.logger.info("TwoFAManager başlatılıyor...")
        
        plugin.databaseManager.executeAsync {
            try {
                plugin.databaseManager.provider.deleteExpiredVerifications()
            } catch (e: Exception) {
                plugin.logger.warning("Süresi dolmuş doğrulama temizleme hatası: ${e.message}")
            }
        }
        
        plugin.logger.info("TwoFAManager başarıyla başlatıldı!")
    }
    
    fun shutdown() {
        plugin.logger.info("TwoFAManager kapatılıyor...")
        
        pendingLogins.values.forEach { loginEvent ->
            try {
                loginEvent.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    "§cSunucu kapatılıyor...")
            } catch (e: Exception) {}
        }
        
        pendingLogins.clear()
        verificationAttempts.clear()
        
        plugin.logger.info("TwoFAManager kapatıldı!")
    }

    fun startVerification(player: Player, ipAddress: String): Boolean {
        val playerUUID = player.uniqueId.toString()
        
        plugin.logger.info("2FA doğrulaması başlatıldı: ${player.name} - IP: $ipAddress")
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(playerUUID)
                
                if (playerData == null || playerData.discordId.isNullOrBlank() || !playerData.twoFAEnabled) {
                    plugin.logger.warning("2FA başlatılamadı - oyuncu verisi bulunamadı veya 2FA aktif değil: ${player.name}")
                    return@executeAsync
                }
                
                val discordId = playerData.discordId
                
                val existingVerification = plugin.databaseManager.provider.getPendingVerification(playerUUID)
                if (existingVerification != null) {
                    plugin.databaseManager.provider.deletePendingVerification(existingVerification.id)
                }
                
                val verificationCode = generateVerificationCode()
                val verification = PendingVerification.create(
                    playerUUID = playerUUID,
                    verificationCode = verificationCode,
                    ipAddress = ipAddress,
                    timeoutSeconds = plugin.configManager.getVerificationTimeout()
                )
                
                plugin.databaseManager.provider.savePendingVerification(verification)
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sendDiscordVerification(discordId, player.name, ipAddress, verificationCode, verification.id)
                })
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§6§l2FA Doğrulaması")
                    player.sendMessage("§7Discord hesabınıza gönderilen mesajdaki onay butonuna tıklayın.")
                    player.sendMessage("§7Süre: §f${plugin.configManager.getVerificationTimeout()} saniye")
                    player.sendMessage("§cZaman aşımında sunucudan atılacaksınız!")
                })
                
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    handleVerificationTimeout(playerUUID)
                }, (plugin.configManager.getVerificationTimeout() * 20L)) // saniyeyi ticke çevir
                
                plugin.databaseManager.provider.saveSecurityLog(SecurityLog.create(
                    eventType = SecurityEventType.LOGIN,
                    description = "2FA doğrulaması başlatıldı",
                    playerUUID = playerUUID,
                    ipAddress = ipAddress,
                    details = "Discord ID: $discordId"
                ))
                
            } catch (e: Exception) {
                plugin.logger.severe("2FA başlatma hatası: ${e.message}")
                e.printStackTrace()
            }
        }
        return true
    }

    private fun sendDiscordVerification(discordId: String, playerName: String, ipAddress: String, code: String, verificationId: String) {
        try {
            val jda = plugin.discordManager.getJDA()
            val user = jda?.getUserById(discordId)
            
            if (user == null) {
                plugin.logger.warning("Discord kullanıcısı bulunamadı: $discordId")
                return
            }
            
            val embed = EmbedBuilder().apply {
                setTitle("🔐 2FA Doğrulaması Gerekli")
                setDescription("**$playerName** adlı hesapınız için giriş doğrulaması gerekiyor.")
                addField("🎮 Oyuncu", playerName, true)
                addField("🌐 IP Adresi", ipAddress, true)
                addField("🕒 İstek Zamanı", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), true)
                addField("⏰ Süre", "${plugin.configManager.getVerificationTimeout()} saniye", true)
                addField("🔢 Doğrulama Kodu", "`$code`", true)
                addBlankField(false)
                addField("❓ Bu sizseniz", "**Onayla** butonuna tıklayın", true)
                addField("⚠️ Bu siz değilseniz", "**Reddet** butonuna tıklayın", true)
                setColor(Color.ORANGE)
                setFooter("DiscordLite 2FA Security System", null)
                setTimestamp(java.time.Instant.now())
            }.build()
            
            val approveButton = Button.success("2fa_approve_$verificationId", "✅ Onayla")
            val denyButton = Button.danger("2fa_deny_$verificationId", "❌ Reddet")
            
            user.openPrivateChannel().queue(
                { channel ->
                    channel.sendMessageEmbeds(embed)
                        .setActionRow(approveButton, denyButton)
                        .queue(
                            { message ->
                                plugin.databaseManager.executeAsync {
                                    try {
                                        val verification = plugin.databaseManager.provider.getPendingVerificationByCode(code)
                                        if (verification != null) {
                                            val updatedVerification = verification.copy(discordMessageId = message.id)
                                            plugin.databaseManager.provider.savePendingVerification(updatedVerification)
                                        }
                                    } catch (e: Exception) {
                                        plugin.logger.warning("Mesaj ID kaydetme hatası: ${e.message}")
                                    }
                                }
                                plugin.logger.info("2FA doğrulama mesajı gönderildi: $playerName")
                            },
                            { error ->
                                plugin.logger.severe("2FA mesajı gönderilemedi: ${error.message}")
                            }
                        )
                },
                { error ->
                    plugin.logger.severe("Discord DM açılamadı: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord doğrulama mesajı hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    fun approveVerification(verificationId: String, discordUserId: String): Boolean {
        return plugin.databaseManager.executeSync {
            try {
                val verification = plugin.databaseManager.provider.getPendingVerification(verificationId) ?: run {
                    plugin.logger.warning("Doğrulama bulunamadı: $verificationId")
                    return@executeSync false
                }

                if (verification.isExpired()) {
                    plugin.databaseManager.provider.deletePendingVerification(verificationId)
                    plugin.logger.warning("Süresi dolmuş doğrulama onaylanmaya çalışıldı: $verificationId")
                    return@executeSync false
                }

                val player = plugin.server.getPlayer(UUID.fromString(verification.playerUUID))
                if (player == null || !player.isOnline) {
                    plugin.databaseManager.provider.deletePendingVerification(verificationId)
                    plugin.logger.warning("Oyuncu çevrimdışı, doğrulama iptal edildi: ${verification.playerUUID}")
                    return@executeSync false
                }

                plugin.databaseManager.provider.deletePendingVerification(verificationId)
                verificationAttempts.remove(verification.playerUUID)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    unfreezePlayer(player)
                    player.sendMessage("§a§l✅ 2FA Doğrulaması Başarılı!")
                    player.sendMessage("§aDiscord hesabınız üzerinden giriş onaylandı.")
                    player.sendMessage("§aHoş geldiniz, ${player.name}!")
                })

                plugin.databaseManager.provider.saveSecurityLog(
                    SecurityLog.createSuccessfulLogin(
                        playerUUID = verification.playerUUID,
                        playerName = player.name,
                        ipAddress = verification.ipAddress,
                        twoFAUsed = true
                    )
                )

                plugin.logger.info("2FA doğrulaması başarılı: ${player.name}")
                true

            } catch (e: Exception) {
                plugin.logger.severe("Doğrulama onaylama hatası: ${e.message}")
                false
            }
        } ?: false
    }

    fun freezePlayer(player: Player) {
        val playerUUID = player.uniqueId.toString()
        frozenPlayers[playerUUID] = System.currentTimeMillis()
        
        player.sendMessage("§c🔒 2FA doğrulaması sırasında hareket kısıtlandı!")
        player.sendMessage("§eDiscord'dan onay bekleniyor...")
        
        plugin.logger.info("Oyuncu 2FA için donduruldu: ${player.name}")
    }

    fun isPlayerFrozen(player: Player): Boolean {
        val playerUUID = player.uniqueId.toString()
        return frozenPlayers.containsKey(playerUUID)
    }

    fun unfreezePlayer(player: Player) {
        val playerUUID = player.uniqueId.toString()
        frozenPlayers.remove(playerUUID)
        
        player.sendMessage("§a✅ Hareket kısıtlaması kaldırıldı!")
        player.sendMessage("§a2FA doğrulaması tamamlandı!")
        
        plugin.logger.info("Oyuncu 2FA sonrası çözüldü: ${player.name}")
    }

    fun handlePlayerMovement(player: Player) {
        player.sendMessage("§c🔒 2FA doğrulaması bekleniyor... Discord'u kontrol edin!")
        
        val freezeTime = frozenPlayers[player.uniqueId.toString()] ?: return
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - freezeTime > 5000 && (currentTime - freezeTime) % 5000 < 1000) {
            player.sendMessage("§e⏰ Discord hesabınıza gelen 2FA onay mesajını kontrol edin!")
            player.sendMessage("§7Süre: ${plugin.configManager.getVerificationTimeout()} saniye")
        }
    }

    fun denyVerification(verificationId: String, discordUserId: String): Boolean {
        return plugin.databaseManager.executeSync {
            try {
                val verification = plugin.databaseManager.provider.getPendingVerification(verificationId) ?: run {
                    plugin.logger.warning("Doğrulama bulunamadı: $verificationId")
                    return@executeSync false
                }

                val player = plugin.server.getPlayer(UUID.fromString(verification.playerUUID))
                val playerName = player?.name ?: "Unknown"

                plugin.databaseManager.provider.deletePendingVerification(verificationId)
                verificationAttempts.remove(verification.playerUUID)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    player?.kickPlayer("§c§l2FA Doğrulaması Reddedildi!\n§7Giriş Discord hesabınız üzerinden reddedildi.\n§7Güvenlik nedeniyle sunucudan atıldınız.")
                })

                if (plugin.configManager.getIPBanDuration() > 0) {
                    plugin.ipBanManager.banIP(
                        ipAddress = verification.ipAddress,
                        reason = "2FA doğrulaması reddedildi",
                        bannedBy = "DiscordLite-2FA",
                        durationSeconds = plugin.configManager.getIPBanDuration()
                    )
                }

                plugin.databaseManager.provider.saveSecurityLog(
                    SecurityLog.createFailedLogin(
                        playerName = playerName,
                        ipAddress = verification.ipAddress,
                        reason = "2FA Reddedildi"
                    )
                )

                plugin.discordManager.sendSecurityAlert(
                    "🚨 2FA Doğrulaması Reddedildi",
                    "Şüpheli giriş denemesi tespit edildi!",
                    "Oyuncu: `$playerName`\nIP: `${verification.ipAddress}`\nDiscord: <@$discordUserId>\nZaman: ${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
                )

                plugin.logger.warning("2FA doğrulaması reddedildi: $playerName (${verification.ipAddress})")
                true

            } catch (e: Exception) {
                plugin.logger.severe("Doğrulama reddetme hatası: ${e.message}")
                false
            }
        } ?: false
    }

    private fun handleVerificationTimeout(playerUUID: String) {
        plugin.databaseManager.executeAsync {
            try {
                val verification = plugin.databaseManager.provider.getPendingVerification(playerUUID)
                if (verification == null) {
                    return@executeAsync
                }
                
                val player = plugin.server.getPlayer(UUID.fromString(playerUUID))
                
                if (player != null && player.isOnline) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.kickPlayer("§c§l⏰ 2FA Zaman Aşımı!\n§7Doğrulama süresi doldu.\n§7Lütfen tekrar giriş yapın.")
                    })
                }
                
                plugin.databaseManager.provider.deletePendingVerification(verification.id)
                verificationAttempts.remove(playerUUID)
                
                plugin.databaseManager.provider.saveSecurityLog(SecurityLog.create(
                    eventType = SecurityEventType.FAILED_LOGIN,
                    description = "2FA doğrulaması zaman aşımı",
                    playerUUID = playerUUID,
                    ipAddress = verification.ipAddress,
                    details = "Timeout: ${plugin.configManager.getVerificationTimeout()}s"
                ))
                
                plugin.logger.info("2FA zaman aşımı: ${player?.name ?: playerUUID}")
                
            } catch (e: Exception) {
                plugin.logger.severe("2FA timeout işleme hatası: ${e.message}")
            }
        }
    }

    fun hasPendingVerification(playerUUID: String): Boolean {
        return plugin.databaseManager.executeSync {
            try {
                val verification = plugin.databaseManager.provider.getPendingVerification(playerUUID)
                verification != null && !verification.isExpired()
            } catch (e: Exception) {
                plugin.logger.warning("Pending verification kontrolü hatası: ${e.message}")
                false
            }
        } ?: false
    }

    fun requires2FA(playerUUID: String): Boolean {
        return plugin.databaseManager.executeSync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(playerUUID)
                playerData?.twoFAEnabled == true && !playerData.discordId.isNullOrBlank()
            } catch (e: Exception) {
                plugin.logger.warning("2FA gereksinim kontrolü hatası: ${e.message}")
                false
            }
        } ?: false
    }

    fun toggle2FA(player: Player, enabled: Boolean) {
        val playerUUID = player.uniqueId.toString()
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(playerUUID)
                
                if (playerData == null || playerData.discordId.isNullOrBlank()) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.sendMessage("§c2FA'yı ${if (enabled) "aktif" else "pasif"} etmek için önce Discord hesabınızı eşlemeniz gerekiyor!")
                        player.sendMessage("§e/discordlite link §7komutu ile eşleme yapabilirsiniz.")
                    })
                    return@executeAsync
                }
                
                val updatedPlayerData = playerData.copy(twoFAEnabled = enabled)
                plugin.databaseManager.provider.savePlayerData(updatedPlayerData)
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (enabled) {
                        player.sendMessage("§a§l✅ 2FA Aktif Edildi!")
                        player.sendMessage("§7Artık sunucuya giriş yaparken Discord üzerinden onay vermeniz gerekecek.")
                    } else {
                        player.sendMessage("§c§l❌ 2FA Pasif Edildi!")
                        player.sendMessage("§7Artık sunucuya giriş yaparken Discord onayı gerekmeyecek.")
                    }
                })
                
                plugin.databaseManager.provider.saveSecurityLog(SecurityLog.create(
                    eventType = SecurityEventType.SYSTEM_ERROR,
                    description = "2FA ${if (enabled) "aktif" else "pasif"} edildi",
                    playerUUID = playerUUID,
                    details = "Oyuncu tarafından değiştirildi"
                ))
                
                plugin.logger.info("${player.name} için 2FA ${if (enabled) "aktif" else "pasif"} edildi")
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c2FA ayarları değiştirilirken hata oluştu!")
                })
                plugin.logger.severe("2FA toggle hatası: ${e.message}")
            }
        }
    }

    private fun generateVerificationCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars[ThreadLocalRandom.current().nextInt(chars.length)] }
            .joinToString("")
    }

    fun get2FAStats(): Map<String, Any> {
        return plugin.databaseManager.executeSync {
            try {
                val stats = plugin.databaseManager.provider.getStats()
                if (stats != null) {
                    mapOf(
                        "active_2fa_users" to stats.activeTwoFAUsers,
                        "pending_verifications" to stats.pendingVerifications,
                        "current_attempts" to verificationAttempts.size
                    )
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                plugin.logger.warning("2FA istatistik hatası: ${e.message}")
                null
            }
        } ?: emptyMap()
    }
}