# ⚙️ DiscordLite Konfigürasyon Kılavuzu

Bu kılavuz DiscordLite plugin'inin tüm konfigürasyon seçeneklerini detaylandırmaktadır.

## 📁 Konfigürasyon Dosyaları

```
plugins/DiscordLite/
├── config.yml              # Ana konfigürasyon
├── messages/
│   ├── tr.yml              # Türkçe mesajlar
│   └── en.yml              # İngilizce mesajlar
└── data/                   # Veri dosyaları
    ├── discordlite.db      # SQLite veritabanı
    └── players.yml         # YAML veri dosyası (opsiyonel)
```

## 🔧 Ana Konfigürasyon (config.yml)

### Discord Bot Ayarları
```yaml
discord:
  # Bot token'ınız (zorunlu)
  bot_token: "YOUR_BOT_TOKEN_HERE"
  
  # Discord sunucu ID'niz (zorunlu)
  guild_id: "YOUR_GUILD_ID_HERE"
  
  # Bot durum ayarları
  activity_type: "PLAYING"           # PLAYING, WATCHING, LISTENING, STREAMING
  activity_text: "Minecraft Server"  # Bot durumunda görünecek metin
  
  # Slash komut ayarları
  slash_commands:
    enabled: true                     # Slash komutları aktif
    global: false                     # Global (tüm sunucular) veya guild-specific
    sync_on_startup: true             # Başlangıçta komutları senkronize et
  
  # Embed mesaj ayarları
  embeds:
    color: 0x00FF00                   # Embed rengi (hex kod)
    footer_text: "DiscordLite Bot"    # Footer metni
    footer_icon_url: ""               # Footer ikonu URL'si
    thumbnail_url: ""                 # Thumbnail URL'si
  
  # Rate limiting ayarları
  rate_limiting:
    enabled: true                     # Discord rate limiting aktif
    requests_per_second: 50           # Saniyede maksimum istek
    burst_size: 10                    # Burst boyutu
```

### Veritabanı Ayarları
```yaml
database:
  # Veritabanı tipi: sqlite, mysql, yaml
  type: "sqlite"
  
  # SQLite ayarları (varsayılan)
  sqlite:
    file_name: "discordlite.db"      # Veritabanı dosya adı
    journal_mode: "WAL"              # WAL, DELETE, TRUNCATE, MEMORY
    synchronous: "NORMAL"            # FULL, NORMAL, OFF
    foreign_keys: true               # Foreign key constraints
    auto_vacuum: "INCREMENTAL"       # NONE, FULL, INCREMENTAL
    
  # MySQL ayarları
  mysql:
    host: "localhost"                # Veritabanı sunucusu
    port: 3306                       # Port
    database: "discordlite"          # Veritabanı adı
    username: "username"             # Kullanıcı adı
    password: "password"             # Şifre
    ssl: false                       # SSL bağlantısı
    ssl_mode: "DISABLED"             # DISABLED, PREFERRED, REQUIRED
    
    # Bağlantı havuzu ayarları
    connection_timeout: 30000        # Bağlantı zaman aşımı (ms)
    socket_timeout: 60000            # Socket zaman aşımı (ms)
    idle_timeout: 600000             # Boş bağlantı zaman aşımı (ms)
    max_lifetime: 1800000            # Maksimum bağlantı ömrü (ms)
    pool_size: 10                    # Maksimum bağlantı sayısı
    minimum_idle: 2                  # Minimum boş bağlantı
    
    # Charset ayarları
    charset: "utf8mb4"               # Karakter seti
    collation: "utf8mb4_unicode_ci"  # Collation
    
  # YAML dosya sistemi ayarları
  yaml:
    data_folder: "data"              # Veri klasörü
    auto_save_interval: 300          # Otomatik kaydetme aralığı (saniye)
    backup_count: 5                  # Yedek dosya sayısı
    
  # Migrasyon ayarları
  migrations:
    auto_migrate: true               # Otomatik migrasyon
    backup_before_migrate: true      # Migrasyon öncesi yedekleme
    
  # Performans ayarları
  performance:
    batch_size: 100                  # Batch işlem boyutu
    query_timeout: 30                # Sorgu zaman aşımı (saniye)
    retry_attempts: 3                # Yeniden deneme sayısı
    retry_delay: 1000                # Yeniden deneme gecikmesi (ms)
```

