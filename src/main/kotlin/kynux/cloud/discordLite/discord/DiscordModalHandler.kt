package kynux.cloud.discordLite.discord

import kynux.cloud.discordLite.DiscordLite
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color

class DiscordModalHandler(private val plugin: DiscordLite) : ListenerAdapter() {
    
    override fun onModalInteraction(event: ModalInteractionEvent) {
        try {
            val modalId = event.modalId
            val user = event.user
            
            when {
                modalId == "persistent_verify_modal" -> {
                    handlePersistentVerificationModal(event, user.id)
                }
                else -> {
                    event.reply("❌ Bilinmeyen modal etkileşimi!").setEphemeral(true).queue()
                }
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("Modal interaction hatası: ${e.message}")
            e.printStackTrace()
            
            if (!event.isAcknowledged) {
                event.reply("❌ İşlem sırasında bir hata oluştu!").setEphemeral(true).queue()
            }
        }
    }
    
    private fun handlePersistentVerificationModal(event: ModalInteractionEvent, discordUserId: String) {
        val enteredCode = event.getValue("verification_code")?.asString
        
        if (enteredCode.isNullOrBlank()) {
            event.reply("❌ Lütfen doğrulama kodunu girin!").setEphemeral(true).queue()
            return
        }
        
        event.deferReply(true).queue()
        
        plugin.databaseManager.executeAsync {
            try {
                val result = plugin.linkingManager.completeLinkingSync(enteredCode, discordUserId)
                
                if (result.success) {
                    val successEmbed = EmbedBuilder().apply {
                        setTitle("✅ Hesap Doğrulama Başarılı!")
                        setDescription("Discord hesabınız Minecraft hesabınızla başarıyla eşlendi!")
                        addField("🎮 Minecraft Hesabı", result.data?.minecraftUsername ?: "Bilinmiyor", true)
                        addField("🎭 Discord Hesabı", "<@$discordUserId>", true)
                        addField("🕒 Eşleme Zamanı", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), false)
                        addField("🔐 Sonraki Adım", "Artık Minecraft'ta `/discordlite 2fa on` komutu ile 2FA'yı aktif edebilirsiniz!", false)
                        setColor(Color.GREEN)
                        setFooter("DiscordLite Account Verification System", null)
                    }.build()
                    
                    event.hook.editOriginalEmbeds(successEmbed).queue()
                    
                    plugin.discordManager.refreshPersistentVerificationEmbed()
                    
                    plugin.discordManager.sendLogMessage(
                        "🔗 Yeni Hesap Eşleme",
                        "**${result.data?.minecraftUsername ?: "Bilinmiyor"}** hesabı <@$discordUserId> ile eşlendi.",
                        Color.GREEN
                    )
                    
                    plugin.logger.info("Kalıcı sistem hesap doğrulama başarılı: ${result.playerName} -> $discordUserId (kod: $enteredCode)")
                    
                } else {
                    val errorEmbed = EmbedBuilder().apply {
                        setTitle("❌ Doğrulama Hatası")
                        setDescription("Hesap doğrulama işlemi başarısız!")
                        addField("📝 Hata", result.message, false)
                        addField("🔄 Ne Yapmalıyım?", "Minecraft'ta yeni bir doğrulama kodu oluşturun: `/discordlite verify`", false)
                        setColor(Color.RED)
                        setFooter("DiscordLite Account Verification System", null)
                    }.build()
                    
                    event.hook.editOriginalEmbeds(errorEmbed).queue()
                    
                    plugin.logger.warning("Kalıcı sistem hesap doğrulama başarısız: $discordUserId (girilen kod: $enteredCode) - ${result.message}")
                }
                
            } catch (e: Exception) {
                plugin.logger.severe("Kalıcı sistem modal verification hatası: ${e.message}")
                e.printStackTrace()
                
                val errorEmbed = EmbedBuilder().apply {
                    setTitle("❌ Sistem Hatası")
                    setDescription("Doğrulama işlemi sırasında beklenmeyen bir hata oluştu!")
                    addField("🔄 Ne Yapmalıyım?", "Lütfen tekrar deneyin veya admin ile iletişime geçin.", false)
                    setColor(Color.RED)
                    setFooter("DiscordLite Account Verification System", null)
                }.build()
                
                event.hook.editOriginalEmbeds(errorEmbed).queue()
            }
        }
    }
}