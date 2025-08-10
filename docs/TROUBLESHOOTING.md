# 🚨 DiscordLite Sorun Giderme Kılavuzu

Bu kılavuz DiscordLite plugin'i ile yaşayabileceğiniz yaygın sorunları ve çözümlerini içermektedir.

## 📋 Hızlı Tanı

### Sistem Durumu Kontrol Listesi
```bash
# 1. Plugin yüklenme durumu
/plugins | grep DiscordLite

# 2. Konfigürasyon kontrol
/discordlite info

# 3. Discord bot durumu
/discordlite status

# 4. Veritabanı bağlantısı
/discordlite db status

# 5. Log kontrol
tail -f plugins/DiscordLite/logs/discordlite.log
```

## 🤖 Discord Bot Sorunları

### Bot Çevrimdışı Görünüyor

**Belirti:**
- Bot Discord'da offline
- "Bot failed to connect" konsol hatası
- Slash komutları çalışmıyor

**Çözümler:**

1. **Token Kontrolü**
```yaml
# config.yml kontrol et
discord:
  bot_token: "YOUR_BOT_TOKEN_HERE"  # Doğru token olduğundan emin ol
```

```bash
# Token'ı test et
curl -H "Authorization: Bot YOUR_BOT_TOKEN" https://discord.com/api/users/@me
```

2. **Bot İzinlerini Kontrol Et**
```
Discord Developer Portal'da bot'un şu izinleri olduğundan emin ol:
✅ Send Messages
✅ Use Slash Commands
✅ Embed Links
✅ Add Reactions
✅ Read Message History
```

3. **Rate Limiting Kontrolü**
```
[ERROR] Discord rate limit exceeded
Çözüm: config.yml'de rate limiting ayarlarını düşür:

discord:
  rate_limiting:
    requests_per_second: 30  # 50 yerine 30
```

4. **Sunucu Üyeliği Kontrolü**
```bash
# Bot'un sunucuda olup olmadığını kontrol et
# Discord'da bot listesine bak veya:
/discordlite guild members
```

### Slash Komutları Çalışmıyor

**Belirti:**
- `/link` komutu Discord'da görünmüyor
- "Unknown interaction" hatası
- Komutlar kayıtlı değil

**Çözümler:**

1. **Guild ID Kontrolü**
```yaml
# config.yml'de guild_id doğru mu?
discord:
  guild_id: "YOUR_GUILD_ID_HERE"
```

```bash
# Guild ID'yi alma:
# Discord'da sunucuya sağ tık → Copy ID (Developer Mode açık olmalı)
```

2. **Komut Senkronizasyonu**
```bash
# Plugin'i yeniden yükle
/discordlite reload

# Veya sunucuyu yeniden başlat
```

3. **İzin Kontrolü**
```
Bot'un applications.commands iznine sahip olduğundan emin ol:
1. Discord Developer Portal → OAuth2 → URL Generator
2. Scopes: bot + applications.commands
3. Yeni URL ile bot'u tekrar davet et
```

4. **Global vs Guild Commands**
```yaml
# config.yml'de:
discord:
  slash_commands:
    global: false  # Guild-specific komutlar için
    sync_on_startup: true
```

### DM Gönderilemiyor

**Belirti:**
- "Failed to send DM" hatası
- 2FA kodları ulaşmıyor
- Link doğrulama mesajları gelmiyor

**Çözümler:**

1. **DM İzinlerini Kontrol Et**
```
Oyuncunun Discord ayarlarında:
User Settings → Privacy & Safety → Allow direct messages from server members: ON
```

2. **Ortak Sunucu Kontrolü**
```bash
# Bot ve oyuncunun ortak sunucuda olup olmadığını kontrol et
/discordlite check-member <discord_id>
```

3. **Bot İzinlerini Kontrol Et**
```
Bot'un şu izinlere sahip olduğundan emin ol:
✅ Send Messages
✅ Embed Links
✅ Add Reactions
```

