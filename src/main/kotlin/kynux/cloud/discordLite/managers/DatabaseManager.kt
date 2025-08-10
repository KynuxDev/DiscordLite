package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.DatabaseProvider
import kynux.cloud.discordLite.database.impl.SQLiteDatabaseProvider
import kynux.cloud.discordLite.database.impl.MySQLDatabaseProvider
import kynux.cloud.discordLite.database.impl.YamlDatabaseProvider
import kynux.cloud.discordLite.database.models.SecurityLog
import kynux.cloud.discordLite.database.models.SecurityEventType
import kotlinx.coroutines.*
import java.time.LocalDateTime

class DatabaseManager(private val plugin: DiscordLite) {
    
    lateinit var provider: DatabaseProvider
        private set
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var isInitializing = false
    @Volatile 
    private var isInitialized = false
    
    fun initialize() {
        plugin.logger.info("DatabaseManager başlatılıyor...")
        
        if (isInitializing || isInitialized) {
            plugin.logger.warning("DatabaseManager zaten başlatılıyor veya başlatılmış!")
            return
        }
        
        isInitializing = true
        
        val databaseType = plugin.configManager.getDatabaseType()
        
        provider = when (databaseType) {
            "sqlite" -> {
                plugin.logger.info("SQLite veritabanı kullanılıyor")
                SQLiteDatabaseProvider(plugin)
            }
            "mysql" -> {
                plugin.logger.info("MySQL veritabanı kullanılıyor")
                MySQLDatabaseProvider(plugin)
            }
            "yaml" -> {
                plugin.logger.info("YAML dosya sistemi kullanılıyor")
                YamlDatabaseProvider(plugin)
            }
            else -> {
                plugin.logger.warning("Bilinmeyen veritabanı tipi: $databaseType, SQLite kullanılacak")
                SQLiteDatabaseProvider(plugin)
            }
        }
        
        coroutineScope.launch {
            try {
                provider.initialize()
                isInitialized = true
                plugin.logger.info("Veritabanı başarıyla başlatıldı")
                
                startCleanupTask()
                
            } catch (e: Exception) {
                plugin.logger.severe("Veritabanı başlatılırken hata oluştu: ${e.message}")
                e.printStackTrace()
            } finally {
                isInitializing = false
            }
        }
    }
    
    private fun startCleanupTask() {
        coroutineScope.launch {
            while (isActive) {
                try {
                    delay(300_000) // 5dk
                    provider.cleanup()
                    plugin.logger.info("Veritabanı temizlik işlemi tamamlandı")
                } catch (e: Exception) {
                    plugin.logger.warning("Veritabanı temizlik hatası: ${e.message}")
                }
            }
        }
    }
    
    fun isConnected(): Boolean {
        return if (!isInitialized || isInitializing) {
            false
        } else {
            runBlocking {
                try {
                    provider.isConnected()
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
    
    fun executeAsync(block: suspend DatabaseProvider.() -> Unit) {
        if (!isInitialized) {
            plugin.logger.warning("Veritabanı henüz başlatılmamış, işlem iptal edildi")
            return
        }
        
        coroutineScope.launch {
            try {
                provider.block()
            } catch (e: Exception) {
                plugin.logger.severe("Veritabanı işlemi hatası: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun <T> executeSync(block: suspend DatabaseProvider.() -> T): T? {
        if (!isInitialized) {
            plugin.logger.warning("Veritabanı henüz başlatılmamış, işlem iptal edildi")
            return null
        }
        
        return try {
            runBlocking {
                provider.block()
            }
        } catch (e: Exception) {
            plugin.logger.severe("Senkron veritabanı işlemi hatası: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    fun close() {
        plugin.logger.info("DatabaseManager kapatılıyor...")
        
        try {
            coroutineScope.cancel()
            
            runBlocking {
                provider.close()
            }
            
            plugin.logger.info("Veritabanı başarıyla kapatıldı")
            
        } catch (e: Exception) {
            plugin.logger.severe("Veritabanı kapatılırken hata oluştu: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun saveSecurityLog(securityLog: SecurityLog) {
        executeAsync {
            provider.saveSecurityLog(securityLog)
        }
    }
    
    fun getSecurityLogsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): List<SecurityLog> {
        return executeSync {
            provider.getSecurityLogsByDateRange(startDate, endDate)
        } ?: emptyList()
    }
    
    fun getSecurityLogsByIP(ipAddress: String, limit: Int = 50): List<SecurityLog> {
        return executeSync {
            provider.getSecurityLogsByIP(ipAddress, limit)
        } ?: emptyList()
    }
    
    fun getSecurityLogsByType(eventType: SecurityEventType, limit: Int = 50): List<SecurityLog> {
        return executeSync {
            provider.getSecurityLogsByType(eventType, limit)
        } ?: emptyList()
    }
    
    fun getAllSecurityLogs(limit: Int = 50, offset: Int = 0): List<SecurityLog> {
        return executeSync {
            provider.getAllSecurityLogs(limit, offset)
        } ?: emptyList()
    }
    
    fun deleteOldSecurityLogs(daysOld: Int = 30) {
        executeAsync {
            provider.deleteOldSecurityLogs(daysOld)
        }
    }
}
