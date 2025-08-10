package kynux.cloud.discordLite.commands

import kynux.cloud.discordLite.DiscordLite
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class DiscordLiteCommand(private val plugin: DiscordLite) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "verify" -> handleVerify(sender, args)
            "unlink" -> handleUnlink(sender, args)
            "2fa" -> handle2FA(sender, args)
            "status" -> handleStatus(sender, args)
            "reload" -> handleReload(sender, args)
            "reset" -> handleReset(sender, args)
            "ban" -> handleBan(sender, args)
            "unban" -> handleUnban(sender, args)
            "info" -> handleInfo(sender, args)
            "logs" -> handleLogs(sender, args)
            else -> sendHelp(sender)
        }
        
        return true
    }
    
    private fun handleVerify(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cBu komut sadece oyuncular tarafından kullanılabilir!")
            return
        }
        
        if (!sender.hasPermission("discordlite.verify")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        plugin.linkingManager.startVerification(sender)
    }
    
    private fun handleUnlink(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("discordlite.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§cKullanım: /discordlite unlink <oyuncu>")
            return
        }
        
        val targetPlayerName = args[1]
        val targetPlayer = plugin.server.getPlayerExact(targetPlayerName)
        
        if (targetPlayer == null) {
            sender.sendMessage("§cOyuncu bulunamadı veya çevrimdışı: $targetPlayerName")
            return
        }
        
        val targetUUID = targetPlayer.uniqueId.toString()
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(targetUUID)
                
                if (playerData == null || !playerData.isLinked()) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.sendMessage("§c$targetPlayerName adlı oyuncunun Discord hesabı zaten eşlenmemiş!")
                    })
                    return@executeAsync
                }
                
                val discordId = playerData.discordId
                
                plugin.databaseManager.provider.unlinkPlayer(targetUUID)
                
                val pendingVerification = plugin.databaseManager.provider.getPendingVerification(targetUUID)
                pendingVerification?.let {
                    plugin.databaseManager.provider.deletePendingVerification(it.id)
                }
                
                plugin.linkingManager.cancelPendingLink(targetUUID)
                
                plugin.databaseManager.provider.saveSecurityLog(kynux.cloud.discordLite.database.models.SecurityLog.create(
                    eventType = kynux.cloud.discordLite.database.models.SecurityEventType.ACCOUNT_UNLINKED,
                    description = "Admin tarafından Discord eşlemesi kaldırıldı",
                    playerUUID = targetUUID,
                    details = "Admin: ${sender.name}, Discord ID: $discordId"
                ))
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§a$targetPlayerName adlı oyuncunun Discord eşlemesi başarıyla kaldırıldı!")
                    targetPlayer.sendMessage("§c§lDikkat! §eAdmin tarafından Discord hesap eşlemeniz kaldırıldı.")
                    targetPlayer.sendMessage("§eYeniden eşlemek için §f/discordlite link §ekomutunu kullanabilirsiniz.")
                    
                    plugin.logger.info("${sender.name} tarafından ${targetPlayerName} oyuncusunun Discord eşlemesi kaldırıldı")
                })
                
                plugin.discordManager.sendLogMessage(
                    "🔗 Discord Eşleme Kaldırıldı",
                    "Oyuncu: `$targetPlayerName`\nDiscord ID: `$discordId`\nAdmin: ${sender.name}",
                    java.awt.Color.ORANGE
                )
                
                try {
                    val guild = plugin.discordManager.getGuild()
                    val member = guild?.getMemberById(discordId!!)
                    if (member != null) {
                        val minecraftRoles = guild.roles.filter { role ->
                            plugin.configManager.getPermissionToRoleMapping().values.contains(role.id)
                        }
                        
                        if (minecraftRoles.isNotEmpty()) {
                            guild.modifyMemberRoles(member, emptyList(), minecraftRoles).queue(
                                {
                                    plugin.logger.info("$targetPlayerName için Discord rolleri kaldırıldı")
                                },
                                { error ->
                                    plugin.logger.warning("Discord rol kaldırma hatası: ${error.message}")
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Discord rol güncelleme hatası: ${e.message}")
                }
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§cEşleme kaldırma işlemi başarısız: ${e.message}")
                })
                plugin.logger.severe("Unlink hatası: ${e.message}")
            }
        }
    }
    
    private fun handle2FA(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cBu komut sadece oyuncular tarafından kullanılabilir!")
            return
        }
        
        if (!sender.hasPermission("discordlite.2fa")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§cKullanım: /discordlite 2fa <on/off/status>")
            return
        }
        
        when (args[1].lowercase()) {
            "on" -> {
                plugin.twoFAManager.toggle2FA(sender, true)
            }
            "off" -> {
                plugin.twoFAManager.toggle2FA(sender, false)
            }
            "status" -> {
                show2FAStatus(sender)
            }
            else -> {
                sender.sendMessage("§cKullanım: /discordlite 2fa <on/off/status>")
            }
        }
    }
    
    private fun show2FAStatus(player: Player) {
        val playerUUID = player.uniqueId.toString()
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(playerUUID)
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§6§l=== 2FA Durumu ===")
                    
                    if (playerData == null || !playerData.isLinked()) {
                        player.sendMessage("§7Discord Eşleme: §cEşlenmemiş")
                        player.sendMessage("§72FA Durumu: §cKullanılamaz")
                        player.sendMessage("§e2FA'yı kullanmak için önce Discord hesabınızı eşlemeniz gerekiyor.")
                        player.sendMessage("§e/discordlite link §7komutu ile eşleme yapabilirsiniz.")
                    } else {
                        player.sendMessage("§7Discord Eşleme: §aEşlenmiş")
                        player.sendMessage("§7Discord ID: §f${playerData.discordId}")
                        player.sendMessage("§72FA Durumu: ${if (playerData.twoFAEnabled) "§aAktif" else "§cPasif"}")
                        
                        if (playerData.twoFAEnabled) {
                            player.sendMessage("§a✅ 2FA aktif - Giriş yaparken Discord onayı gerekecek")
                            player.sendMessage("§7Devre dışı bırakmak için: §f/discordlite 2fa off")
                        } else {
                            player.sendMessage("§c❌ 2FA pasif - Ek güvenlik koruması yok")
                            player.sendMessage("§7Aktif etmek için: §f/discordlite 2fa on")
                        }
                        
                        if (plugin.twoFAManager.hasPendingVerification(playerUUID)) {
                            player.sendMessage("§eŞu anda bekleyen bir doğrulama var!")
                        }
                    }
                })
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c2FA durumu kontrol edilirken hata oluştu!")
                })
                plugin.logger.severe("2FA status kontrol hatası: ${e.message}")
            }
        }
    }
    
    private fun handleStatus(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cBu komut sadece oyuncular tarafından kullanılabilir!")
            return
        }
        
        if (!sender.hasPermission("discordlite.use")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        val playerUUID = sender.uniqueId.toString()
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(playerUUID)
                val pendingVerification = plugin.databaseManager.provider.getPendingVerification(playerUUID)
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§6§l=== Hesap Durumu ===")
                    sender.sendMessage("§7Oyuncu: §f${sender.name}")
                    sender.sendMessage("§7UUID: §f$playerUUID")
                    
                    if (playerData == null || !playerData.isLinked()) {
                        sender.sendMessage("§7Discord Eşleme: §cEşlenmemiş")
                        sender.sendMessage("§72FA Durumu: §cKullanılamaz")
                        sender.sendMessage("")
                        sender.sendMessage("§eDiscord hesabınızı eşlemek için §f/discordlite link §ekomutunu kullanın.")
                    } else {
                        sender.sendMessage("§7Discord Eşleme: §aEşlenmiş")
                        sender.sendMessage("§7Discord ID: §f${playerData.discordId}")
                        sender.sendMessage("§72FA Durumu: ${if (playerData.twoFAEnabled) "§aAktif" else "§cPasif"}")
                        
                        if (playerData.linkedAt != null) {
                            sender.sendMessage("§7Eşleme Tarihi: §f${playerData.linkedAt!!.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                        }
                        
                        if (playerData.lastLogin != null) {
                            sender.sendMessage("§7Son Giriş: §f${playerData.lastLogin!!.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                        }
                    }
                    
                    if (pendingVerification != null && !pendingVerification.isExpired()) {
                        sender.sendMessage("")
                        sender.sendMessage("§e⚠ Bekleyen Doğrulama Var!")
                        sender.sendMessage("§7Doğrulama Kodu: §f${pendingVerification.verificationCode}")
                        sender.sendMessage("§7Kalan Süre: §f${pendingVerification.getRemainingSeconds()} saniye")
                        sender.sendMessage("§7Discord hesabınızdaki mesajı kontrol edin.")
                    }
                    
                    val permissions = plugin.permissionManager.getPlayerPermissions(sender)
                    if (permissions.isNotEmpty()) {
                        sender.sendMessage("")
                        sender.sendMessage("§7Discord Rolleri: §f${permissions.size} permission aktif")
                    }
                })
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§cDurum bilgileri alınırken hata oluştu!")
                })
                plugin.logger.severe("Status kontrol hatası: ${e.message}")
            }
        }
    }
    
    private fun handleReload(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("discordlite.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        try {
            plugin.configManager.reloadConfig()
            sender.sendMessage("§aKonfigürasyon başarıyla yeniden yüklendi!")
        } catch (e: Exception) {
            sender.sendMessage("§cKonfigürasyon yüklenirken hata oluştu: ${e.message}")
        }
    }
    
    private fun handleReset(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("discordlite.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§cKullanım: /discordlite reset <oyuncu>")
            return
        }
        
        val targetPlayerName = args[1]
        val targetPlayer = plugin.server.getPlayerExact(targetPlayerName)
        
        if (targetPlayer == null) {
            sender.sendMessage("§cOyuncu bulunamadı veya çevrimdışı: $targetPlayerName")
            return
        }
        
        val targetUUID = targetPlayer.uniqueId.toString()
        
        sender.sendMessage("§e${targetPlayerName} adlı oyuncunun TÜM verilerini silmek istediğinizden emin misiniz?")
        sender.sendMessage("§eOnaylamak için §f/discordlite reset $targetPlayerName confirm §eyazın.")
        
        if (args.size >= 3 && args[2].equals("confirm", ignoreCase = true)) {
            plugin.databaseManager.executeAsync {
                try {
                    plugin.databaseManager.provider.deletePlayerData(targetUUID)
                    
                    val pendingVerification = plugin.databaseManager.provider.getPendingVerification(targetUUID)
                    pendingVerification?.let {
                        plugin.databaseManager.provider.deletePendingVerification(it.id)
                    }
                    
                    plugin.linkingManager.cancelPendingLink(targetUUID)
                    
                    plugin.databaseManager.provider.saveSecurityLog(kynux.cloud.discordLite.database.models.SecurityLog.create(
                        eventType = kynux.cloud.discordLite.database.models.SecurityEventType.ADMIN_ACTION,
                        description = "Admin tarafından oyuncu verileri sıfırlandı",
                        playerUUID = targetUUID,
                        details = "Admin: ${sender.name}"
                    ))
                    
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.sendMessage("§a${targetPlayerName} adlı oyuncunun TÜM verileri başarıyla sıfırlandı!")
                        targetPlayer.sendMessage("§c§lDikkat! §eAdmin tarafından Discord hesap verileriniz sıfırlandı.")
                        
                        plugin.logger.warning("${sender.name} tarafından ${targetPlayerName} oyuncusunun verileri sıfırlandı")
                    })
                    
                } catch (e: Exception) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.sendMessage("§cVeri sıfırlama işlemi başarısız: ${e.message}")
                    })
                    plugin.logger.severe("Veri sıfırlama hatası: ${e.message}")
                }
            }
        }
    }
    
    private fun handleBan(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("discordlite.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§cKullanım: /discordlite ban <ip> [süre] [sebep]")
            sender.sendMessage("§7Örnek: /discordlite ban 192.168.1.1 3600 \"Şüpheli aktivite\"")
            sender.sendMessage("§7Süre (saniye): -1 = kalıcı, 0 = varsayılan")
            return
        }
        
        val ipAddress = args[1]
        
        if (!ipAddress.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
            sender.sendMessage("§cGeçersiz IP adresi formatı!")
            return
        }
        
        val duration = if (args.size >= 3) {
            try {
                args[2].toInt()
            } catch (e: NumberFormatException) {
                sender.sendMessage("§cGeçersiz süre formatı! Saniye cinsinden sayı girin.")
                return
            }
        } else {
            plugin.configManager.getIPBanDuration()
        }
        
        val reason = if (args.size >= 4) {
            args.drop(3).joinToString(" ")
        } else {
            "Admin tarafından banlandı"
        }
        
        plugin.databaseManager.executeAsync {
            try {
                val ipBan = kynux.cloud.discordLite.database.models.IPBan.create(
                    ipAddress = ipAddress,
                    reason = reason,
                    bannedBy = sender.name,
                    durationSeconds = duration
                )
                
                plugin.databaseManager.provider.saveIPBan(ipBan)
                
                plugin.databaseManager.provider.saveSecurityLog(kynux.cloud.discordLite.database.models.SecurityLog.create(
                    eventType = kynux.cloud.discordLite.database.models.SecurityEventType.IP_BAN,
                    description = "IP adresi banlandı",
                    ipAddress = ipAddress,
                    details = "Sebep: $reason, Süre: ${if (duration == -1) "Kalıcı" else "${duration}s"}, Admin: ${sender.name}"
                ))
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§a$ipAddress IP adresi başarıyla banlandı!")
                    sender.sendMessage("§7Sebep: §f$reason")
                    sender.sendMessage("§7Süre: §f${if (duration == -1) "Kalıcı" else "${duration} saniye"}")
                    
                    plugin.logger.warning("${sender.name} tarafından $ipAddress IP adresi banlandı: $reason")
                })
                
                plugin.discordManager.sendSecurityAlert(
                    "🚫 IP Ban İşlemi",
                    "Yeni IP ban uygulandı",
                    "IP: `$ipAddress`\nSebep: $reason\nSüre: ${if (duration == -1) "Kalıcı" else "${duration}s"}\nAdmin: ${sender.name}"
                )
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§cIP ban işlemi başarısız: ${e.message}")
                })
                plugin.logger.severe("IP ban hatası: ${e.message}")
            }
        }
    }
    
    private fun handleUnban(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("discordlite.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§cKullanım: /discordlite unban <ip>")
            return
        }
        
        val ipAddress = args[1]
        
        if (!ipAddress.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
            sender.sendMessage("§cGeçersiz IP adresi formatı!")
            return
        }
        
        plugin.databaseManager.executeAsync {
            try {
                val existingBan = plugin.databaseManager.provider.getIPBan(ipAddress)
                
                if (existingBan == null) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.sendMessage("§c$ipAddress IP adresi zaten banlı değil!")
                    })
                    return@executeAsync
                }
                
                plugin.databaseManager.provider.deleteIPBan(ipAddress)
                
                plugin.databaseManager.provider.saveSecurityLog(kynux.cloud.discordLite.database.models.SecurityLog.create(
                    eventType = kynux.cloud.discordLite.database.models.SecurityEventType.IP_UNBAN,
                    description = "IP ban kaldırıldı",
                    ipAddress = ipAddress,
                    details = "Admin: ${sender.name}, Önceki sebep: ${existingBan.reason}"
                ))
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§a$ipAddress IP adresi ban listesinden kaldırıldı!")
                    sender.sendMessage("§7Önceki ban sebebi: §f${existingBan.reason}")
                    
                    plugin.logger.info("${sender.name} tarafından $ipAddress IP ban kaldırıldı")
                })
                
                plugin.discordManager.sendLogMessage(
                    "✅ IP Ban Kaldırıldı",
                    "IP ban kaldırıldı: `$ipAddress`\nAdmin: ${sender.name}",
                    java.awt.Color.GREEN
                )
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§cIP ban kaldırma işlemi başarısız: ${e.message}")
                })
                plugin.logger.severe("IP ban kaldırma hatası: ${e.message}")
            }
        }
    }
    
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("discordlite.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§cKullanım: /discordlite info <oyuncu>")
            return
        }
        
        val targetPlayerName = args[1]
        val targetPlayer = plugin.server.getPlayerExact(targetPlayerName)
        
        if (targetPlayer == null) {
            sender.sendMessage("§cOyuncu bulunamadı veya çevrimdışı: $targetPlayerName")
            return
        }
        
        val targetUUID = targetPlayer.uniqueId.toString()
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(targetUUID)
                val pendingVerification = plugin.databaseManager.provider.getPendingVerification(targetUUID)
                val recentLogs = plugin.databaseManager.provider.getSecurityLogsByPlayer(targetUUID, 5)
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§6§l=== ${targetPlayerName} Oyuncu Bilgileri ===")
                    sender.sendMessage("§7UUID: §f$targetUUID")
                    sender.sendMessage("§7IP Adresi: §f${targetPlayer.address?.address?.hostAddress ?: "Bilinmiyor"}")
                    sender.sendMessage("§7Online: §a${if (targetPlayer.isOnline) "Evet" else "Hayır"}")
                    
                    if (playerData != null) {
                        sender.sendMessage("§7Discord Eşleme: ${if (playerData.isLinked()) "§aEşlenmiş" else "§cEşlenmemiş"}")
                        
                        if (playerData.isLinked()) {
                            sender.sendMessage("§7Discord ID: §f${playerData.discordId}")
                            sender.sendMessage("§72FA Durumu: ${if (playerData.twoFAEnabled) "§aAktif" else "§cPasif"}")
                            
                            if (playerData.linkedAt != null) {
                                sender.sendMessage("§7Eşleme Tarihi: §f${playerData.linkedAt!!.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                            }
                            
                            if (playerData.lastLogin != null) {
                                sender.sendMessage("§7Son Giriş: §f${playerData.lastLogin!!.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                            }
                        }
                    } else {
                        sender.sendMessage("§7Discord Eşleme: §cEşlenmemiş")
                        sender.sendMessage("§72FA Durumu: §cPasif")
                    }
                    
                    if (pendingVerification != null) {
                        sender.sendMessage("§7Bekleyen Doğrulama: §eEvet")
                        sender.sendMessage("§7Doğrulama Kodu: §f${pendingVerification.verificationCode}")
                        sender.sendMessage("§7Kalan Süre: §f${pendingVerification.getRemainingSeconds()}s")
                    } else {
                        sender.sendMessage("§7Bekleyen Doğrulama: §aYok")
                    }
                    
                    val pendingLink = plugin.linkingManager.getPendingLink(targetUUID)
                    if (pendingLink != null) {
                        sender.sendMessage("§7Bekleyen Eşleme: §e${pendingLink.linkCode}")
                        sender.sendMessage("§7Eşleme Süresi: §f${pendingLink.getRemainingSeconds()}s")
                    }
                    
                    if (recentLogs.isNotEmpty()) {
                        sender.sendMessage("§6Son Güvenlik Logları:")
                        recentLogs.forEach { log ->
                            sender.sendMessage("§8- §7${log.eventType.emoji} ${log.description} §8(${log.getFormattedTimestamp()})")
                        }
                    }
                })
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§cOyuncu bilgileri alınırken hata oluştu: ${e.message}")
                })
                plugin.logger.severe("Oyuncu bilgi hatası: ${e.message}")
            }
        }
    }
    
    private fun handleLogs(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("discordlite.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("no_permission"))
            return
        }
        
        var page = 1
        var targetPlayer: String? = null
        var eventType: String? = null
        var limit = 10
        
        if (args.size > 1) {
            try {
                page = args[1].toInt()
                if (page < 1) page = 1
                
                if (args.size > 2) {
                    targetPlayer = args[2]
                }
                
                if (args.size > 3) {
                    eventType = args[3].uppercase()
                }
                
            } catch (e: NumberFormatException) {
                targetPlayer = args[1]
                
                if (args.size > 2) {
                    eventType = args[2].uppercase()
                }
            }
        }
        
        val offset = (page - 1) * limit
        
        plugin.databaseManager.executeAsync {
            try {
                val targetUUID = if (targetPlayer != null) {
                    val player = plugin.server.getPlayerExact(targetPlayer)
                    player?.uniqueId?.toString() ?: run {
                        java.util.UUID.nameUUIDFromBytes("OfflinePlayer:$targetPlayer".toByteArray()).toString()
                    }
                } else null
                
                val logs = if (targetUUID != null) {
                    plugin.databaseManager.provider.getSecurityLogsByPlayer(targetUUID, limit, offset)
                } else {
                    plugin.databaseManager.provider.getAllSecurityLogs(limit, offset)
                }
                
                val totalLogs = if (targetUUID != null) {
                    plugin.databaseManager.provider.getSecurityLogCountByPlayer(targetUUID)
                } else {
                    plugin.databaseManager.provider.getTotalSecurityLogCount()
                }
                
                val totalPages = (totalLogs + limit - 1) / limit
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§6§l=== Güvenlik Logları ===")
                    
                    if (targetPlayer != null) {
                        sender.sendMessage("§7Oyuncu: §f$targetPlayer")
                    }
                    
                    if (eventType != null) {
                        sender.sendMessage("§7Event Tipi: §f$eventType")
                    }
                    
                    sender.sendMessage("§7Sayfa: §f$page§7/§f$totalPages §7(Toplam: §f$totalLogs§7)")
                    sender.sendMessage("§7" + "=".repeat(40))
                    
                    if (logs.isEmpty()) {
                        sender.sendMessage("§cBu kriterlere uygun log bulunamadı.")
                    } else {
                        logs.forEach { log ->
                            sender.sendMessage("§8[${log.getFormattedTimestamp()}] ${log.eventType.emoji} §f${log.description}")
                            
                            if (log.additionalData.isNotEmpty()) {
                                sender.sendMessage("  §8↳ §7${log.additionalData}")
                            }
                        }
                    }
                    
                    sender.sendMessage("§7" + "=".repeat(40))
                    
                    if (totalPages > 1) {
                        val prevPage = if (page > 1) page - 1 else 1
                        val nextPage = if (page < totalPages) page + 1 else totalPages
                        
                        sender.sendMessage("§7Sayfa geçişi: §e/discordlite logs $prevPage ${targetPlayer ?: ""} ${eventType ?: ""}".trim() + " §7(önceki)")
                        sender.sendMessage("§7Sayfa geçişi: §e/discordlite logs $nextPage ${targetPlayer ?: ""} ${eventType ?: ""}".trim() + " §7(sonraki)")
                    }
                    
                    sender.sendMessage("§8Kullanım: /discordlite logs [sayfa] [oyuncu] [event_type]")
                    sender.sendMessage("§8Event tipleri: LOGIN, LINK, UNLINK, BAN, UNBAN, FAILED_LOGIN")
                })
                
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("§cLoglar alınırken hata oluştu: ${e.message}")
                })
                plugin.logger.severe("Log alma hatası: ${e.message}")
            }
        }
    }
    
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§6§l=== DiscordLite Komutları ===")
        
        if (sender.hasPermission("discordlite.verify")) {
            sender.sendMessage("§e/discordlite verify §7- Discord hesabınızı eşleyin")
        }
        
        if (sender.hasPermission("discordlite.2fa")) {
            sender.sendMessage("§e/discordlite 2fa <on/off> §7- 2FA ayarlarını değiştirin")
        }
        
        if (sender.hasPermission("discordlite.use")) {
            sender.sendMessage("§e/discordlite status §7- Hesap durumunuzu görün")
        }
        
        if (sender.hasPermission("discordlite.admin")) {
            sender.sendMessage("§c§l=== Admin Komutları ===")
            sender.sendMessage("§c/discordlite unlink <oyuncu> §7- Oyuncu eşlemesini kaldır")
            sender.sendMessage("§c/discordlite reload §7- Konfigürasyonu yeniden yükle")
            sender.sendMessage("§c/discordlite reset <oyuncu> §7- Oyuncu verilerini sıfırla")
            sender.sendMessage("§c/discordlite ban <ip> §7- IP banla")
            sender.sendMessage("§c/discordlite unban <ip> §7- IP ban kaldır")
            sender.sendMessage("§c/discordlite info <oyuncu> §7- Oyuncu bilgileri")
            sender.sendMessage("§c/discordlite logs [sayfa] §7- Güvenlik logları")
        }
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subcommands = mutableListOf<String>()
            
            if (sender.hasPermission("discordlite.verify")) {
                subcommands.add("verify")
            }
            
            if (sender.hasPermission("discordlite.2fa")) {
                subcommands.add("2fa")
            }
            
            if (sender.hasPermission("discordlite.use")) {
                subcommands.add("status")
            }
            
            if (sender.hasPermission("discordlite.admin")) {
                subcommands.addAll(listOf("unlink", "reload", "reset", "ban", "unban", "info", "logs"))
            }
            
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }
        
        if (args.size == 2) {
            when (args[0].lowercase()) {
                "2fa" -> return listOf("on", "off").filter { it.startsWith(args[1].lowercase()) }
                "unlink", "reset", "info" -> {
                    if (sender.hasPermission("discordlite.admin")) {
                        return plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                }
            }
        }
        
        return emptyList()
    }
}