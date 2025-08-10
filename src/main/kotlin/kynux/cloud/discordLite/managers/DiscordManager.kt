package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.discord.DiscordButtonHandler
import kynux.cloud.discordLite.discord.DiscordModalHandler
import kynux.cloud.discordLite.discord.DiscordEventListener
import kynux.cloud.discordLite.utils.EmbedUtils
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import java.awt.Color

class DiscordManager(private val plugin: DiscordLite) {
    
    private var jda: JDA? = null
    private var guild: Guild? = null
    private var logChannel: TextChannel? = null
    private var verificationChannel: TextChannel? = null
    
    companion object {
        private const val PERSISTENT_VERIFICATION_MESSAGE_KEY = "persistent_verification_message_id"
    }
    
    fun initialize() {
        plugin.logger.info("DiscordManager başlatılıyor...")
        
        val botToken = plugin.configManager.getBotToken()
        if (botToken.isBlank() || botToken == "BOT_TOKEN_BURAYA") {
            plugin.logger.severe("Discord bot token ayarlanmamış! Lütfen config.yml dosyasında bot_token ayarlayın.")
            return
        }
        
        try {
            val builder = JDABuilder.createDefault(botToken)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.DIRECT_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT
                )
            
            builder.addEventListeners(
                DiscordButtonHandler(plugin),
                DiscordModalHandler(plugin)
            )
            
            jda = builder.build()
            jda?.awaitReady()
            
            plugin.logger.info("Discord bot başarıyla bağlandı: ${jda?.selfUser?.name}")
            
            setupGuildAndChannel()
            
            removeSlashCommands()
            
            sendStartupMessage()
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord bot başlatılırken hata oluştu: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun setupGuildAndChannel() {
        val guildId = plugin.configManager.getGuildId()
        val logChannelId = plugin.configManager.getLogChannelId()
        
        if (guildId.isBlank()) {
            plugin.logger.warning("Guild ID ayarlanmamış!")
            return
        }
        
        guild = jda?.getGuildById(guildId)
        if (guild == null) {
            plugin.logger.warning("Guild bulunamadı: $guildId")
            return
        }
        
        plugin.logger.info("Guild bağlantısı kuruldu: ${guild?.name}")
        
        if (logChannelId.isNotBlank()) {
            logChannel = guild?.getTextChannelById(logChannelId)
            if (logChannel == null) {
                plugin.logger.warning("Log kanalı bulunamadı: $logChannelId")
            } else {
                plugin.logger.info("Log kanalı ayarlandı: ${logChannel?.name}")
            }
        }
        
        val verificationChannelId = plugin.configManager.getVerificationChannelId()
        if (verificationChannelId.isNotBlank()) {
            verificationChannel = guild?.getTextChannelById(verificationChannelId)
            if (verificationChannel == null) {
                plugin.logger.warning("Verification kanalı bulunamadı: $verificationChannelId")
            } else {
                plugin.logger.info("Verification kanalı ayarlandı: ${verificationChannel?.name}")
            }
        }
    }
    
    private fun removeSlashCommands() {
        guild?.updateCommands()?.queue(
            { plugin.logger.info("Slash komutları başarıyla kaldırıldı! Artık sadece Minecraft tabanlı sistem kullanılacak.") },
            { error -> plugin.logger.severe("Slash komutları kaldırılırken hata: ${error.message}") }
        )
    }
    
    private fun sendStartupMessage() {
        if (logChannel != null) {
            val embed = EmbedUtils.createSuccessEmbed(
                "🟢 Bot Başlatıldı",
                "DiscordLite plugin başarıyla başlatıldı ve Discord'a bağlandı!",
                "✅ Minecraft tabanlı doğrulama sistemi aktif\n" +
                "✅ Verification kanalı hazır\n" +
                "✅ Modal interaction sistemi hazır\n" +
                "✅ Kalıcı doğrulama embed sistemi hazır"
            )
            
            logChannel?.sendMessageEmbeds(embed)?.queue()
        }
        
        setupPersistentVerificationEmbed()
    }
    
    fun sendLogMessage(title: String, description: String, color: Color = Color.ORANGE) {
        if (logChannel != null) {
            val embed = EmbedUtils.createEmbed(title, description, color)
            logChannel?.sendMessageEmbeds(embed)?.queue()
        }
    }
    
    fun sendSecurityAlert(title: String, description: String, details: String? = null) {
        if (logChannel != null) {
            val embed = EmbedUtils.createErrorEmbed(title, description, details)
            logChannel?.sendMessageEmbeds(embed)?.queue()
        }
    }
    
    fun getJDA(): JDA? = jda
    
    fun getGuild(): Guild? = guild
    
    fun getLogChannel(): TextChannel? = logChannel
    
    fun getVerificationChannel(): TextChannel? = verificationChannel
    
    fun isConnected(): Boolean = jda != null && jda?.status?.name == "CONNECTED"
    
    private fun setupPersistentVerificationEmbed() {
        if (!plugin.configManager.isPersistentVerificationEnabled()) {
            plugin.logger.info("Kalıcı doğrulama embed sistemi devre dışı")
            return
        }
        
        if (verificationChannel == null) {
            plugin.logger.warning("Verification kanalı bulunamadı, kalıcı embed oluşturulamıyor")
            return
        }
        
        plugin.databaseManager.executeAsync {
            try {
                val existingMessageId = plugin.databaseManager.provider.getSystemSetting(PERSISTENT_VERIFICATION_MESSAGE_KEY)
                
                if (existingMessageId != null && plugin.configManager.isPersistentVerificationAutoUpdate()) {
                    updatePersistentVerificationEmbed(existingMessageId)
                } else {
                    createNewPersistentVerificationEmbed()
                }
            } catch (e: Exception) {
                plugin.logger.severe("Kalıcı doğrulama embed kurulumu hatası: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun updatePersistentVerificationEmbed(messageId: String) {
        verificationChannel?.retrieveMessageById(messageId)?.queue(
            { message ->
                plugin.databaseManager.executeAsync {
                    val activePendingCount = getActivePendingVerificationsCount()
                    
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        val embed = createPersistentVerificationEmbedFromConfig(activePendingCount)
                        val button = net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                            "persistent_verify",
                            plugin.configManager.getPersistentVerificationButtonLabel()
                        )
                        
                        message.editMessageEmbeds(embed)
                            .setComponents(net.dv8tion.jda.api.interactions.components.ActionRow.of(button))
                            .queue(
                                {
                                    plugin.logger.info("Kalıcı doğrulama embed'i güncellendi")
                                },
                                { error ->
                                    plugin.logger.warning("Kalıcı embed güncelleme hatası: ${error.message}")
                                    createNewPersistentVerificationEmbed()
                                }
                            )
                    })
                }
            },
            { error ->
                plugin.logger.warning("Kalıcı embed mesajı bulunamadı: ${error.message}")
                createNewPersistentVerificationEmbed()
            }
        )
    }
    
    private fun createNewPersistentVerificationEmbed() {
        plugin.databaseManager.executeAsync {
            val activePendingCount = getActivePendingVerificationsCount()
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                val embed = createPersistentVerificationEmbedFromConfig(activePendingCount)
                val button = net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                    "persistent_verify",
                    plugin.configManager.getPersistentVerificationButtonLabel()
                )
                
                verificationChannel?.sendMessageEmbeds(embed)
                    ?.addActionRow(button)
                    ?.queue(
                        { message ->
                            plugin.databaseManager.executeAsync {
                                plugin.databaseManager.provider.setSystemSetting(
                                    PERSISTENT_VERIFICATION_MESSAGE_KEY,
                                    message.id
                                )
                                plugin.logger.info("Yeni kalıcı doğrulama embed'i oluşturuldu: ${message.id}")
                            }
                        },
                        { error ->
                            plugin.logger.severe("Kalıcı embed oluşturma hatası: ${error.message}")
                        }
                    )
            })
        }
    }
    
    private fun createPersistentVerificationEmbedFromConfig(activePendingCount: Int): net.dv8tion.jda.api.entities.MessageEmbed {
        return EmbedUtils.createPersistentVerificationEmbed(
            title = plugin.configManager.getEmbedTitle("persistent_verification"),
            description = plugin.configManager.getEmbedDescription("persistent_verification"),
            color = plugin.configManager.getEmbedColor("persistent_verification"),
            footer = plugin.configManager.getEmbedFooter("persistent_verification"),
            activePendingCount = activePendingCount
        )
    }
    
    private suspend fun getActivePendingVerificationsCount(): Int {
        return try {
            0
        } catch (e: Exception) {
            0
        }
    }
    
    fun refreshPersistentVerificationEmbed() {
        if (!plugin.configManager.isPersistentVerificationEnabled()) return
        
        plugin.databaseManager.executeAsync {
            val messageId = plugin.databaseManager.provider.getSystemSetting(PERSISTENT_VERIFICATION_MESSAGE_KEY)
            if (messageId != null) {
                updatePersistentVerificationEmbed(messageId)
            }
        }
    }
    
    fun shutdown() {
        plugin.logger.info("DiscordManager kapatılıyor...")
        
        try {
            if (logChannel != null) {
                val embed = EmbedUtils.createErrorEmbed(
                    "🔴 Bot Kapatıldı",
                    "DiscordLite plugin kapatıldı.",
                    "Bot bağlantısı kesildi."
                )
                logChannel?.sendMessageEmbeds(embed)?.complete()
            }
            
            jda?.shutdown()
            jda = null
            
            plugin.logger.info("Discord bot bağlantısı başarıyla kapatıldı")
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord bot kapatılırken hata oluştu: ${e.message}")
            e.printStackTrace()
        }
    }
}