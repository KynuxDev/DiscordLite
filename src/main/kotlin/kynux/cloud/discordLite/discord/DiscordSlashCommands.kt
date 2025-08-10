package kynux.cloud.discordLite.discord

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.utils.EmbedUtils
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.modals.Modal
import kotlin.random.Random

class DiscordSlashCommands(private val plugin: DiscordLite) : ListenerAdapter() {
    
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {
            "link" -> handleLinkCommand(event)
            "unlink" -> handleUnlinkCommand(event)
            "status" -> handleStatusCommand(event)
            "help" -> handleHelpCommand(event)
        }
    }
    
    private fun handleLinkCommand(event: SlashCommandInteractionEvent) {
        val code = event.getOption("kod")?.asString
        
        if (code.isNullOrBlank()) {
            event.replyEmbeds(
                EmbedUtils.createErrorEmbed(
                    "Geçersiz Kod",
                    "Lütfen geçerli bir eşleme kodu girin!"
                )
            ).setEphemeral(true).queue()
            return
        }
        
        if (!isValidCode(code)) {
            event.replyEmbeds(
                EmbedUtils.createErrorEmbed(
                    "Geçersiz Kod Formatı",
                    "Eşleme kodu 6 haneli bir sayı olmalıdır!"
                )
            ).setEphemeral(true).queue()
            return
        }
        
        val discordUserId = event.user.id
        
        event.deferReply(true).queue()
        
        plugin.databaseManager.executeAsync {
            try {
                val result = plugin.linkingManager.completeLinkingSync(code, discordUserId)
                
                if (result.success) {
                    event.hook.editOriginalEmbeds(
                        EmbedUtils.createSuccessEmbed(
                            "Hesap Eşleme Başarılı!",
                            "Discord hesabınız Minecraft hesabınızla başarıyla eşlendi!",
                            "Minecraft Oyuncu: ${result.data?.minecraftUsername ?: "Bilinmiyor"}\nArtık oyunda 2FA'yı açabilirsiniz."
                        )
                    ).queue()
                    
                    plugin.discordManager.sendLogMessage(
                        "🔗 Yeni Hesap Eşleme",
                        "**${result.data?.minecraftUsername ?: "Bilinmiyor"}** hesabı ${event.user.asMention} ile eşlendi.",
                        java.awt.Color.GREEN
                    )
                } else {
                    event.hook.editOriginalEmbeds(
                        EmbedUtils.createErrorEmbed(
                            "Eşleme Hatası",
                            result.message ?: "Bilinmeyen hata",
                            "Lütfen Minecraft'ta /verify komutuyla yeni bir kod alın."
                        )
                    ).queue()
                }
                
            } catch (e: Exception) {
                plugin.logger.severe("Discord link komutu hatası: ${e.message}")
                e.printStackTrace()
                
                event.hook.editOriginalEmbeds(
                    EmbedUtils.createErrorEmbed(
                        "Sistem Hatası",
                        "İşlem sırasında beklenmeyen bir hata oluştu!"
                    )
                ).queue()
            }
        }
        
        plugin.logger.info("Discord link komutu kullanıldı: ${event.user.name} (${event.user.id}) - Kod: $code")
    }
    
    private fun handleUnlinkCommand(event: SlashCommandInteractionEvent) {
        val userId = event.user.id
        
        event.deferReply(true).queue()
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerDataByDiscordId(userId)
                
                if (playerData == null) {
                    event.hook.editOriginalEmbeds(
                        EmbedUtils.createErrorEmbed(
                            "Hesap Bulunamadı",
                            "Bu Discord hesabı herhangi bir Minecraft hesabıyla eşlenmemiş!"
                        )
                    ).queue()
                    return@executeAsync
                }
                
                val success = plugin.linkingManager.unlinkAccount(playerData.uuid)
                
                if (success) {
                    event.hook.editOriginalEmbeds(
                        EmbedUtils.createSuccessEmbed(
                            "Hesap Eşleme Kaldırıldı",
                            "Discord hesabınızın Minecraft ile eşlemesi başarıyla kaldırıldı!",
                            "Minecraft Hesabı: ${playerData.minecraftUsername}"
                        )
                    ).queue()
                    
                    plugin.discordManager.sendLogMessage(
                        "💔 Hesap Eşleme Kaldırıldı",
                        "**${playerData.minecraftUsername}** hesabının ${event.user.asMention} ile eşlemesi kaldırıldı.",
                        java.awt.Color.ORANGE
                    )
                } else {
                    event.hook.editOriginalEmbeds(
                        EmbedUtils.createErrorEmbed(
                            "İşlem Hatası",
                            "Hesap eşlemesi kaldırılırken bir hata oluştu!"
                        )
                    ).queue()
                }
                
            } catch (e: Exception) {
                plugin.logger.severe("Discord unlink komutu hatası: ${e.message}")
                event.hook.editOriginalEmbeds(
                    EmbedUtils.createErrorEmbed(
                        "Sistem Hatası",
                        "İşlem sırasında beklenmeyen bir hata oluştu!"
                    )
                ).queue()
            }
        }
        
        plugin.logger.info("Discord unlink komutu kullanıldı: ${event.user.name} (${event.user.id})")
    }
    
    private fun handleStatusCommand(event: SlashCommandInteractionEvent) {
        val userId = event.user.id
        
        event.deferReply(true).queue()
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerDataByDiscordId(userId)
                
                if (playerData == null) {
                    event.hook.editOriginalEmbeds(
                        EmbedUtils.createWarningEmbed(
                            "Hesap Eşlenmemiş",
                            "Bu Discord hesabı herhangi bir Minecraft hesabıyla eşlenmemiş!",
                            "Eşleme yapmak için `/link <kod>` komutunu kullanın."
                        )
                    ).queue()
                    return@executeAsync
                }
                
                val lastLoginText = playerData.lastLogin?.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) ?: "Hiç giriş yapılmamış"
                val linkedAtText = playerData.linkedAt?.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) ?: "Bilinmiyor"
                
                val playerInfoEmbed = EmbedUtils.createPlayerInfoEmbed(
                    playerData.minecraftUsername,
                    playerData.uuid,
                    userId,
                    playerData.twoFAEnabled,
                    lastLoginText
                )

                event.hook.editOriginalEmbeds(
                    playerInfoEmbed.addField("🔗 Eşleme Tarihi", linkedAtText, true).build()
                ).queue()
                
            } catch (e: Exception) {
                plugin.logger.severe("Discord status komutu hatası: ${e.message}")
                event.hook.editOriginalEmbeds(
                    EmbedUtils.createErrorEmbed(
                        "Sistem Hatası",
                        "Durum bilgisi alınırken bir hata oluştu!"
                    )
                ).queue()
            }
        }
        
        plugin.logger.info("Discord status komutu kullanıldı: ${event.user.name} (${event.user.id})")
    }
    
    private fun handleHelpCommand(event: SlashCommandInteractionEvent) {
        event.replyEmbeds(EmbedUtils.createHelpEmbed()).setEphemeral(true).queue()
        
        plugin.logger.info("Discord help komutu kullanıldı: ${event.user.name} (${event.user.id})")
    }
    
    private fun generateLinkingCode(): String {
        return Random.nextInt(100000, 999999).toString()
    }
    
    private fun isValidCode(code: String): Boolean {
        return code.matches(Regex("\\d{6}"))
    }
}