4. **Blocked Users Kontrolü**
```
Oyuncunun bot'u engellemiş olabileceğini kontrol et:
Discord → User Settings → Privacy & Safety → Blocked Users
```

## 🔗 Hesap Bağlama Sorunları

### Bağlama İşlemi Başarısız

**Belirti:**
- "Link failed" mesajı
- Discord'da doğrulama mesajı gelmiyor
- "Player already linked" hatası (değilken)

**Çözümler:**

1. **Mevcut Bağlantıları Temizle**
```bash
# Admin olarak:
/discordlite unlink <oyuncu>
/discordlite reset <oyuncu>
```

2. **Veritabanı Kontrolü**
```sql
-- SQLite için:
SELECT * FROM player_links WHERE player_uuid = 'UUID_HERE';

-- MySQL için:
SELECT * FROM discordlite_player_links WHERE player_uuid = 'UUID_HERE';
```

3. **Cache Temizleme**
```bash
/discordlite cache clear
# veya plugin'i yeniden yükle
/discordlite reload
```

4. **Log Kontrolü**
```bash
# Link işlemi sırasında hataları kontrol et
tail -f logs/latest.log | grep "DiscordLite"
```

### Doğrulama Zaman Aşımı

**Belirti:**
- "Verification timeout" mesajı
- 5 dakika içinde tamamlanamıyor
- Butonlar çalışmıyor

**Çözümler:**

1. **Timeout Süresini Artır**
```yaml
# config.yml'de:
two_factor_auth:
  timeout_seconds: 600  # 300 yerine 600 (10 dakika)
```

2. **Button İnteraksiyon Kontrolü**
```
Discord'da:
1. DM mesajını yenile (F5)
2. Butonlara tıkla (uzun basmayın)
3. Tarayıcı cache'ini temizle
```

3. **Pending İstekleri Temizle**
```bash
/discordlite pending clear
/discordlite pending list  # Bekleyen istekleri listele
```

## 🔒 2FA Sorunları

### 2FA Kodları Gelmiyor

**Belirti:**
- Giriş yaparken 2FA isteniyor ama DM gelmiyor
- "2FA verification required" mesajı
- Oyuncu donuyor

**Çözümler:**

1. **2FA Durumunu Kontrol Et**
```bash
/discordlite 2fa status <oyuncu>
/discordlite 2fa reset <oyuncu>  # Reset 2FA for player
```

2. **DM İzinlerini Kontrol Et**
```
Oyuncunun DM'lere açık olduğundan emin ol (yukarıdaki DM bölümüne bakın)
```

3. **Bağlantı Durumunu Kontrol Et**
```bash
/discordlite info <oyuncu>  # Hesap bağlı mı kontrol et
```

4. **Bypass İzni Ver (Geçici)**
```bash
/lp user <oyuncu> permission set discordlite.2fa.bypass true
```

### 2FA Sonsuz Döngü

**Belirti:**
- Sürekli 2FA doğrulama isteniyor
- Doğrulama yapılıyor ama kabul edilmiyor
- Oyuncu sunucuya giremiyor

**Çözümler:**

1. **2FA Cache Temizle**
```bash
/discordlite 2fa clear-cache
/discordlite cache clear
```

2. **Pending 2FA İstekleri Temizle**
```bash
/discordlite 2fa pending clear <oyuncu>
```

3. **Veritabanında 2FA Durumunu Sıfırla**
```sql
-- SQLite/MySQL için:
UPDATE player_data SET two_fa_pending = 0, two_fa_attempts = 0 WHERE player_uuid = 'UUID_HERE';
```

4. **Config'de 2FA'yı Geçici Kapat**
```yaml
# config.yml'de geçici olarak:
two_factor_auth:
  enabled: false
```

## 🗄️ Veritabanı Sorunları

### SQLite Bağlantı Hatası

