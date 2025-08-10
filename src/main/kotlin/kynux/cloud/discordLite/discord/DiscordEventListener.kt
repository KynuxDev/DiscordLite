package kynux.cloud.discordLite.discord

import kynux.cloud.discordLite.DiscordLite
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class DiscordEventListener(private val plugin: DiscordLite) : ListenerAdapter() {
    
    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        try {
            val member = event.member
            val addedRoles = event.roles
            
            plugin.logger.info("Discord rol eklendi: ${member.user.name} - ${addedRoles.map { it.name }}")
            
            plugin.permissionManager.onDiscordRoleUpdate(member.id)
            
            plugin.discordManager.sendLogMessage(
                "🎭 Rol Eklendi",
                "**Üye:** ${member.asMention}\n**Eklenen Roller:** ${addedRoles.joinToString(", ") { it.asMention }}\n**Zaman:** ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
            )
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord rol ekleme event hatası: ${e.message}")
        }
    }
    
    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) {
        try {
            val member = event.member
            val removedRoles = event.roles
            
            plugin.logger.info("Discord rol kaldırıldı: ${member.user.name} - ${removedRoles.map { it.name }}")
            
            plugin.permissionManager.onDiscordRoleUpdate(member.id)
            
            plugin.discordManager.sendLogMessage(
                "🎭 Rol Kaldırıldı",
                "**Üye:** ${member.asMention}\n**Kaldırılan Roller:** ${removedRoles.joinToString(", ") { it.asMention }}\n**Zaman:** ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
            )
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord rol kaldırma event hatası: ${e.message}")
        }
    }
    
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        try {
            val member = event.member
            
            plugin.logger.info("Discord sunucusuna yeni üye katıldı: ${member.user.name}")
            
            plugin.databaseManager.executeAsync {
                try {
                    val playerData = plugin.databaseManager.provider.getPlayerDataByDiscordId(member.id)
                    
                    if (playerData != null) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            giveVerifiedRole(member)
                        })
                        
                        plugin.logger.info("Eşleştirilmiş hesap tespit edildi: ${playerData.minecraftUsername} -> ${member.user.name}")
                    } else {
                        sendWelcomeMessage(member)
                    }
                    
                } catch (e: Exception) {
                    plugin.logger.warning("Discord üye katılma kontrolü hatası: ${e.message}")
                }
            }
            
            plugin.discordManager.sendLogMessage(
                "👋 Yeni Üye",
                "**Üye:** ${member.asMention}\n**Kullanıcı Adı:** ${member.user.name}\n**Hesap Oluşturma:** ${member.user.timeCreated.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}\n**Katılma Zamanı:** ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
            )
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord üye katılma event hatası: ${e.message}")
        }
    }
    
    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        try {
            val user = event.user
            
            plugin.logger.info("Discord sunucusundan üye ayrıldı: ${user.name}")
            
            plugin.databaseManager.executeAsync {
                try {
                    val playerData = plugin.databaseManager.provider.getPlayerDataByDiscordId(user.id)
                    
                    if (playerData != null) {
                        val minecraftPlayer = plugin.server.getPlayer(java.util.UUID.fromString(playerData.uuid))
                        if (minecraftPlayer != null && minecraftPlayer.isOnline) {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                plugin.permissionManager.clearPlayerDiscordPermissions(minecraftPlayer)
                            })
                        }
                        
                        plugin.logger.info("Eşleştirilmiş hesap Discord'dan ayrıldı: ${playerData.minecraftUsername}")
                    }
                    
                } catch (e: Exception) {
                    plugin.logger.warning("Discord üye ayrılma kontrolü hatası: ${e.message}")
                }
            }
            
            plugin.discordManager.sendLogMessage(
                "👋 Üye Ayrıldı",
                "**Kullanıcı:** ${user.name}#${user.discriminator}\n**ID:** ${user.id}\n**Ayrılma Zamanı:** ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
            )
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord üye ayrılma event hatası: ${e.message}")
        }
    }
    
    private fun giveVerifiedRole(member: net.dv8tion.jda.api.entities.Member) {
        try {
            val verifiedRoleId = plugin.configManager.getConfig().getString("discord_roles.verified")
            if (verifiedRoleId.isNullOrBlank()) {
                return
            }
            
            val guild = plugin.discordManager.getGuild()
            val verifiedRole = guild?.getRoleById(verifiedRoleId)
            
            if (verifiedRole != null && !member.roles.contains(verifiedRole)) {
                guild.addRoleToMember(member, verifiedRole).queue(
                    {
                        plugin.logger.info("Verified rol verildi: ${member.user.name}")
                        
                        member.user.openPrivateChannel().queue({ channel ->
                            channel.sendMessage(
                                "✅ **Minecraft Hesabınız Doğrulandı!**\n\n" +
                                "Discord hesabınız Minecraft sunucumuzla eşleştirilmiş. Artık özel kanalları görebilir ve sunucudaki tüm özelliklerden yararlanabilirsiniz!\n\n" +
                                "**Sunucu IP:** `${plugin.configManager.getConfig().getString("server.ip", "play.example.com")}`\n" +
                                "**Websitesi:** ${plugin.configManager.getConfig().getString("server.website", "https://example.com")}"
                            ).queue()
                        }, { 
                            plugin.logger.warning("Verified role DM gönderilemedi: ${member.user.name}")
                        })
                    },
                    { error ->
                        plugin.logger.warning("Verified rol verme hatası: ${error.message}")
                    }
                )
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("Verified rol verme hatası: ${e.message}")
        }
    }
    
    private fun sendWelcomeMessage(member: net.dv8tion.jda.api.entities.Member) {
        try {
            val welcomeChannelId = plugin.configManager.getConfig().getString("discord_channels.welcome")
            if (welcomeChannelId.isNullOrBlank()) {
                return
            }
            
            val guild = plugin.discordManager.getGuild()
            val welcomeChannel = guild?.getTextChannelById(welcomeChannelId)
            
            welcomeChannel?.sendMessage(
                "🎉 **Hoş geldin ${member.asMention}!**\n\n" +
                "Minecraft sunucumuzun Discord'una katıldığın için teşekkürler!\n\n" +
                "**Minecraft hesabını Discord ile eşlemek için:**\n" +
                "1. Sunucuya bağlan: `${plugin.configManager.getConfig().getString("server.ip", "play.example.com")}`\n" +
                "2. `/discordlite link` komutunu kullan\n" +
                "3. Aldığın kodu `/link <kod>` şeklinde buraya yaz\n\n" +
                "**Kuralları oku:** <#${plugin.configManager.getConfig().getString("discord_channels.rules", "")}>\n" +
                "**Yardım için:** <#${plugin.configManager.getConfig().getString("discord_channels.help", "")}>"
            )?.queue()
            
        } catch (e: Exception) {
            plugin.logger.warning("Hoş geldin mesajı gönderme hatası: ${e.message}")
        }
    }
}