### 2FA (İki Faktörlü Kimlik Doğrulama) Ayarları
```yaml
two_factor_auth:
  # 2FA sistemi aktif/pasif
  enabled: true
  
  # Doğrulama zaman aşımı (saniye)
  timeout_seconds: 300
  
  # Doğrulama beklerken oyuncuyu dondur
  freeze_on_pending: true
  
  # Giriş sırasında 2FA zorunlu
  required_on_join: true
  
  # 2FA'yı atlama izni
  bypass_permission: "discordlite.2fa.bypass"
  
  # Çoklu giriş denemesi ayarları
  max_attempts: 3                    # Maksimum deneme sayısı
  lockout_duration: 1800             # Kilitleme süresi (saniye)
  
  # DM mesaj ayarları
  dm_settings:
    embed_color: 0x00FF00            # Embed rengi
    timeout_warning: 60              # Zaman aşımı uyarısı (saniye kala)
    auto_delete_after: 600           # Mesajı otomatik sil (saniye)
    
  # Button ayarları
  buttons:
    confirm_emoji: "✅"              # Onaylama emoji'si
    cancel_emoji: "❌"               # İptal emoji'si
    confirm_style: "SUCCESS"         # SUCCESS, DANGER, PRIMARY, SECONDARY
    cancel_style: "DANGER"
    
  # Güvenlik ayarları
  security:
    require_linked_account: true     # Bağlı hesap zorunlu
    check_user_permissions: true     # Kullanıcı izinlerini kontrol et
    log_all_attempts: true           # Tüm denemeleri logla
```

### Güvenlik Ayarları
```yaml
security:
  # IP ban sistemi
  ip_ban_enabled: true
  
  # Rate limiting
  rate_limiting: true
  
  # Tehdit tespiti
  threat_detection: true
  
  # Maksimum giriş denemesi
  max_login_attempts: 3
  
  # Hesap kilitleme süresi (saniye)
  lockout_duration: 1800
  
  # IP whitelist ayarları
  ip_whitelist:
    enabled: false                   # IP whitelist aktif
    allowed_ips:
      - "127.0.0.1"                 # Yerel bağlantılar
      - "192.168.1.0/24"            # Yerel ağ
      - "10.0.0.0/8"                # Private IP aralığı
    bypass_permission: "discordlite.security.bypass"
    
  # Rate limiting detayları
  rate_limits:
    commands_per_minute: 5           # Dakikada komut sayısı
    links_per_hour: 3                # Saatte bağlama denemesi
    login_attempts_per_hour: 10      # Saatte giriş denemesi
    dm_requests_per_hour: 20         # Saatte DM isteği
    
  # Tehdit tespiti ayarları
  threat_detection:
    sql_injection: true              # SQL injection tespiti
    xss_detection: true              # XSS attack tespiti
    command_injection: true          # Command injection tespiti
    directory_traversal: true        # Directory traversal tespiti
    suspicious_activity: true        # Şüpheli aktivite tespiti
    
    # Risk seviyesi eşikleri
    risk_thresholds:
      low: 25                        # Düşük risk eşiği
      medium: 50                     # Orta risk eşiği
      high: 75                       # Yüksek risk eşiği
      critical: 90                   # Kritik risk eşiği
      
  # Otomatik güvenlik önlemleri
  auto_security:
    enabled: true                    # Otomatik güvenlik aktif
    ban_on_critical: true            # Kritik tehditte otomatik ban
    notify_admins: true              # Adminleri bilgilendir
    emergency_lockdown: false        # Acil durum kilidi
    
  # Şifreleme ayarları
  encryption:
    algorithm: "AES-256-GCM"         # Şifreleme algoritması
    key_length: 256                  # Anahtar uzunluğu
    salt_length: 32                  # Salt uzunluğu
```