**Belirti:**
- "Database connection failed"
- "SQLite file locked"
- "Permission denied" hatası

**Çözümler:**

1. **Dosya İzinlerini Kontrol Et**
```bash
ls -la plugins/DiscordLite/
chmod 755 plugins/DiscordLite/
chmod 644 plugins/DiscordLite/discordlite.db
```

2. **Disk Alanını Kontrol Et**
```bash
df -h  # Disk alanı kontrolü
du -sh plugins/DiscordLite/  # Plugin disk kullanımı
```

3. **SQLite Dosyasını Onar**
```bash
# Backup al
cp plugins/DiscordLite/discordlite.db backup/

# SQLite integrity check
sqlite3 plugins/DiscordLite/discordlite.db "PRAGMA integrity_check;"
```

4. **Yeni Veritabanı Oluştur**
```bash
# Eski dosyayı yedekle
mv plugins/DiscordLite/discordlite.db plugins/DiscordLite/discordlite.db.backup

# Plugin'i yeniden başlat (yeni db oluşturulacak)
/discordlite reload
```

### MySQL Bağlantı Sorunları

**Belirti:**
- "Connection refused"
- "Access denied"
- "Unknown database"

**Çözümler:**

1. **MySQL Servis Durumu**
```bash
# MySQL durumunu kontrol et
systemctl status mysql
# veya
service mysql status
```

2. **Bağlantı Parametrelerini Test Et**
```bash
mysql -h localhost -u discordlite -p discordlite
```

3. **Config Kontrol**
```yaml
# config.yml'de:
database:
  mysql:
    host: "localhost"     # Doğru host
    port: 3306           # Doğru port
    database: "discordlite"  # Var olan database
    username: "discordlite"  # Doğru kullanıcı adı
    password: "password"     # Doğru şifre
```

4. **MySQL Kullanıcı İzinleri**
```sql
-- MySQL'de izinleri kontrol et:
SHOW GRANTS FOR 'discordlite'@'localhost';

-- Eksik izinler varsa:
GRANT ALL PRIVILEGES ON discordlite.* TO 'discordlite'@'localhost';
FLUSH PRIVILEGES;
```

### Migrasyon Hataları

**Belirti:**
- "Migration failed"
- "Table already exists"
- "Schema version mismatch"

**Çözümler:**

1. **Migration Durumunu Kontrol Et**
```bash
/discordlite db migrate status
```

2. **Manuel Migration Çalıştır**
```bash
/discordlite db migrate force
```

3. **Backup ve Reset**
```bash
# Veritabanını yedekle
/discordlite db backup

# Schema'yı sıfırla
/discordlite db reset --confirm
```

## 🛡️ Güvenlik Sorunları

### IP Ban Sorunu

**Belirti:**
- Oyuncular yanlış IP ban'ı yiyor
- "IP address banned" mesajı
- Yasal oyuncular bağlanamıyor

**Çözümler:**

1. **IP Ban Listesini Kontrol Et**
```bash
/discordlite ipban list
/discordlite ipban info <ip_address>
```

2. **Yanlış Ban'ları Kaldır**
```bash
/discordlite unban <ip_address>
```

3. **IP Whitelist Ekle**
```yaml
# config.yml'de:
security:
  ip_whitelist:
    enabled: true
    allowed_ips:
      - "127.0.0.1"
      - "192.168.1.0/24"  # Yerel ağ
```

4. **Otomatik IP Ban'ı Kapat**
```yaml
# config.yml'de geçici olarak:
security:
  ip_ban_enabled: false
```

### Rate Limiting Sorunu

**Belirti:**
- "Rate limit exceeded"
- Oyuncular komut kullanamıyor
- Çok fazla "slow down" mesajı

**Çözümler:**

1. **Rate Limit Ayarlarını Gevşet**
```yaml
# config.yml'de:
security:
  rate_limits:
    commands_per_minute: 10  # 5 yerine 10
    links_per_hour: 5        # 3 yerine 5
```

