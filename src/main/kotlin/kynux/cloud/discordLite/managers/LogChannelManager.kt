package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.utils.EmbedUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import java.awt.Color
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class LogChannelManager(private val plugin: DiscordLite) {
    
    private val logChannels = ConcurrentHashMap<LogType, TextChannel?>()
    private val messageQueue = LinkedBlockingQueue<QueuedMessage>()
    private val rateLimitTracker = ConcurrentHashMap<LogType, Long>()
    
    companion object {
        private const val RATE_LIMIT_MS = 1000L // 1 saniye
        private const val MAX_QUEUE_SIZE = 100
        private const val BATCH_SIZE = 5
    }
    
    enum class LogType(val configKey: String, val displayName: String, val defaultColor: Color) {
        SECURITY("security", "Güvenlik", Color.RED),
        ADMIN("admin", "Admin", Color.ORANGE),
        PLAYER("player", "Oyuncu", Color.BLUE),
        SYSTEM("system", "Sistem", Color.GREEN),
        ERROR("error", "Hata", Color.RED),
        DEBUG("debug", "Debug", Color.GRAY),
        AUDIT("audit", "Denetim", Color.MAGENTA)
    }
    
    data class QueuedMessage(
        val logType: LogType,
        val title: String,
        val description: String,
        val details: String? = null,
        val color: Color? = null,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    fun initialize() {
        plugin.logger.info("LogChannelManager başlatılıyor...")
        
        loadLogChannels()
        
        startQueueProcessor()
        
        plugin.logger.info("LogChannelManager başarıyla başlatıldı!")
    }
    
    fun shutdown() {
        plugin.logger.info("LogChannelManager kapatılıyor...")
        
        processQueue()
        
        logChannels.clear()
        messageQueue.clear()
        rateLimitTracker.clear()
        
        plugin.logger.info("LogChannelManager kapatıldı!")
    }

    fun logSecurity(title: String, description: String, details: String? = null) {
        queueMessage(LogType.SECURITY, title, description, details, Color.RED)
    }

    fun logAdmin(title: String, description: String, details: String? = null) {
        queueMessage(LogType.ADMIN, title, description, details, Color.ORANGE)
    }

    fun logPlayer(title: String, description: String, details: String? = null) {
        queueMessage(LogType.PLAYER, title, description, details, Color.BLUE)
    }

    fun logSystem(title: String, description: String, details: String? = null) {
        queueMessage(LogType.SYSTEM, title, description, details, Color.GREEN)
    }

    fun logError(title: String, description: String, details: String? = null) {
        queueMessage(LogType.ERROR, title, description, details, Color.RED)
    }

    fun logDebug(title: String, description: String, details: String? = null) {
        if (plugin.configManager.isDebugMode()) {
            queueMessage(LogType.DEBUG, title, description, details, Color.GRAY)
        }
    }

    fun logAudit(title: String, description: String, details: String? = null) {
        queueMessage(LogType.AUDIT, title, description, details, Color.MAGENTA)
    }

    fun logCustom(logType: LogType, title: String, description: String, details: String? = null, color: Color? = null) {
        queueMessage(logType, title, description, details, color ?: logType.defaultColor)
    }

    fun logEmergency(title: String, description: String, details: String? = null) {
        val embed = EmbedBuilder().apply {
            setTitle("🚨 ACİL DURUM - $title")
            setDescription(description)
            setColor(Color.RED)
            details?.let { addField("📋 Detaylar", it, false) }
            addField("🕒 Zaman", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), true)
            addField("🏷️ Öncelik", "**YÜKSEK**", true)
            setTimestamp(java.time.Instant.now())
            setFooter("DiscordLite Emergency System", null)
        }.build()
        
        logChannels.values.filterNotNull().forEach { channel ->
            channel.sendMessageEmbeds(embed).queue()
        }
        
        plugin.logger.severe("ACİL DURUM: $title - $description")
    }

    private fun queueMessage(logType: LogType, title: String, description: String, details: String?, color: Color) {
        val lastSent = rateLimitTracker[logType] ?: 0
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastSent < RATE_LIMIT_MS) {
            if (messageQueue.size < MAX_QUEUE_SIZE) {
                messageQueue.offer(QueuedMessage(logType, title, description, details, color))
            }
            return
        }
        
        rateLimitTracker[logType] = currentTime
        
        if (messageQueue.isEmpty()) {
            sendLogMessage(logType, title, description, details, color)
        } else {
            if (messageQueue.size < MAX_QUEUE_SIZE) {
                messageQueue.offer(QueuedMessage(logType, title, description, details, color))
            }
        }
    }

    private fun loadLogChannels() {
        LogType.values().forEach { logType ->
            val channelId = plugin.configManager.getConfig().getString("discord_channels.log_${logType.configKey}")
            
            if (!channelId.isNullOrBlank()) {
                val guild = plugin.discordManager.getGuild()
                val channel = guild?.getTextChannelById(channelId)
                
                if (channel != null) {
                    logChannels[logType] = channel
                    plugin.logger.info("${logType.displayName} log kanalı yüklendi: ${channel.name}")
                } else {
                    plugin.logger.warning("${logType.displayName} log kanalı bulunamadı: $channelId")
                }
            }
        }
        
        plugin.logger.info("${logChannels.size} log kanalı yüklendi")
    }

    private fun startQueueProcessor() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            processQueue()
        }, 20L, 20L) // Her saniye
    }

    private fun processQueue() {
        val batch = mutableListOf<QueuedMessage>()
        
        repeat(BATCH_SIZE) {
            val message = messageQueue.poll()
            if (message != null) {
                batch.add(message)
            }
        }
        
        batch.forEach { message ->
            sendLogMessage(
                message.logType,
                message.title,
                message.description,
                message.details,
                message.color ?: message.logType.defaultColor
            )
        }
    }

    private fun sendLogMessage(logType: LogType, title: String, description: String, details: String?, color: Color) {
        try {
            val channel = logChannels[logType]
            if (channel == null) {
                plugin.logger.warning("${logType.displayName} log kanalı ayarlanmamış!")
                return
            }
            
            val embed = EmbedBuilder().apply {
                setTitle("${getLogIcon(logType)} $title")
                setDescription(description)
                setColor(color)
                
                details?.let { 
                    addField("📋 Detaylar", it, false)
                }
                
                addField("🕒 Zaman", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), true)
                addField("🏷️ Kategori", logType.displayName, true)
                
                setTimestamp(java.time.Instant.now())
                setFooter("DiscordLite Log System", null)
            }.build()
            
            channel.sendMessageEmbeds(embed).queue(
                null,
                { error ->
                    plugin.logger.warning("Log mesajı gönderme hatası (${logType.displayName}): ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            plugin.logger.severe("Log gönderim hatası: ${e.message}")
        }
    }

    private fun getLogIcon(logType: LogType): String {
        return when (logType) {
            LogType.SECURITY -> "🔒"
            LogType.ADMIN -> "⚙️"
            LogType.PLAYER -> "👤"
            LogType.SYSTEM -> "🖥️"
            LogType.ERROR -> "❌"
            LogType.DEBUG -> "🐛"
            LogType.AUDIT -> "📊"
        }
    }

    fun updateLogChannel(logType: LogType, channelId: String?) {
        if (channelId.isNullOrBlank()) {
            logChannels.remove(logType)
            plugin.logger.info("${logType.displayName} log kanalı kaldırıldı")
            return
        }
        
        val guild = plugin.discordManager.getGuild()
        val channel = guild?.getTextChannelById(channelId)
        
        if (channel != null) {
            logChannels[logType] = channel
            plugin.logger.info("${logType.displayName} log kanalı güncellendi: ${channel.name}")
        } else {
            plugin.logger.warning("${logType.displayName} log kanalı bulunamadı: $channelId")
        }
    }

    fun reloadLogChannels() {
        logChannels.clear()
        loadLogChannels()
        plugin.logger.info("Log kanalları yeniden yüklendi")
    }

    fun getLogStats(): Map<String, Any> {
        return mapOf(
            "active_channels" to logChannels.size,
            "queue_size" to messageQueue.size,
            "channels" to logChannels.mapKeys { it.key.displayName }.mapValues { it.value?.name ?: "Ayarlanmamış" }
        )
    }
}