package kynux.cloud.discordLite.database.models

enum class SecurityEventType(val displayName: String, val severity: Int, val emoji: String) {
    LOGIN("Giriş", 1, "✅"),
    FAILED_LOGIN("Başarısız Giriş", 3, "❌"),
    ACCOUNT_LINKED("Hesap Bağlandı", 2, "🔗"),
    ACCOUNT_UNLINKED("Hesap Bağlantısı Kesildi", 2, "🔓"),
    PERMISSION_CHANGE("İzin Değişikliği", 2, "⚡"),
    ROLE_SYNC("Rol Senkronizasyonu", 1, "🔄"),
    IP_BAN("IP Banlandı", 4, "🚫"),
    IP_UNBAN("IP Ban Kaldırıldı", 2, "✅"),
    SUSPICIOUS_ACTIVITY("Şüpheli Aktivite", 3, "⚠️"),
    BRUTE_FORCE_ATTEMPT("Brute Force Denemesi", 4, "🛡️"),
    INVALID_TOKEN("Geçersiz Token", 3, "🔑"),
    RATE_LIMIT_EXCEEDED("Rate Limit Aşıldı", 2, "⏰"),
    CONFIG_CHANGE("Konfigürasyon Değişikliği", 2, "⚙️"),
    ADMIN_ACTION("Admin İşlemi", 2, "👑"),
    SYSTEM_ERROR("Sistem Hatası", 3, "💥"),
    DATABASE_ERROR("Veritabanı Hatası", 4, "💾"),
    DISCORD_ERROR("Discord Hatası", 3, "🤖"),
    PLUGIN_RELOAD("Plugin Yeniden Yüklendi", 1, "🔄"),
    PLUGIN_SHUTDOWN("Plugin Kapatıldı", 1, "🔌"),
    UNAUTHORIZED_ACCESS("Yetkisiz Erişim", 4, "🚨"),
    DATA_BREACH_ATTEMPT("Veri İhlali Denemesi", 5, "🔥");
    
    companion object {
        fun fromString(name: String): SecurityEventType? {
            return values().find { it.name.equals(name, ignoreCase = true) }
        }
        
        fun getCriticalEvents(): List<SecurityEventType> {
            return values().filter { it.severity >= 4 }
        }
        
        fun getHighRiskEvents(): List<SecurityEventType> {
            return values().filter { it.severity >= 3 }
        }
    }
}