### İzin Sistemi Ayarları
```yaml
permissions:
  # Discord rolleri ile senkronizasyon
  sync_with_discord: true
  
  # Otomatik rol atama
  auto_assign_roles: true
  
  # Varsayılan rol ID'si
  default_role_id: "ROLE_ID"
  
  # Rol eşleştirmesi
  role_mappings:
    "123456789012345678": "vip"      # Discord Rol ID: Minecraft grup
    "234567890123456789": "moderator"
    "345678901234567890": "admin"
    "456789012345678901": "owner"
    
  # Otomatik rol atama
  auto_roles:
    on_link: "123456789012345678"    # Bağlama sonrası rol
    on_verify: "234567890123456789"  # Doğrulama sonrası rol
    on_join: "345678901234567890"    # Sunucuya katılma sonrası rol
    
  # İzin grupları
  permission_groups:
    default:                         # Varsayılan izinler
      - "discordlite.link"
      - "discordlite.unlink"
    vip:                            # VIP izinleri
      - "discordlite.link"
      - "discordlite.unlink"
      - "discordlite.info"
    moderator:                      # Moderatör izinleri
      - "discordlite.*"
      - "discordlite.admin.info"
    admin:                          # Admin izinleri
      - "discordlite.*"
      - "discordlite.admin.*"
      
  # Senkronizasyon ayarları
  sync_settings:
    sync_interval: 300               # Senkronizasyon aralığı (saniye)
    remove_roles_on_unlink: true     # Bağlantı koparıldığında rolleri kaldır
    update_permissions_realtime: true # Gerçek zamanlı izin güncelleme
```

### Log Sistemi Ayarları
```yaml
logging:
  # Discord log kanalı
  discord_channel_enabled: true
  log_channel_id: "LOG_CHANNEL_ID"
  
  # Konsol logları
  console_logging: true
  
  # Dosya logları
  file_logging: true
  
  # Log seviyeleri
  log_levels:
    console: "INFO"                  # TRACE, DEBUG, INFO, WARN, ERROR
    file: "DEBUG"
    discord: "INFO"
    
  # Dosya log ayarları
  file_settings:
    file_name: "discordlite.log"
    max_file_size: "10MB"           # Maksimum dosya boyutu
    backup_count: 5                 # Yedek dosya sayısı
    compression: true               # Log sıkıştırma
    
  # Discord log ayarları
  discord_settings:
    embed_colors:
      INFO: 0x00FF00                # Yeşil
      WARN: 0xFF8000                # Turuncu
      ERROR: 0xFF0000               # Kırmızı
      DEBUG: 0x808080               # Gri
    rate_limit: 20                  # Dakikada maksimum mesaj
    queue_size: 100                 # Mesaj kuyruğu boyutu
    
  # Log kategorileri
  categories:
    security: true                  # Güvenlik logları
    admin: true                     # Admin işlemleri
    player: true                    # Oyuncu işlemleri
    system: true                    # Sistem logları
    discord: true                   # Discord işlemleri
    database: true                  # Veritabanı işlemleri
    
  # Filtreleme
  filters:
    exclude_debug_spam: true        # Debug spam'i filtrele
    exclude_player_movement: true   # Oyuncu hareketi loglarını filtrele
    include_stack_traces: false     # Stack trace'leri dahil et
```

### Mesaj Sistemi Ayarları
```yaml
messages:
  # Varsayılan dil
  default_language: "tr"             # tr, en
  
  # Dil tespiti
  auto_detect_language: true         # Otomatik dil tespiti
  
  # Önbellek ayarları
  cache_messages: true               # Mesajları önbelleğe al
  cache_size: 1000                   # Önbellek boyutu
  cache_expire_time: 3600            # Önbellek süresi (saniye)
  
  # Formatlar
  formats:
    date_format: "dd/MM/yyyy HH:mm:ss"  # Tarih formatı
    time_format: "HH:mm:ss"             # Saat formatı
    number_format: "#,##0.00"           # Sayı formatı
    
  # Placeholder ayarları
  placeholders:
    enabled: true                    # Placeholder desteği
    custom_placeholders: {}          # Özel placeholder'lar
    
  # Mesaj özelleştirme
  customization:
    prefix: "&8[&bDiscordLite&8]&r "  # Mesaj prefix'i
    error_prefix: "&8[&cHata&8]&r "   # Hata prefix'i
    success_prefix: "&8[&aBaşarılı&8]&r " # Başarı prefix'i
```

