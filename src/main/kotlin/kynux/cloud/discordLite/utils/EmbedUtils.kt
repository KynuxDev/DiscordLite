package kynux.cloud.discordLite.utils

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color
import java.time.Instant

object EmbedUtils {
    
    fun createEmbed(title: String, description: String, color: Color = Color.BLUE): MessageEmbed {
        return EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(color)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System", null)
            .build()
    }
    
    fun createSuccessEmbed(title: String, description: String, details: String? = null): MessageEmbed {
        val builder = EmbedBuilder()
            .setTitle("✅ $title")
            .setDescription(description)
            .setColor(Color.GREEN)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System", null)
        
        if (details != null) {
            builder.addField("Detaylar", details, false)
        }
        
        return builder.build()
    }
    
    fun createErrorEmbed(title: String, description: String, details: String? = null): MessageEmbed {
        val builder = EmbedBuilder()
            .setTitle("❌ $title")
            .setDescription(description)
            .setColor(Color.RED)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System", null)
        
        if (details != null) {
            builder.addField("Detaylar", details, false)
        }
        
        return builder.build()
    }
    
    fun createWarningEmbed(title: String, description: String, details: String? = null): MessageEmbed {
        val builder = EmbedBuilder()
            .setTitle("⚠️ $title")
            .setDescription(description)
            .setColor(Color.ORANGE)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System", null)
        
        if (details != null) {
            builder.addField("Detaylar", details, false)
        }
        
        return builder.build()
    }
    
    fun createVerificationEmbed(
        playerName: String,
        ipAddress: String,
        code: String,
        expiresIn: Int
    ): MessageEmbed {
        return EmbedBuilder()
            .setTitle("🔐 Minecraft Giriş Onayı")
            .setDescription("**$playerName** adlı oyuncu sunucuya giriş yapmaya çalışıyor.")
            .addField("🌐 IP Adresi", "`$ipAddress`", true)
            .addField("🔢 Doğrulama Kodu", "`$code`", true)
            .addField("⏱️ Süre", "$expiresIn saniye", true)
            .setColor(Color.CYAN)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System • Bu işlem $expiresIn saniye içinde süresi dolacak", null)
            .build()
    }
    
    fun createVerificationEmbed(
        playerName: String,
        verificationCode: String,
        playerUUID: String
    ): MessageEmbed {
        return EmbedBuilder()
            .setTitle("🔐 Yeni Hesap Doğrulama Talebi")
            .setDescription("**$playerName** adlı oyuncu Discord hesabını eşlemek istiyor.")
            .addField("🎮 Oyuncu", playerName, true)
            .addField("🆔 UUID", "`$playerUUID`", true)
            .addField("⏱️ Süre", "5 dakika", true)
            .addField(
                "📝 Yapmanız Gerekenler",
                "1. Aşağıdaki **Hesabı Doğrula** butonuna tıklayın\n" +
                "2. Açılan pencereye kodu girin: `$verificationCode`\n" +
                "3. Onaylayın",
                false
            )
            .addField("⚠️ Güvenlik", "Eğer bu sizin işleminiz değilse butona tıklamayın!", false)
            .setColor(Color.ORANGE)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Account Verification System", null)
            .build()
    }
    
    fun createPlayerInfoEmbed(
        playerName: String,
        uuid: String,
        discordId: String,
        twoFAEnabled: Boolean,
        lastLogin: String?
    ): EmbedBuilder {
        val builder = EmbedBuilder()
            .setTitle("👤 Oyuncu Bilgileri")
            .addField("🎮 Oyuncu Adı", playerName, true)
            .addField("🆔 UUID", "`$uuid`", true)
            .addField("💬 Discord ID", "<@$discordId>", true)
            .addField("🔐 2FA Durumu", if (twoFAEnabled) "✅ Aktif" else "❌ Pasif", true)
            .setColor(Color.BLUE)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System", null)
        
        if (lastLogin != null) {
            builder.addField("🕐 Son Giriş", lastLogin, true)
        }
        
        return builder
    }
    
    fun createHelpEmbed(): MessageEmbed {
        return EmbedBuilder()
            .setTitle("📚 DiscordLite Komutları")
            .setDescription("Aşağıdaki komutları kullanabilirsiniz:")
            .addField(
                "🔗 Hesap Eşleme",
                "`/link <kod>` - Minecraft hesabınızı Discord ile eşleyin\n" +
                "`/unlink` - Hesap eşlemesini kaldırın",
                false
            )
            .addField(
                "📊 Durum",
                "`/status` - Hesap durumunuzu kontrol edin",
                false
            )
            .addField(
                "❓ Yardım",
                "`/help` - Bu yardım mesajını görüntüleyin",
                false
            )
            .setColor(Color.BLUE)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System", null)
            .build()
    }
    
    fun createLinkingEmbed(code: String, expiresIn: Int): MessageEmbed {
        return EmbedBuilder()
            .setTitle("🔗 Hesap Eşleme Başlatıldı")
            .setDescription("Discord hesabınızı Minecraft hesabınızla eşlemek için aşağıdaki kodu kullanın.")
            .addField("🔢 Eşleme Kodu", "`$code`", false)
            .addField("📝 Kullanım", "Minecraft'ta `/discordlite link` komutunu kullanın", false)
            .addField("⏱️ Süre", "Bu kod $expiresIn saniye geçerlidir", false)
            .setColor(Color.GREEN)
            .setTimestamp(Instant.now())
            .setFooter("DiscordLite Security System", null)
            .build()
    }
    
    fun createPersistentVerificationEmbed(
        title: String = "🔐 DiscordLite Hesap Doğrulama",
        description: String = "Minecraft hesabınızı Discord ile eşleştirmek için aşağıdaki adımları takip edin.",
        color: Color = Color.decode("#00ff88"),
        footer: String = "DiscordLite Persistent Verification System",
        activePendingCount: Int = 0
    ): MessageEmbed {
        val builder = EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(color)
            .setTimestamp(Instant.now())
            .setFooter(footer, null)
        
        if (activePendingCount > 0) {
            builder.addField("📊 Bekleyen Doğrulamalar", "$activePendingCount doğrulama bekleniyor", true)
        }
        

        builder.addField("🔄 Son Güncelleme", "Az önce", true)
        
        return builder.build()
    }
}