# 🚀 DiscordLite Kurulum Kılavuzu

Bu kılavuz DiscordLite plugin'inin adım adım kurulumunu anlatmaktadır.

## 📋 Gereksinimler

### Sunucu Gereksinimleri
- **Minecraft Server**: Spigot 1.19+ veya Paper 1.19+
- **Java**: OpenJDK 17 veya üzeri
- **RAM**: Minimum 1GB (2GB+ önerilir)
- **Disk Alanı**: 50MB+ boş alan

### Discord Gereksinimleri
- Discord Developer hesabı
- Yönetim izinlerine sahip Discord sunucusu
- Bot oluşturma izni

### Veritabanı Gereksinimleri (Opsiyonel)
- **SQLite**: Otomatik (varsayılan)
- **MySQL**: 5.7+ veya MariaDB 10.3+

## 🤖 Discord Bot Kurulumu

### 1. Discord Developer Portal'a Giriş
1. [Discord Developer Portal](https://discord.com/developers/applications)'a gidin
2. Discord hesabınızla giriş yapın
3. "New Application" butonuna tıklayın

### 2. Uygulama Oluşturma
```
1. Application Name: "DiscordLite Bot" (veya istediğiniz isim)
2. Description: Bot'unuzun açıklaması
3. "Create" butonuna tıklayın
```

### 3. Bot Token'ı Alma
```
1. Sol menüden "Bot" sekmesine gidin
2. "Add Bot" butonuna tıklayın
3. "Reset Token" butonuna tıklayın
4. Token'ı kopyalayın ve güvenli bir yerde saklayın
```

⚠️ **Önemli**: Token'ınızı asla paylaşmayın!

### 4. Bot İzinlerini Ayarlama
```
Bot sekmesinde aşağıdaki izinleri aktifleştirin:
✅ Send Messages
✅ Send Messages in Threads
✅ Embed Links
✅ Attach Files
✅ Add Reactions
✅ Use Slash Commands
✅ Manage Messages (opsiyonel)
✅ Read Message History
```

### 5. OAuth2 URL'si Oluşturma
```
1. Sol menüden "OAuth2" → "URL Generator" sekmesine gidin
2. Scopes bölümünde:
   ✅ bot
   ✅ applications.commands
3. Bot Permissions bölümünde:
   ✅ Send Messages
   ✅ Use Slash Commands
   ✅ Embed Links
   ✅ Add Reactions
4. Generated URL'yi kopyalayın
```

### 6. Bot'u Sunucuya Davet Etme
```
1. Oluşturulan URL'yi tarayıcınızda açın
2. Discord sunucunuzu seçin
3. İzinleri onaylayın
4. "Authorize" butonuna tıklayın
```

## 🛠️ Plugin Kurulumu

### 1. Plugin Dosyasını İndirme
```bash
# GitHub Releases'den son sürümü indirin
wget https://github.com/KynuxDev/DiscordLite/releases/latest/download/DiscordLite.jar

# Veya manuel olarak releases sayfasından indirin
```

### 2. Plugin'i Sunucuya Yükleme
```bash
# Plugins klasörüne kopyalayın
cp DiscordLite.jar /path/to/your/server/plugins/

# Dosya izinlerini kontrol edin
chmod 644 /path/to/your/server/plugins/DiscordLite.jar
```

### 3. İlk Başlatma
```bash
# Sunucuyu başlatın
java -Xmx2G -Xms1G -jar server.jar nogui

# Veya mevcut sunucuda
/reload confirm
```

Plugin ilk kez başlatıldığında varsayılan konfigürasyon dosyalarını oluşturacaktır.

## ⚙️ Konfigürasyon

### 1. Ana Konfigürasyon Dosyası
```yaml
# plugins/DiscordLite/config.yml

# Discord Bot Ayarları
discord:
  bot_token: "YOUR_BOT_TOKEN_HERE"  # Bot token'ınızı buraya yazın
  guild_id: "YOUR_GUILD_ID_HERE"   # Discord sunucu ID'nizi buraya yazın
  activity_type: "PLAYING"         # Bot durumu: PLAYING, WATCHING, LISTENING
  activity_text: "Minecraft Server" # Bot durum metni
  
# Veritabanı Ayarları
database:
  type: "sqlite"                   # sqlite, mysql, yaml
  
  # SQLite ayarları (varsayılan)
  sqlite:
    file_name: "discordlite.db"
    
  # MySQL ayarları (opsiyonel)
  mysql:
    host: "localhost"
    port: 3306
    database: "discordlite"
    username: "username"
    password: "password"
    
# 2FA Ayarları
two_factor_auth:
  enabled: true                    # 2FA'yı aktifleştir
  timeout_seconds: 300             # Doğrulama zaman aşımı (saniye)
  freeze_on_pending: true          # Beklemede olan oyuncuları dondur
  required_on_join: true           # Giriş sırasında 2FA iste
  bypass_permission: "discordlite.2fa.bypass"
  
# Güvenlik Ayarları
security:
  ip_ban_enabled: true             # IP ban sistemini aktifleştir
  rate_limiting: true              # Rate limiting aktif
  threat_detection: true           # Tehdit tespiti aktif
  max_login_attempts: 3            # Maksimum giriş denemesi
  lockout_duration: 1800           # Hesap kilitleme süresi (saniye)
  
# Log Ayarları
logging:
  discord_channel_enabled: true    # Discord log kanalı aktif
  log_channel_id: "LOG_CHANNEL_ID" # Log kanalı ID'si
  console_logging: true            # Konsol logları aktif
  file_logging: true               # Dosya logları aktif
  
# İzin Ayarları
permissions:
  sync_with_discord: true          # Discord rolleri ile senkronize et
  auto_assign_roles: true          # Otomatik rol atama
  default_role_id: "ROLE_ID"       # Varsayılan rol ID'si
```

### 2. Discord Sunucu ID'si Alma
```
1. Discord'da Developer Mode'u aktifleştirin:
   User Settings → Advanced → Developer Mode
2. Sunucunuzun ismine sağ tıklayın
3. "Copy ID" seçeneğine tıklayın
4. ID'yi config.yml dosyasına yapıştırın
```

### 3. Discord Log Kanalı Kurulumu
```
1. Discord sunucunuzda yeni bir metin kanalı oluşturun (#discordlite-logs)
2. Kanala sağ tıklayın → "Copy ID"
3. ID'yi config.yml dosyasındaki log_channel_id kısmına yapıştırın
4. Bot'un kanala mesaj gönderme izninin olduğundan emin olun
```

## 🗄️ Veritabanı Kurulumu

### SQLite (Varsayılan)
```yaml
# Hiç bir şey yapmanıza gerek yok!
# SQLite otomatik olarak kurulur ve çalışır
database:
  type: "sqlite"
  sqlite:
    file_name: "discordlite.db"
```

### MySQL Kurulumu
```sql
-- 1. Veritabanı oluşturun
CREATE DATABASE discordlite CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. Kullanıcı oluşturun
CREATE USER 'discordlite'@'localhost' IDENTIFIED BY 'strong_password_here';

-- 3. İzinleri verin
GRANT ALL PRIVILEGES ON discordlite.* TO 'discordlite'@'localhost';
FLUSH PRIVILEGES;
```

```yaml
# config.yml dosyasını güncelleyin
database:
  type: "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "discordlite"
    username: "discordlite"
    password: "strong_password_here"
    ssl: false
    connection_timeout: 30000
    pool_size: 10
```

### YAML Dosya Sistemi
```yaml
# Sadece test için önerilir
database:
  type: "yaml"
  yaml:
    data_folder: "data"
```

## 🔧 İleri Düzey Yapılandırma

### Güvenlik Ayarları
```yaml
security:
  # IP Whitelist
  ip_whitelist:
    enabled: false
    allowed_ips:
      - "127.0.0.1"
      - "192.168.1.0/24"
      
  # Rate Limiting
  rate_limiting:
    commands_per_minute: 5
    links_per_hour: 3
    login_attempts_per_hour: 10
    
  # Threat Detection
  threat_detection:
    sql_injection: true
    xss_detection: true
    command_injection: true
    suspicious_activity: true
```

### Discord Rol Eşleştirme
```yaml
permissions:
  role_mappings:
    "DISCORD_ROLE_ID_1": "minecraft.permission.group.vip"
    "DISCORD_ROLE_ID_2": "minecraft.permission.group.admin"
    "DISCORD_ROLE_ID_3": "minecraft.permission.group.moderator"
    
  # Otomatik rol atama
  auto_roles:
    on_link: "LINKED_ROLE_ID"
    on_verify: "VERIFIED_ROLE_ID"
```

### Mesaj Özelleştirme
```yaml
# Türkçe mesajları özelleştirmek için:
# plugins/DiscordLite/messages/tr.yml dosyasını düzenleyin

messages:
  link:
    success: "&a✅ Hesabınız başarıyla Discord ile bağlandı!"
    already_linked: "&c❌ Hesabınız zaten Discord ile bağlı!"
    timeout: "&c⏱️ Doğrulama süresi doldu, tekrar deneyin!"
```

## 🧪 Test ve Doğrulama

### 1. Bot Bağlantısını Test Etme
```bash
# Sunucu konsolunda
/discordlite reload

# Başarılı mesajlar:
[INFO] Discord bot successfully connected!
[INFO] Registered slash commands: 1
[INFO] ValidationManager başarıyla başlatıldı!
```

### 2. Slash Komutlarını Test Etme
```
Discord'da:
/link

Sonuç: Bot size DM göndermeli
```

### 3. Oyuncu Bağlamayı Test Etme
```bash
# Minecraft'ta
/discordlite link

# Console'da başarı mesajları:
[INFO] Player TestUser initiated link process
[INFO] Discord DM sent to user: 123456789012345678
```

### 4. 2FA'yı Test Etme
```bash
# Oyuncu bağlandıktan sonra sunucudan çıkıp tekrar girin
# 2FA DM'i gelmelidir
```

## 🚨 Sorun Giderme

### Bot Bağlanamıyor
```bash
# Hata: "Invalid token"
Çözüm: Bot token'ını kontrol edin
1. Discord Developer Portal'da token'ı yenileyin
2. config.yml dosyasında token'ı güncelleyin
3. /discordlite reload komutunu çalıştırın
```

```bash
# Hata: "Missing permission"
Çözüm: Bot izinlerini kontrol edin
1. Bot'un sunucuda gerekli izinleri olduğundan emin olun
2. OAuth2 URL'sini yeniden oluşturun
3. Bot'u tekrar davet edin
```

### Slash Komutları Çalışmıyor
```bash
# Hata: "Unknown interaction"
Çözüm: 
1. Guild ID'nin doğru olduğundan emin olun
2. Bot'un applications.commands izni olduğundan emin olun
3. /discordlite reload komutunu çalıştırın
4. Discord'da 1-2 dakika bekleyin
```

### Veritabanı Bağlantı Sorunları
```bash
# SQLite sorunları:
1. Dosya izinlerini kontrol edin
2. Disk alanını kontrol edin
3. plugins/DiscordLite/ klasörünün var olduğundan emin olun

# MySQL sorunları:
1. Bağlantı bilgilerini kontrol edin
2. Kullanıcı izinlerini kontrol edin
3. MySQL servisinin çalıştığından emin olun
```

### 2FA DM'leri Gelmiyor
```bash
# Sorun: DM'ler ulaşmıyor
Çözüm:
1. Oyuncunun DM izinlerinin açık olduğundan emin olun
2. Bot ile oyuncunun ortak sunucuda olduğundan emin olun
3. Discord privacy ayarlarını kontrol ettirin
4. Bot'un Send Messages izninin olduğundan emin olun
```

## 📈 Performans Optimizasyonu

### Veritabanı Optimizasyonu
```yaml
database:
  mysql:
    pool_size: 20                    # Eşzamanlı bağlantı sayısı
    connection_timeout: 30000        # Bağlantı zaman aşımı
    idle_timeout: 600000             # Boş bağlantı zaman aşımı
    max_lifetime: 1800000            # Maksimum bağlantı ömrü
```

### Bellek Optimizasyonu
```yaml
# Cache ayarları
caching:
  player_cache_size: 1000           # Oyuncu cache boyutu
  discord_cache_size: 500           # Discord cache boyutu
  cache_expire_time: 3600           # Cache süresi (saniye)
```

### Log Optimizasyonu
```yaml
logging:
  file_logging: true
  max_file_size: "10MB"             # Maksimum log dosyası boyutu
  backup_count: 5                   # Saklanan log dosyası sayısı
  compression: true                 # Log sıkıştırma
```

## 🔄 Güncelleme

### Plugin Güncelleme
```bash
# 1. Mevcut plugin'i yedekleyin
cp plugins/DiscordLite.jar plugins/DiscordLite.jar.backup

# 2. Yeni sürümü indirin
wget https://github.com/KynuxDev/DiscordLite/releases/latest/download/DiscordLite.jar

# 3. Eski dosyayı silin ve yenisini kopyalayın
rm plugins/DiscordLite.jar
cp DiscordLite.jar plugins/

# 4. Sunucuyu yeniden başlatın veya reload edin
/reload confirm
```

### Konfigürasyon Yedekleme
```bash
# Otomatik yedekleme scripti
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
tar -czf "discordlite_backup_$DATE.tar.gz" plugins/DiscordLite/
echo "Backup created: discordlite_backup_$DATE.tar.gz"
```

## ✅ Kurulum Kontrol Listesi

- [ ] Java 17+ kurulu
- [ ] Spigot/Paper 1.19+ server
- [ ] Discord Developer hesabı oluşturuldu
- [ ] Discord botu oluşturuldu
- [ ] Bot token'ı alındı
- [ ] Bot sunucuya davet edildi
- [ ] Bot gerekli izinlere sahip
- [ ] Plugin dosyası indirildi
- [ ] Plugin sunucuya yüklendi
- [ ] config.yml dosyası düzenlendi
- [ ] Discord guild_id eklendi
- [ ] Bot token eklendi
- [ ] Log kanalı ID'si eklendi
- [ ] Veritabanı yapılandırıldı
- [ ] Plugin başarıyla yüklenди
- [ ] Bot çevrimiçi görünüyor
- [ ] Slash komutları çalışıyor
- [ ] Test bağlaması yapıldı
- [ ] 2FA test edildi
- [ ] Log sistemi çalışıyor

## 🎉 Kurulum Tamamlandı!

Tebrikler! DiscordLite başarıyla kuruldu. Artık:

1. Oyuncular `/discordlite link` komutu ile hesaplarını bağlayabilir
2. Discord üzerinden `/link` slash komutu kullanabilir
3. 2FA doğrulama sistemi aktif
4. Güvenlik logları Discord kanalında görüntülenir
5. Admin komutları ile sistem yönetilebilir

Daha fazla bilgi için [README.md](../README.md) ve [CONFIG.md](CONFIG.md) dosyalarına bakın.

## 📞 Destek

Kurulum sırasında sorun yaşarsanız:
- [GitHub Issues](https://github.com/KynuxDev/DiscordLite/issues)
- [Discord Sunucusu](https://discord.gg/bgHexr9rk5)
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md)