2. **Rate Limit Cache Temizle**
```bash
/discordlite ratelimit clear
/discordlite cache clear
```

3. **Bypass İzni Ver**
```bash
/lp user <oyuncu> permission set discordlite.ratelimit.bypass true
```

### Yanlış Güvenlik Uyarıları

**Belirti:**
- Çok fazla "suspicious activity" log'u
- Normal oyuncular "high risk" olarak işaretleniyor
- Otomatik güvenlik önlemleri yanlış tetikleniyor

**Çözümler:**

1. **Güvenlik Eşiklerini Ayarla**
```yaml
# config.yml'de:
security:
  threat_detection:
    risk_thresholds:
      low: 40     # 25 yerine 40
      medium: 65  # 50 yerine 65
      high: 85    # 75 yerine 85
```

2. **Güvenlik Loglarını Filtrele**
```yaml
# config.yml'de:
logging:
  categories:
    security: false  # Geçici olarak kapat
```

3. **Oyuncu Risk Skorunu Sıfırla**
```bash
/discordlite security reset <oyuncu>
```

## 📊 Performans Sorunları

### Yavaş Plugin

**Belirti:**
- Sunucu lag'i
- Komutlar yavaş çalışıyor
- Bellek kullanımı yüksek

**Çözümler:**

1. **Performance İstatistiklerini Kontrol Et**
```bash
/discordlite stats performance
/discordlite stats memory
```

2. **Cache Boyutlarını Artır**
```yaml
# config.yml'de:
caching:
  player_cache:
    size: 2000      # 1000 yerine 2000
    expire_time: 7200  # 3600 yerine 7200
```

3. **Thread Pool Ayarla**
```yaml
# config.yml'de:
performance:
  thread_pool:
    core_size: 8    # 4 yerine 8
    max_size: 24    # 16 yerine 24
```

4. **Gereksiz Log'ları Kapat**
```yaml
# config.yml'de:
logging:
  log_levels:
    console: "INFO"  # DEBUG yerine INFO
    file: "WARN"     # DEBUG yerine WARN
```

### Bellek Sızıntısı

**Belirti:**
- Bellek kullanımı sürekli artıyor
- OutOfMemoryError
- Sunucu donuyor

**Çözümler:**

1. **Cache Temizliği**
```bash
/discordlite cache clear
/discordlite gc  # Garbage collection tetikle
```

2. **Cache Ayarlarını Optimize Et**
```yaml
# config.yml'de:
caching:
  cleanup:
    interval: 300     # 600 yerine 300 (daha sık temizlik)
    threshold: 0.7    # 0.8 yerine 0.7
```

3. **Bellek İzleme Aktifleştir**
```yaml
# config.yml'de:
performance:
  monitoring:
    enabled: true
    memory_threshold: 75  # 80 yerine 75
```

## 🔧 Genel Sorun Giderme

### Plugin Yüklenmiyor

**Belirti:**
- Plugin listesinde görünmüyor
- "Failed to load plugin" hatası
- Dependency hataları

**Çözümler:**

1. **Java Versiyonu Kontrol**
```bash
java -version  # Java 17+ olmalı
```

2. **Plugin Bağımlılıklarını Kontrol Et**
```
Gerekli plugin'ler var mı:
- Spigot/Paper 1.19+
- (Opsiyonel) Vault, LuckPerms, PlaceholderAPI
```

3. **JAR Dosyası Kontrolü**
```bash
ls -la plugins/DiscordLite.jar
# Dosya boyutu 0 ise tekrar indir
```

4. **Console Loglarını Kontrol Et**
```bash
grep "DiscordLite" logs/latest.log
tail -f logs/latest.log  # Canlı log takibi
```

### Komutlar Çalışmıyor

**Belirti:**
- "Unknown command" hatası
- Permission denied mesajları
- Komut tab completion çalışmıyor