### Hata Yönetimi Ayarları
```yaml
error_management:
  # Hata loglama
  log_errors: true
  
  # Otomatik kurtarma
  auto_recovery: true
  
  # Rate limiting
  error_rate_limiting: true
  max_errors_per_minute: 10
  
  # Kategori ayarları
  categories:
    database:
      max_retries: 3
      retry_delay: 5000              # ms
      auto_recovery: true
    discord:
      max_retries: 5
      retry_delay: 2000
      auto_recovery: true
    security:
      max_retries: 1
      retry_delay: 10000
      auto_recovery: false
      
  # Bildirim ayarları
  notifications:
    discord_notifications: true
    admin_notifications: true
    console_notifications: true
    
  # Acil durum ayarları
  emergency:
    shutdown_on_critical: false
    backup_on_critical: true
    notify_immediately: true
```

### Cache (Önbellek) Ayarları
```yaml
caching:
  # Ana cache ayarları
  enabled: true
  
  # Player cache
  player_cache:
    size: 1000                       # Maksimum oyuncu sayısı
    expire_time: 3600                # Süre (saniye)
    
  # Discord cache
  discord_cache:
    size: 500                        # Maksimum Discord kullanıcısı
    expire_time: 1800                # Süre (saniye)
    
  # Permission cache
  permission_cache:
    size: 2000                       # Maksimum izin kaydı
    expire_time: 900                 # Süre (saniye)
    
  # Database query cache
  query_cache:
    enabled: true
    size: 1000                       # Maksimum sorgu sayısı
    expire_time: 300                 # Süre (saniye)
    
  # Cleanup ayarları
  cleanup:
    interval: 600                    # Temizlik aralığı (saniye)
    threshold: 0.8                   # Temizlik eşiği (80%)
```

### Performans Ayarları
```yaml
performance:
  # Thread pool ayarları
  thread_pool:
    core_size: 4                     # Temel thread sayısı
    max_size: 16                     # Maksimum thread sayısı
    queue_capacity: 1000             # Kuyruk kapasitesi
    keep_alive_time: 60              # Thread yaşam süresi (saniye)
    
  # Async işlem ayarları
  async_operations:
    database_operations: true       # Veritabanı işlemleri async
    discord_operations: true        # Discord işlemleri async
    file_operations: true           # Dosya işlemleri async
    
  # Optimizasyon ayarları
  optimizations:
    lazy_loading: true              # Lazy loading aktif
    batch_operations: true          # Batch işlemler aktif
    connection_pooling: true        # Connection pooling aktif
    
  # Monitoring
  monitoring:
    enabled: true                   # Performans izleme
    report_interval: 3600           # Rapor aralığı (saniye)
    memory_threshold: 80            # Bellek uyarı eşiği (%)
    cpu_threshold: 90               # CPU uyarı eşiği (%)
```

## 🌐 Dil Dosyaları

### Türkçe Mesajlar (messages/tr.yml)
```yaml
# Genel mesajlar
general:
  plugin_name: "DiscordLite"
  prefix: "&8[&bDiscordLite&8]&r "
  no_permission: "&cBu komutu kullanma iznin yok!"
  player_only: "&cBu komut sadece oyuncular tarafından kullanılabilir!"
  unknown_command: "&cBilinmeyen komut! Yardım için &e/discordlite help &ckullanın."

# Link sistemi mesajları
link:
  success: "&a✅ Hesabınız başarıyla Discord ile bağlandı!"
  already_linked: "&c❌ Hesabınız zaten Discord ile bağlı!"
  not_linked: "&c❌ Hesabınız Discord ile bağlı değil!"
  timeout: "&c⏱️ Doğrulama süresi doldu, tekrar deneyin!"
  discord_dm_sent: "&a📧 Discord'da size DM gönderildi. Lütfen kontrol edin!"
  discord_dm_failed: "&c📧 Discord DM gönderilemedi. DM izinlerinizi kontrol edin!"
  
# 2FA mesajları
two_factor_auth:
  pending: "&e🔒 2FA doğrulaması gerekli! Discord'dan gelen mesajı kontrol edin."
  success: "&a✅ 2FA doğrulaması başarılı! Hoş geldiniz!"
  failed: "&c❌ 2FA doğrulaması başarısız!"
  timeout: "&c⏱️ 2FA doğrulama süresi doldu!"
  required: "&c🔒 Bu sunucuya girmek için 2FA doğrulaması gereklidir!"

# Admin mesajları
admin:
  reload_success: "&a🔄 Plugin başarıyla yeniden yüklendi!"
  reload_failed: "&c🔄 Plugin yeniden yüklenirken hata oluştu!"
  player_unlinked: "&a✅ {player} oyuncusunun bağlantısı kaldırıldı!"
  player_not_found: "&c❌ Oyuncu bulunamadı: {player}"
  
# Hata mesajları
errors:
  database_error: "&c💾 Veritabanı hatası oluştu!"
  discord_error: "&c🤖 Discord bağlantı hatası!"
  unknown_error: "&c❌ Bilinmeyen bir hata oluştu!"
```

