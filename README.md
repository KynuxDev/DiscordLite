# DiscordLite

[![Minecraft](https://img.shields.io/badge/Minecraft-1.19+-green)](https://minecraft.net)
[![Discord](https://img.shields.io/badge/Discord-JDA%205.0.0-blue)](https://github.com/DV8FromTheWorld/JDA)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.8+-purple)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**Minecraft Tabanlı Discord Hesap Eşleme ve 2FA Güvenlik Sistemi**

## ✨ Özellikler

### 🔐 Güvenlik Sistemi
- **Minecraft Tabanlı Doğrulama**: [`/verify`](src/main/kotlin/com/kynux/discordlite/commands/VerifyCommand.kt) komutu ile basit hesap eşleme
- **Discord Kanal Entegrasyonu**: Verification kanalında interactive button sistemi
- **Modal Code Input**: Güvenli kod girme sistemi
- **2FA (Two-Factor Authentication)**: Discord üzerinden ek güvenlik onayı
- **IP Ban Sistemi**: Şüpheli IP adreslerini otomatik olarak banlama

### 🔧 Teknik Özellikler  
- **Multi-Database Desteği**: SQLite, MySQL, YAML/JSON desteği
- **HikariCP Connection Pooling**: Performanslı veritabanı erişimi
- **Kotlin Coroutines**: Asenkron işlem desteği
- **Kapsamlı Logging**: 21 farklı güvenlik event tipi
- **Rol Senkronizasyonu**: Discord rolleri ↔ Minecraft permission'ları

### 🎮 Kullanıcı Deneyimi
- **Basit Komutlar**: [`/verify`](src/main/kotlin/com/kynux/discordlite/commands/VerifyCommand.kt), [`/discordlite`](src/main/kotlin/com/kynux/discordlite/commands/DiscordLiteCommand.kt) komutları
- **Interactive UI**: Discord button'ları ve modal'lar
- **Multi-language**: Türkçe ve İngilizce destek
- **Real-time Feedback**: Anlık bildirimler

### ⚙️ Yönetim Araçları
- **Admin Komutları**: Kapsamlı yönetim sistemi
- **Security Dashboard**: Güvenlik logları ve monitoring
- **Permission Management**: Gelişmiş yetki sistemi
- **Rate Limiting**: Brute force koruması

## 🚀 Kurulum

### 1. Plugin Kurulumu
```bash
# Plugin JAR dosyasını plugins klasörüne koyun
mv DiscordLite.jar plugins/

# Sunucuyu başlatın (ilk çalıştırma)
./start.sh
```

### 2. Discord Bot Kurulumu
1. [Discord Developer Portal](https://discord.com/developers/applications)
2. Yeni Application oluşturun
3. Bot sekmesinden Token'ı kopyalayın
4. Bot Permissions: `Send Messages`, `Use Slash Commands`, `Embed Links`, `View Channels`

### 3. Konfigürasyon
[`plugins/DiscordLite/config.yml`](config.yml) dosyasını düzenleyin:

```yaml
discord:
  bot_token: "YOUR_BOT_TOKEN"
  guild_id: "YOUR_DISCORD_SERVER_ID"
  
  channels:
    log_channel_id: "LOG_CHANNEL_ID"
    verification_channel_id: "VERIFICATION_CHANNEL_ID"  # YENİ!
    security_channel_id: "SECURITY_CHANNEL_ID"
```

### 4. Sunucuyu Yeniden Başlatın
```bash
./restart.sh
```

## 📖 Kullanım

### 🎯 Yeni Hesap Doğrulama Akışı

#### 1. Minecraft Tarafı
```bash
# Oyuncu Minecraft'ta doğrulama başlatır
/verify
```
```
§6§l=== Discord Hesap Doğrulama ===
§aDoğrulama kodunuz: §f§l123456
§eDiscord sunucusundaki doğrulama kanalını kontrol edin
§eOrada çıkan butona tıklayıp bu kodu girin!
§7Bu kod 5 dakika geçerlidir.
```

#### 2. Discord Tarafı
Verification kanalında şu embed gözükür:
```
🔐 Yeni Hesap Doğrulama Talebi
[PlayerName] adlı oyuncu Discord hesabını eşlemek istiyor.

🎮 Oyuncu: PlayerName
🆔 UUID: abc-123-def
⏱️ Süre: 5 dakika

📝 Yapmanız Gerekenler:
1. Aşağıdaki Hesabı Doğrula butonuna tıklayın
2. Açılan pencereye kodu girin: 123456
3. Onaylayın

[🔐 Hesabı Doğrula] <- Button
```

#### 3. Modal ve Onay
- Button'a tıklama → Modal açılır
- Kod girme → Doğrulama 
- Başarı mesajı → Hesap eşlenir

### 🔒 2FA Kullanımı
```bash
# 2FA'yı açma (hesap eşlendikten sonra)
/discordlite 2fa on

# Durum kontrol
/discordlite 2fa status

# 2FA'yı kapatma
/discordlite 2fa off
```

### 👑 Admin Komutları
```bash
# Oyuncu bilgileri
/discordlite info <oyuncu>

# Eşleme kaldırma
/discordlite unlink <oyuncu>

# IP ban/unban
/discordlite ban <ip> [süre] [sebep]
/discordlite unban <ip>

# Güvenlik logları
/discordlite logs [sayfa] [oyuncu] [event_type]

# Oyuncu verilerini sıfırlama
/discordlite reset <oyuncu> confirm
```

## 🔧 Teknik Detaylar

### Veritabanı Desteği
```yaml
database:
  type: "sqlite"  # sqlite, mysql, yaml
  
  mysql:
    host: "localhost"
    port: 3306
    database: "discordlite"
    username: "user"
    password: "pass"
    
  connection_pool:
    min_idle: 2
    max_pool_size: 10
    connection_timeout: 30000
```

### Güvenlik Özellikleri
- **Rate Limiting**: Dakikada max 10 istek
- **IP Whitelisting**: Güvenli IP listesi
- **Brute Force Protection**: 5 başarısız denemeden sonra ban
- **Anomaly Detection**: Anormal aktivite tespiti
- **Security Scoring**: Risk değerlendirme sistemi

### Performance Ayarları
```yaml
performance:
  cache:
    enabled: true
    max_size: 1000
    expire_after_write: 300
    
  thread_pool:
    core_size: 2
    max_size: 10
    queue_capacity: 100
```

## 🛡️ Permission Sistemi

### Kullanıcı Permission'ları
```yaml
discordlite.verify: true          # /verify komutu
discordlite.use: true             # Temel komutlar
discordlite.2fa: true             # 2FA ayarları
```

### Admin Permission'ları  
```yaml
discordlite.admin: op             # Tüm admin komutları
discordlite.admin.unlink: op      # Eşleme kaldırma
discordlite.admin.ban: op         # IP ban/unban
discordlite.admin.info: op        # Oyuncu bilgileri
discordlite.admin.logs: op        # Güvenlik logları
discordlite.admin.bypass: op      # Güvenlik bypass
```

### Rol Eşlemeleri
```yaml
role_mappings:
  - permission: "discordlite.admin"
    discord_role_id: "ADMIN_ROLE_ID"
    
  - permission: "group.vip"
    discord_role_id: "VIP_ROLE_ID"
```

## ⚙️ Konfigürasyon

### Ana Konfigürasyon
```yaml
# Discord Bot Ayarları
discord:
  bot_token: "YOUR_BOT_TOKEN"
  guild_id: "YOUR_GUILD_ID"
  
  channels:
    log_channel_id: "LOG_CHANNEL_ID"
    verification_channel_id: "VERIFICATION_CHANNEL_ID"
    security_channel_id: "SECURITY_CHANNEL_ID"
  
# 2FA Ayarları
two_factor_auth:
  enabled: true
  timeout_seconds: 300
  freeze_on_pending: true
  
# Güvenlik Ayarları
security:
  ip_ban_enabled: true
  rate_limiting: true
  threat_detection: true
  
# Verification Ayarları
verification:
  code_length: 6
  timeout_minutes: 5
  max_attempts: 3
```

Tam konfigürasyon rehberi için [CONFIG.md](CONFIG.md) dosyasına bakın.

## 🎯 API Kullanımı

### Event Handling
```kotlin
// Discord hesap bağlama eventi
@EventHandler
fun onDiscordLink(event: DiscordLinkEvent) {
    val player = event.player
    val discordId = event.discordId
    // Custom logic here
}

// Verification başlama eventi
@EventHandler
fun onVerificationStart(event: VerificationStartEvent) {
    val player = event.player
    val verificationCode = event.code
    // Custom logic here
}

// 2FA doğrulama eventi
@EventHandler
fun onTwoFactorAuth(event: TwoFactorAuthEvent) {
    val player = event.player
    val success = event.isSuccess
    // Custom logic here
}
```

### API Methods
```kotlin
// Plugin instance
val discordLite = DiscordLite.instance

// Hesap bağlama kontrolü
val isLinked = discordLite.linkingManager.isPlayerLinked(player.uniqueId)

// Discord ID alma
val discordId = discordLite.linkingManager.getDiscordId(player.uniqueId)

// Verification başlatma
discordLite.linkingManager.startVerification(player)

// 2FA durumu kontrolü
val is2FAEnabled = discordLite.twoFAManager.is2FARequired(player)
```

API dokümantasyonu için [API.md](API.md) dosyasına bakın.

## 🐛 Troubleshooting

### Bot Bağlantı Sorunları
```bash
# Log kontrol
tail -f logs/latest.log | grep DiscordLite

# Config doğrulama
grep -A 5 "discord:" plugins/DiscordLite/config.yml
```

### Verification Kanalı Sorunları
1. Bot'un kanala mesaj gönderme yetkisi var mı?
2. Channel ID doğru mu?
3. Guild ID config'te doğru ayarlanmış mı?

### Permission Sorunları
```bash
# Permission kontrolü
/lp user <player> permission check discordlite.verify
```

### Yaygın Sorunlar

#### Verification Embedi Gözükmüyor
```
Problem: /verify komutu çalışıyor ama Discord'ta embed gözükmüyor
Çözüm: 
1. verification_channel_id doğru ayarlanmış mı?
2. Bot'un kanala mesaj gönderme yetkisi var mı?
3. Bot online mı?
```

#### Modal Açılmıyor
```
Problem: Button'a tıklıyorum ama modal açılmıyor
Çözüm:
1. Bot'un "Send Messages" yetkisi var mı?
2. Console'da hata mesajı var mı?
3. Interaction timeout olmuş olabilir
```

#### Kod Doğrulama Başarısız
```
Problem: Doğru kodu giriyorum ama kabul etmiyor
Çözüm:
1. Kod süresi dolmuş olabilir (5 dakika)
2. Büyük/küçük harf duyarlılığı kontrol edin
3. Boşluk karakteri girmiş olabilirsiniz
```

Daha fazla sorun giderme için [TROUBLESHOOTING.md](TROUBLESHOOTING.md) dosyasına bakın.

## 📊 İstatistikler

Plugin ile ilgili istatistikler:
- **Kod Satırları**: 5000+ satır Kotlin kodu
- **Test Coverage**: %85+
- **Manager Sınıfları**: 12 adet
- **Güvenlik Kontrolleri**: 21 farklı olay türü
- **Dil Desteği**: 2 dil (TR/EN)
- **Veritabanı Desteği**: 3 tür (SQLite/MySQL/YAML)

## 🤝 Katkıda Bulunma

1. Fork yapın
2. Feature branch oluşturun (`git checkout -b feature/amazing-feature`)
3. Değişikliklerinizi commit edin (`git commit -m 'Add amazing feature'`)
4. Branch'i push edin (`git push origin feature/amazing-feature`)
5. Pull Request oluşturun

### Geliştirme Ortamı
```bash
# Projeyi klonlayın
git clone https://github.com/KynuxDev/DiscordLite.git

# Gradle ile build edin
./gradlew build

# Test edin
./gradlew test
```

## 📋 Lisans

Bu proje [MIT Lisansı](LICENSE) altında lisanslanmıştır.

## 🔗 Bağlantılar

- [Discord Sunucusu](https://discord.gg/bgHexr9rk5)
- [SpigotMC Sayfası](https://spigotmc.org/resources/)
- [Bug Reports](https://github.com/KynuxDev/DiscordLite/issues)
- [Feature Requests](https://github.com/KynuxDev/DiscordLite/discussions)

## 📞 Destek

Herhangi bir sorun yaşarsanız:
1. [Issues](https://github.com/KynuxDev/DiscordLite/issues) sayfasında mevcut sorunları kontrol edin
2. Yeni bir issue oluşturun
3. [Discord sunucumuz](https://discord.gg/bgHexr9rk5)a katılın
4. [Sorun giderme kılavuzu](TROUBLESHOOTING.md)na bakın

---

**Made with ❤️ by kynux**

**DiscordLite v0.1.0** ile Minecraft sunucunuzda güvenli ve gelişmiş Discord entegrasyonunun keyfini çıkarın! 🎮✨