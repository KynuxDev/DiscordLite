package kynux.cloud.discordLite.discord

import kynux.cloud.discordLite.DiscordLite
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import java.awt.Color

class DiscordButtonHandler(private val plugin: DiscordLite) : ListenerAdapter() {
    
    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        try {
            val buttonId = event.componentId
            val user = event.user
            
            when {
                buttonId == "persistent_verify" -> {
                    handlePersistentVerification(event, user.id)
                }
                buttonId.startsWith("2fa_approve_") -> {
                    handle2FAApproval(event, buttonId, user.id)
                }
                buttonId.startsWith("2fa_deny_") -> {
                    handle2FADenial(event, buttonId, user.id)
                }
                buttonId.startsWith("link_approve_") -> {
                    handleLinkApproval(event, buttonId, user.id)
                }
                buttonId.startsWith("link_deny_") -> {
                    handleLinkDenial(event, buttonId, user.id)
                }
                buttonId.startsWith("2fa_deny_confirm_") -> {
                    handle2FADenialConfirm(event, buttonId, user.id)
                }
                buttonId.startsWith("2fa_deny_cancel_") -> {
                    handle2FADenialCancel(event, buttonId, user.id)
                }
                else -> {
                    event.reply("❌ Bilinmeyen buton etkileşimi!").setEphemeral(true).queue()
                }
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("Button interaction hatası: ${e.message}")
            e.printStackTrace()
            
            if (!event.isAcknowledged) {
                event.reply("❌ İşlem sırasında bir hata oluştu!").setEphemeral(true).queue()
            }
        }
    }
    
    private fun handlePersistentVerification(event: ButtonInteractionEvent, discordUserId: String) {
        val modal = Modal.create("persistent_verify_modal", "Hesap Doğrulama")
            .addComponents(ActionRow.of(
                TextInput.create("verification_code", "Doğrulama Kodu", TextInputStyle.SHORT)
                    .setPlaceholder("6 haneli doğrulama kodunu girin")
                    .setRequiredRange(6, 6)
                    .build()
            )).build()
        
        event.replyModal(modal).queue()
        
        plugin.logger.info("Kalıcı doğrulama modal'ı açıldı: $discordUserId")
    }
    