**Çözümler:**

1. **Plugin Aktif Mi Kontrol Et**
```bash
/plugins | grep DiscordLite
```

2. **İzinleri Kontrol Et**
```bash
/lp user <oyuncu> permission info discordlite
```

3. **Command Registration Kontrol**
```bash
# Console'da:
/discordlite reload
# "Commands registered" mesajını bekle
```

### Konfigürasyon Yüklenmiyor

**Belirti:**
- "Config load failed"
- Varsayılan değerler kullanılıyor
- Değişiklikler uygulanmıyor

**Çözümler:**

1. **YAML Syntax Kontrol**
```bash
# YAML syntax checker kullan:
python -c "import yaml; yaml.safe_load(open('plugins/DiscordLite/config.yml'))"
```

2. **File Encoding Kontrol**
```bash
file plugins/DiscordLite/config.yml
# UTF-8 encoding olmalı
```

3. **Backup'dan Restore Et**
```bash
cp plugins/DiscordLite/config.yml.backup plugins/DiscordLite/config.yml
/discordlite reload
```

## 🆘 Acil Durum Prosedürleri

### Plugin Tamamen Çöktü

```bash
# 1. Plugin'i güvenle devre dışı bırak
/plugins disable DiscordLite

# 2. Veritabanını yedekle
cp -r plugins/DiscordLite/data/ backup/

# 3. Log'ları kaydet
cp logs/latest.log backup/crash-$(date +%Y%m%d-%H%M%S).log

# 4. Plugin'i temiz kurulum için hazırla
rm plugins/DiscordLite.jar
wget https://github.com/KynuxDev/DiscordLite/releases/latest/download/DiscordLite.jar -O plugins/DiscordLite.jar

# 5. Sunucuyu yeniden başlat
/restart
```

### Veritabanı Bozuldu

```bash
# 1. Sunucuyu güvenli modda başlat
# config.yml'de tüm özellikleri kapat

# 2. Veritabanını yedekle
cp plugins/DiscordLite/discordlite.db backup/

# 3. Veritabanını onar veya yeniden oluştur
rm plugins/DiscordLite/discordlite.db
/discordlite reload

# 4. Backup'dan veri geri yükle (opsiyonel)
```

### Discord Bot Hack'lendi

```bash
# 1. Bot token'ını hemen değiştir
# Discord Developer Portal'da "Reset Token"

# 2. Yeni token'ı config'e ekle
# config.yml'de bot_token güncelle

# 3. Eski token'ı iptal et
# Developer Portal'da bot'u deactivate et

# 4. Bot izinlerini gözden geçir
# Gereksiz izinleri kaldır

# 5. Güvenlik loglarını kontrol et
/discordlite logs security --hours 24
```

## 📞 Destek Alma

### Bug Report Oluşturma

```
1. Sorunu reproduke et
2. Console log'larını topla
3. Config dosyalarını hazırla
4. GitHub Issues'da detaylı açıklama yap
5. Sistem bilgilerini ekle:
   - Server versiyonu
   - Java versiyonu
   - Plugin versiyonu
   - Diğer plugin'ler
```

### Community Kaynaklardan Yardım

1. **Discord Sunucusu**: Anlık destek
2. **GitHub Discussions**: Genel sorular
3. **GitHub Issues**: Bug raporları
4. **Wiki**: Detaylı dokümantasyon

### Log Toplama

```bash
# Tam log paketi oluştur
tar -czf discordlite-logs-$(date +%Y%m%d).tar.gz \
    logs/latest.log \
    plugins/DiscordLite/logs/ \
    plugins/DiscordLite/config.yml \
    plugins/DiscordLite/messages/
```

Bu sorun giderme kılavuzu en yaygın sorunları kapsar. Eğer sorununuz bu listede yoksa, lütfen community kaynaklarımızdan destek isteyin.