### İngilizce Mesajlar (messages/en.yml)
```yaml
# General messages
general:
  plugin_name: "DiscordLite"
  prefix: "&8[&bDiscordLite&8]&r "
  no_permission: "&cYou don't have permission to use this command!"
  player_only: "&cThis command can only be used by players!"
  unknown_command: "&cUnknown command! Use &e/discordlite help &cfor help."

# Link system messages
link:
  success: "&a✅ Your account has been successfully linked to Discord!"
  already_linked: "&c❌ Your account is already linked to Discord!"
  not_linked: "&c❌ Your account is not linked to Discord!"
  timeout: "&c⏱️ Verification timeout, please try again!"
  discord_dm_sent: "&a📧 A DM has been sent to you on Discord. Please check!"
  discord_dm_failed: "&c📧 Failed to send Discord DM. Check your DM permissions!"
```

## 🔧 Konfigürasyon Örnekleri

### Küçük Sunucu Konfigürasyonu
```yaml
# 50 oyuncuya kadar küçük sunucu
database:
  type: "sqlite"
  
performance:
  thread_pool:
    core_size: 2
    max_size: 8
    
caching:
  player_cache:
    size: 100
    expire_time: 1800
```

### Büyük Sunucu Konfigürasyonu
```yaml
# 500+ oyuncu büyük sunucu
database:
  type: "mysql"
  mysql:
    pool_size: 20
    
performance:
  thread_pool:
    core_size: 8
    max_size: 32
    
caching:
  player_cache:
    size: 2000
    expire_time: 7200
```

### Yüksek Güvenlik Konfigürasyonu
```yaml
security:
  threat_detection: true
  ip_ban_enabled: true
  max_login_attempts: 2
  lockout_duration: 3600
  
  rate_limits:
    commands_per_minute: 3
    links_per_hour: 1
    
two_factor_auth:
  enabled: true
  required_on_join: true
  max_attempts: 2
```

## 🚨 Önemli Notlar

### Güvenlik Uyarıları
1. **Bot Token**: Asla bot token'ınızı paylaşmayın!
2. **Database Credentials**: Veritabanı bilgilerini güvenli tutun
3. **File Permissions**: Konfigürasyon dosyalarının izinlerini kontrol edin

### Performans Önerileri
1. **SQLite**: Küçük sunucular için yeterli
2. **MySQL**: Büyük sunucular için önerilir
3. **Cache**: Bellek kullanımını izleyin
4. **Thread Pool**: CPU çekirdek sayısına göre ayarlayın

### Backup Önerileri
```bash
# Günlük backup scripti
#!/bin/bash
DATE=$(date +%Y%m%d)
tar -czf "discordlite_config_$DATE.tar.gz" plugins/DiscordLite/config.yml
tar -czf "discordlite_data_$DATE.tar.gz" plugins/DiscordLite/data/
```

## 📞 Destek

Konfigürasyon konusunda yardıma ihtiyacınız varsa:
- [GitHub Issues](https://github.com/KynuxDev/DiscordLite/issues)
- [Discord Sunucusu](https://discord.gg/bgHexr9rk5)
- [SETUP.md](SETUP.md) - Kurulum kılavuzu
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Sorun giderme