    private fun handle2FAApproval(event: ButtonInteractionEvent, buttonId: String, discordUserId: String) {
        val verificationId = buttonId.removePrefix("2fa_approve_")
        
        event.deferEdit().queue()
        
        val success = plugin.twoFAManager.approveVerification(verificationId, discordUserId)
        
        if (success) {
            val successEmbed = EmbedBuilder().apply {
                setTitle("✅ 2FA Doğrulaması Onaylandı")
                setDescription("Giriş işlemi başarıyla onaylandı!")
                addField("🎮 Durum", "Oyuncu sunucuya giriş yapabilir", false)
                addField("🕒 Onay Zamanı", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), false)
                setColor(Color.GREEN)
                setFooter("DiscordLite 2FA Security System", null)
            }.build()
            
            event.hook.editOriginalEmbeds(successEmbed).setComponents().queue()
            
            plugin.logger.info("2FA doğrulaması onaylandı: $verificationId by $discordUserId")
            
        } else {
            val errorEmbed = EmbedBuilder().apply {
                setTitle("❌ Doğrulama Hatası")
                setDescription("Bu doğrulama artık geçerli değil veya zaten işlenmiş.")
                addField("🔄 Ne Yapmalıyım?", "Oyuncu yeniden giriş yapmayı deneyebilir", false)
                setColor(Color.RED)
                setFooter("DiscordLite 2FA Security System", null)
            }.build()
            
            event.hook.editOriginalEmbeds(errorEmbed).setComponents().queue()
        }
    }
    
    private fun handle2FADenial(event: ButtonInteractionEvent, buttonId: String, discordUserId: String) {
        val verificationId = buttonId.removePrefix("2fa_deny_")
        
        val confirmEmbed = EmbedBuilder().apply {
            setTitle("⚠️ Doğrulama Reddi Onayı")
            setDescription("Bu giriş denemesini reddetmek istediğinizden emin misiniz?")
            addField("🚨 Uyarı", "Bu işlem giriş yapan kişiyi sunucudan atacak ve IP adresini banlayabilir!", false)
            addField("❓ Emin misiniz?", "Sadece bu sizin girişiniz değilse reddedin!", false)
            setColor(Color.ORANGE)
            setFooter("DiscordLite 2FA Security System", null)
        }.build()
        
        val confirmButton = net.dv8tion.jda.api.interactions.components.buttons.Button.danger("2fa_deny_confirm_$verificationId", "🚨 Evet, Reddet")
        val cancelButton = net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("2fa_deny_cancel_$verificationId", "❌ İptal")
        
        event.replyEmbeds(confirmEmbed)
            .addActionRow(confirmButton, cancelButton)
            .setEphemeral(true)
            .queue()
    }
    
    private fun handleLinkApproval(event: ButtonInteractionEvent, buttonId: String, discordUserId: String) {
        val linkCode = buttonId.removePrefix("link_approve_")
        
        event.deferEdit().queue()
        
        val result = plugin.linkingManager.completeLinkingSync(linkCode, discordUserId)
        
        if (result.success) {
            val successEmbed = EmbedBuilder().apply {
                setTitle("✅ Hesap Eşleme Başarılı!")
                setDescription("Discord hesabınız Minecraft hesabınızla başarıyla eşlendi!")
                addField("🎮 Minecraft Hesabı", result.data?.minecraftUsername ?: "Bilinmiyor", true)
                addField("🎭 Discord Hesabı", "<@$discordUserId>", true)
                addField("🕒 Eşleme Zamanı", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), false)
                addField("🔐 Sonraki Adım", "Artık `/discordlite 2fa on` komutu ile 2FA'yı aktif edebilirsiniz!", false)
                setColor(Color.GREEN)
                setFooter("DiscordLite Linking System", null)
            }.build()
            
            event.hook.editOriginalEmbeds(successEmbed).setComponents().queue()
            
            plugin.logger.info("Hesap eşleme onaylandı: $linkCode -> $discordUserId (${result.data?.minecraftUsername ?: "Bilinmiyor"})")
        } else {
            val errorEmbed = EmbedBuilder().apply {
                setTitle("❌ Eşleme Hatası")
                setDescription("Hesap eşleme işlemi başarısız!")
                addField("📝 Hata", result.message, false)
                addField("🔄 Ne Yapmalıyım?", "Minecraft'ta yeni bir eşleme kodu oluşturun: `/discordlite link`", false)
                setColor(Color.RED)
                setFooter("DiscordLite Linking System", null)
            }.build()
            
            event.hook.editOriginalEmbeds(errorEmbed).setComponents().queue()
        }
    }
    
    private fun handleLinkDenial(event: ButtonInteractionEvent, buttonId: String, discordUserId: String) {
        val linkCode = buttonId.removePrefix("link_deny_")
        
        event.deferEdit().queue()
        
        plugin.linkingManager.cancelLinking(linkCode)
        
        val deniedEmbed = EmbedBuilder().apply {
            setTitle("❌ Hesap Eşleme Reddedildi")
            setDescription("Hesap eşleme işlemi reddedildi.")
            addField("🔒 Güvenlik", "Bu eşleme denemesi güvenlik nedeniyle reddedildi", false)
            addField("📝 Bilgi", "Eğer bu sizin işleminiz değilse, doğru kararı verdiniz!", false)
            setColor(Color.RED)
            setFooter("DiscordLite Linking System", null)
        }.build()
        
        event.hook.editOriginalEmbeds(deniedEmbed).setComponents().queue()
        
        plugin.logger.warning("Hesap eşleme reddedildi: $linkCode by $discordUserId")
    }
    
    private fun handle2FADenialConfirm(event: ButtonInteractionEvent, buttonId: String, discordUserId: String) {
        val verificationId = buttonId.removePrefix("2fa_deny_confirm_")
        
        event.deferEdit().queue()
        
        val success = plugin.twoFAManager.denyVerification(verificationId, discordUserId)
        
        if (success) {
            val deniedEmbed = EmbedBuilder().apply {
                setTitle("🚨 2FA Doğrulaması Reddedildi")
                setDescription("Giriş denemesi başarıyla reddedildi!")
                addField("🔒 Güvenlik", "Bu giriş denemesi güvenlik nedeniyle reddedildi", false)
                addField("⚡ Sonuç", "Oyuncu sunucudan atıldı ve IP adresi banlandı", false)
                addField("🕒 Red Zamanı", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), false)
                setColor(Color.RED)
                setFooter("DiscordLite 2FA Security System", null)
            }.build()
            
            event.hook.editOriginalEmbeds(deniedEmbed).setComponents().queue()
            
            plugin.logger.warning("2FA doğrulaması reddedildi: $verificationId by $discordUserId")
            
        } else {
            val errorEmbed = EmbedBuilder().apply {
                setTitle("❌ Red İşlemi Hatası")
                setDescription("Bu doğrulama artık geçerli değil veya zaten işlenmiş.")
                setColor(Color.ORANGE)
                setFooter("DiscordLite 2FA Security System", null)
            }.build()
            
            event.hook.editOriginalEmbeds(errorEmbed).setComponents().queue()
        }
    }
    
    private fun handle2FADenialCancel(event: ButtonInteractionEvent, buttonId: String, discordUserId: String) {
        val verificationId = buttonId.removePrefix("2fa_deny_cancel_")
        
        event.deferEdit().queue()
        
        val cancelEmbed = EmbedBuilder().apply {
            setTitle("↩️ İşlem İptal Edildi")
            setDescription("2FA red işlemi iptal edildi.")
            addField("ℹ️ Bilgi", "Giriş doğrulaması hala beklemede", false)
            setColor(Color.GRAY)
            setFooter("DiscordLite 2FA Security System", null)
        }.build()
        
        event.hook.editOriginalEmbeds(cancelEmbed).setComponents().queue()
        
        plugin.logger.info("2FA red işlemi iptal edildi: $verificationId by $discordUserId")
    }
}