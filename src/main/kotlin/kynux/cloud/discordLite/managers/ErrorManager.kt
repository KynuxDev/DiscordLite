package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.models.SecurityEventType
import kynux.cloud.discordLite.database.models.SecurityLog
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ErrorManager(private val plugin: DiscordLite) {
    
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val recentErrors = ConcurrentHashMap<String, LocalDateTime>()
    private val suppressedErrors = ConcurrentHashMap<String, LocalDateTime>()
    
    companion object {
        private const val MAX_ERROR_RATE = 10
        private const val ERROR_WINDOW_MINUTES = 5
        private const val SUPPRESSION_DURATION_MINUTES = 30
    }
    
    enum class ErrorLevel(val severity: Int) {
        TRACE(1),
        DEBUG(2),
        INFO(3),
        WARN(4),
        ERROR(5),
        FATAL(6)
    }
    
    enum class ErrorCategory {
        DATABASE,
        DISCORD_API,
        NETWORK,
        PERMISSION,
        VALIDATION,
        SECURITY,
        CONFIGURATION,
        PLUGIN_INTERACTION,
        INTERNAL,
        USER_INPUT,
        RATE_LIMIT
    }
    
    data class ErrorContext(
        val component: String,
        val action: String,
        val userId: String? = null,
        val ipAddress: String? = null,
        val additionalData: Map<String, Any> = emptyMap()
    )
    
    fun initialize() {
        plugin.logger.info("ErrorManager başlatılıyor...")
        
        startCleanupTask()
        
        addShutdownHook()
        
        plugin.logger.info("ErrorManager başarıyla başlatıldı!")
    }
    
    fun shutdown() {
        plugin.logger.info("ErrorManager kapatılıyor...")
        
        generateFinalErrorReport()
        
        errorCounts.clear()
        recentErrors.clear()
        suppressedErrors.clear()
        
        plugin.logger.info("ErrorManager kapatıldı!")
    }

    fun handleError(
        level: ErrorLevel,
        category: ErrorCategory,
        message: String,
        exception: Throwable? = null,
        context: ErrorContext? = null,
        suppressDuplicates: Boolean = true
    ) {
        try {
            val errorKey = generateErrorKey(category, message, exception)
            
            if (suppressDuplicates && shouldSuppressError(errorKey)) {
                return
            }
            
            incrementErrorCount(errorKey)
            
            val errorDetails = buildErrorDetails(level, category, message, exception, context)
            
            logError(level, errorDetails)
            
            createSecurityLog(level, category, message, context, exception)
            
            sendDiscordNotification(level, category, errorDetails)
            
            attemptAutoRecovery(category, exception, context)
            
            if (level >= ErrorLevel.FATAL) {
                handleCriticalError(errorDetails, exception)
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("ErrorManager'da hata: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun generateErrorKey(category: ErrorCategory, message: String, exception: Throwable?): String {
        val exceptionType = exception?.javaClass?.simpleName ?: "NoException"
        return "${category.name}_${exceptionType}_${message.take(50).hashCode()}"
    }

    private fun shouldSuppressError(errorKey: String): Boolean {
        val now = LocalDateTime.now()
        
        val suppressedUntil = suppressedErrors[errorKey]
        if (suppressedUntil != null && now.isBefore(suppressedUntil)) {
            return true
        }
        
        val recentErrorTime = recentErrors[errorKey]
        if (recentErrorTime != null) {
            val minutesSince = java.time.Duration.between(recentErrorTime, now).toMinutes()
            if (minutesSince < ERROR_WINDOW_MINUTES) {
                val count = errorCounts[errorKey]?.get() ?: 0
                if (count >= MAX_ERROR_RATE) {
                    suppressedErrors[errorKey] = now.plusMinutes(SUPPRESSION_DURATION_MINUTES.toLong())
                    plugin.logger.warning("Hata bastırıldı (rate limit): $errorKey")
                    return true
                }
            }
        }
        
        recentErrors[errorKey] = now
        return false
    }

    private fun incrementErrorCount(errorKey: String) {
        errorCounts.computeIfAbsent(errorKey) { AtomicInteger(0) }.incrementAndGet()
    }

    private fun buildErrorDetails(
        level: ErrorLevel,
        category: ErrorCategory,
        message: String,
        exception: Throwable?,
        context: ErrorContext?
    ): Map<String, Any> {
        val details = mutableMapOf<String, Any>(
            "level" to level.name,
            "category" to category.name,
            "message" to message,
            "timestamp" to LocalDateTime.now().toString(),
            "thread" to Thread.currentThread().name,
            "server_info" to getServerInfo()
        )
        
        if (exception != null) {
            details["exception_type"] = exception.javaClass.simpleName
            details["exception_message"] = exception.message ?: "No message"
            details["stack_trace"] = getStackTrace(exception)
            
            val causes = mutableListOf<String>()
            var cause = exception.cause
            while (cause != null) {
                causes.add("${cause.javaClass.simpleName}: ${cause.message}")
                cause = cause.cause
            }
            if (causes.isNotEmpty()) {
                details["cause_chain"] = causes
            }
        }
        
        if (context != null) {
            details["component"] = context.component
            details["action"] = context.action
            if (context.userId != null) details["user_id"] = context.userId
            if (context.ipAddress != null) details["ip_address"] = context.ipAddress
            if (context.additionalData.isNotEmpty()) details["additional_data"] = context.additionalData
        }
        
        return details
    }

    private fun logError(level: ErrorLevel, details: Map<String, Any>) {
        val message = formatErrorMessage(details)
        
        when (level) {
            ErrorLevel.TRACE -> plugin.logger.finest(message)
            ErrorLevel.DEBUG -> plugin.logger.fine(message)
            ErrorLevel.INFO -> plugin.logger.info(message)
            ErrorLevel.WARN -> plugin.logger.warning(message)
            ErrorLevel.ERROR -> plugin.logger.severe(message)
            ErrorLevel.FATAL -> plugin.logger.severe("FATAL: $message")
        }
        
        if (plugin.configManager.isDebugMode()) {
            plugin.logger.info("Error details: $details")
        }
    }

    private fun createSecurityLog(
        level: ErrorLevel,
        category: ErrorCategory,
        message: String,
        context: ErrorContext?,
        exception: Throwable?
    ) {
        if (level >= ErrorLevel.ERROR) {
            val securityLog = SecurityLog.createSystemError(
                errorMessage = message,
                component = context?.component ?: category.name,
                severity = level.name
            )
            
            plugin.databaseManager.executeAsync {
                try {
                    plugin.databaseManager.provider.saveSecurityLog(securityLog)
                } catch (e: Exception) {
                    plugin.logger.severe("Güvenlik logu kaydedilemedi: ${e.message}")
                }
            }
        }
    }

    private fun sendDiscordNotification(
        level: ErrorLevel,
        category: ErrorCategory,
        details: Map<String, Any>
    ) {
        if (level >= ErrorLevel.ERROR && plugin.configManager.isSecurityNotifications()) {
            try {
                val title = when (level) {
                    ErrorLevel.FATAL -> "🚨 FATAL ERROR"
                    ErrorLevel.ERROR -> "❌ ERROR"
                    else -> "⚠️ WARNING"
                }
                
                val description = "${details["message"]}"
                val component = details["component"] ?: "Unknown"
                val timestamp = details["timestamp"] ?: "Unknown"
                
                val formattedDetails = """
                    **Kategori:** ${category.name}
                    **Komponent:** $component
                    **Zaman:** $timestamp
                    **Thread:** ${details["thread"]}
                    ${if (details.containsKey("exception_type")) "**Exception:** ${details["exception_type"]}" else ""}
                """.trimIndent()
                
                if (level >= ErrorLevel.FATAL) {
                    plugin.logChannelManager.logEmergency(title, description, formattedDetails)
                } else {
                    plugin.logChannelManager.logError(title, description)
                }
                
            } catch (e: Exception) {
                plugin.logger.warning("Discord error notification gönderilemedi: ${e.message}")
            }
        }
    }

    private fun attemptAutoRecovery(
        category: ErrorCategory,
        exception: Throwable?,
        context: ErrorContext?
    ) {
        try {
            when (category) {
                ErrorCategory.DATABASE -> recoverDatabase()
                ErrorCategory.DISCORD_API -> recoverDiscord()
                ErrorCategory.NETWORK -> recoverNetwork()
                ErrorCategory.RATE_LIMIT -> handleRateLimit(context)
                else -> {}
            }
        } catch (e: Exception) {
            plugin.logger.warning("Auto recovery başarısız: ${e.message}")
        }
    }

    private fun handleCriticalError(details: Map<String, Any>, exception: Throwable?) {
        try {
            if (plugin.configManager.isAutoBackup()) {
                plugin.logger.severe("Kritik hata nedeniyle emergency backup başlatılıyor...")
            }
            val component = details["component"] as? String
            if (component == "DatabaseManager" || component == "DiscordManager") {
                plugin.logger.severe("Kritik komponent hatası! Plugin devre dışı bırakılıyor...")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    Bukkit.getPluginManager().disablePlugin(plugin)
                })
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("Kritik hata işleme başarısız: ${e.message}")
        }
    }

    private fun recoverDatabase() {
        plugin.logger.info("Veritabanı recovery başlatılıyor...")
        try {
            if (!plugin.databaseManager.isConnected()) {
                plugin.databaseManager.initialize()
            }
        } catch (e: Exception) {
            plugin.logger.severe("Veritabanı recovery başarısız: ${e.message}")
        }
    }

    private fun recoverDiscord() {
        plugin.logger.info("Discord recovery başlatılıyor...")
        try {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
                plugin.discordManager.initialize()
            }, 100L) // 5sn
        } catch (e: Exception) {
            plugin.logger.severe("Discord recovery başarısız: ${e.message}")
        }
    }

    private fun recoverNetwork() {
        plugin.logger.info("Network recovery başlatılıyor...")
    }

    private fun handleRateLimit(context: ErrorContext?) {
        plugin.logger.warning("Rate limit tespit edildi, işlemler yavaşlatılıyor...")
        
        val userId = context?.userId
        if (userId != null) {
            plugin.logger.info("Kullanıcı rate limit: $userId")
        }
    }

    private fun getStackTrace(exception: Throwable): String {
        return try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "Stack trace alınamadı: ${e.message}"
        }
    }

    private fun getServerInfo(): Map<String, Any> {
        return try {
            mapOf(
                "server_version" to Bukkit.getVersion(),
                "bukkit_version" to Bukkit.getBukkitVersion(),
                "online_players" to Bukkit.getOnlinePlayers().size,
                "max_players" to Bukkit.getMaxPlayers(),
                "java_version" to System.getProperty("java.version"),
                "os_name" to System.getProperty("os.name"),
                "available_memory" to "${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB",
                "max_memory" to "${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB",
                "plugin_version" to plugin.description.version
            )
        } catch (e: Exception) {
            mapOf("error" to "Sunucu bilgileri alınamadı: ${e.message}")
        }
    }

    private fun formatErrorMessage(details: Map<String, Any>): String {
        val level = details["level"]
        val category = details["category"]
        val message = details["message"]
        val component = details["component"]
        val exceptionType = details["exception_type"]
        
        return buildString {
            append("[$level] ")
            if (category != null) append("[$category] ")
            if (component != null) append("[$component] ")
            append(message)
            if (exceptionType != null) append(" ($exceptionType)")
        }
    }

    private fun startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            try {
                cleanupOldErrors()
            } catch (e: Exception) {
                plugin.logger.warning("Error cleanup görevi başarısız: ${e.message}")
            }
        }, 6000L, 6000L) // Her 5 dakika
    }

    private fun cleanupOldErrors() {
        val now = LocalDateTime.now()
        val cutoffTime = now.minusMinutes(ERROR_WINDOW_MINUTES.toLong())
        
        recentErrors.entries.removeIf { it.value.isBefore(cutoffTime) }
        
        suppressedErrors.entries.removeIf { it.value.isBefore(now) }
        
        errorCounts.entries.removeIf {
            !recentErrors.containsKey(it.key) && !suppressedErrors.containsKey(it.key)
        }
    }

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                generateFinalErrorReport()
            } catch (e: Exception) {
                System.err.println("Final error report oluşturulamadı: ${e.message}")
            }
        })
    }

    private fun generateFinalErrorReport() {
        try {
            val totalErrors = errorCounts.values.sumOf { it.get() }
            val uniqueErrors = errorCounts.size
            val suppressedCount = suppressedErrors.size
            
            plugin.logger.info("=== FINAL ERROR REPORT ===")
            plugin.logger.info("Toplam hata: $totalErrors")
            plugin.logger.info("Benzersiz hata tipi: $uniqueErrors")
            plugin.logger.info("Bastırılan hata: $suppressedCount")
            
            if (totalErrors > 0) {
                plugin.logger.info("En sık hata tipleri:")
                errorCounts.entries
                    .sortedByDescending { it.value.get() }
                    .take(5)
                    .forEach { (key, count) ->
                        plugin.logger.info("  $key: ${count.get()} kez")
                    }
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("Final error report oluşturulamadı: ${e.message}")
        }
    }

    fun getErrorStats(): Map<String, Any> {
        return mapOf(
            "total_errors" to errorCounts.values.sumOf { it.get() },
            "unique_error_types" to errorCounts.size,
            "suppressed_errors" to suppressedErrors.size,
            "recent_errors" to recentErrors.size,
            "top_errors" to errorCounts.entries
                .sortedByDescending { it.value.get() }
                .take(10)
                .associate { it.key to it.value.get() }
        )
    }

    fun logTrace(component: String, message: String, exception: Throwable? = null) {
        handleError(ErrorLevel.TRACE, ErrorCategory.INTERNAL, message, exception, 
            ErrorContext(component, "trace"))
    }
    
    fun logDebug(component: String, message: String, exception: Throwable? = null) {
        handleError(ErrorLevel.DEBUG, ErrorCategory.INTERNAL, message, exception, 
            ErrorContext(component, "debug"))
    }
    
    fun logInfo(component: String, message: String) {
        handleError(ErrorLevel.INFO, ErrorCategory.INTERNAL, message, null, 
            ErrorContext(component, "info"))
    }
    
    fun logWarning(component: String, message: String, exception: Throwable? = null) {
        handleError(ErrorLevel.WARN, ErrorCategory.INTERNAL, message, exception, 
            ErrorContext(component, "warning"))
    }
    
    fun logError(component: String, message: String, exception: Throwable? = null) {
        handleError(ErrorLevel.ERROR, ErrorCategory.INTERNAL, message, exception, 
            ErrorContext(component, "error"))
    }
    
    fun logFatal(component: String, message: String, exception: Throwable? = null) {
        handleError(ErrorLevel.FATAL, ErrorCategory.INTERNAL, message, exception, 
            ErrorContext(component, "fatal"))
    }
    
    fun logDatabaseError(component: String, message: String, exception: Throwable? = null) {
        handleError(ErrorLevel.ERROR, ErrorCategory.DATABASE, message, exception, 
            ErrorContext(component, "database_operation"))
    }
    
    fun logDiscordError(component: String, message: String, exception: Throwable? = null) {
        handleError(ErrorLevel.ERROR, ErrorCategory.DISCORD_API, message, exception, 
            ErrorContext(component, "discord_api"))
    }
    
    fun logSecurityError(component: String, message: String, player: Player? = null, exception: Throwable? = null) {
        val context = ErrorContext(
            component = component,
            action = "security_violation",
            userId = player?.uniqueId?.toString(),
            ipAddress = player?.address?.address?.hostAddress
        )
        handleError(ErrorLevel.ERROR, ErrorCategory.SECURITY, message, exception, context)
    }
    
    fun logValidationError(component: String, message: String, input: String? = null) {
        val context = ErrorContext(
            component = component,
            action = "validation",
            additionalData = if (input != null) mapOf("input" to input) else emptyMap()
        )
        handleError(ErrorLevel.WARN, ErrorCategory.VALIDATION, message, null, context)
    }
}