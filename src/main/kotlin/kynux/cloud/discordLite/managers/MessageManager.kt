package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStream
import java.text.MessageFormat
import java.util.concurrent.ConcurrentHashMap

class MessageManager(private val plugin: DiscordLite) {
    
    private val messageCache = ConcurrentHashMap<String, String>()
    private lateinit var currentLanguage: String
    private lateinit var messages: FileConfiguration
    private lateinit var fallbackMessages: FileConfiguration
    
    fun initialize() {
        plugin.logger.info("MessageManager başlatılıyor...")
        
        currentLanguage = plugin.configManager.getLanguage()
        
        loadMessages(currentLanguage)
        loadFallbackMessages()
        
        plugin.logger.info("MessageManager başarıyla başlatıldı! Dil: $currentLanguage")
    }
    
    fun shutdown() {
        plugin.logger.info("MessageManager kapatılıyor...")
        messageCache.clear()
        plugin.logger.info("MessageManager kapatıldı!")
    }

    private fun loadMessages(language: String) {
        try {
            val messageFile = File(plugin.dataFolder, "messages/$language.yml")
            
            if (!messageFile.exists()) {
                saveResourceFile("messages/$language.yml", messageFile)
            }
            
            messages = YamlConfiguration.loadConfiguration(messageFile)
            
            messageCache.clear()
            
            plugin.logger.info("Mesaj dosyası yüklendi: $language.yml")
            
        } catch (e: Exception) {
            plugin.logger.severe("Mesaj dosyası yüklenemedi ($language): ${e.message}")
            
            if (language != "en") {
                loadMessages("en")
                currentLanguage = "en"
            }
        }
    }

    private fun loadFallbackMessages() {
        try {
            val fallbackFile = File(plugin.dataFolder, "messages/en.yml")
            
            if (!fallbackFile.exists()) {
                saveResourceFile("messages/en.yml", fallbackFile)
            }
            
            fallbackMessages = YamlConfiguration.loadConfiguration(fallbackFile)
            plugin.logger.info("Fallback mesaj dosyası yüklendi: en.yml")
            
        } catch (e: Exception) {
            plugin.logger.severe("Fallback mesaj dosyası yüklenemedi: ${e.message}")
        }
    }

    private fun saveResourceFile(resourcePath: String, targetFile: File) {
        try {
            targetFile.parentFile.mkdirs()
            
            val inputStream: InputStream? = plugin.getResource(resourcePath)
            if (inputStream != null) {
                targetFile.writeBytes(inputStream.readBytes())
                inputStream.close()
            } else {
                plugin.logger.warning("Kaynak dosyası bulunamadı: $resourcePath")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Kaynak dosyası kopyalanamadı ($resourcePath): ${e.message}")
        }
    }

    fun getMessage(key: String): String {
        val cachedMessage = messageCache[key]
        if (cachedMessage != null) {
            return cachedMessage
        }
        
        var message = messages.getString(key)
        
        if (message == null && ::fallbackMessages.isInitialized) {
            message = fallbackMessages.getString(key)
        }
        
        if (message == null) {
            message = "Missing message: $key"
            plugin.logger.warning("Mesaj bulunamadı: $key")
        }
        
        message = applyAdvancedFormatting(message)
        
        message = ChatColor.translateAlternateColorCodes('&', message)
        
        messageCache[key] = message
        
        return message
    }

    fun getMessage(key: String, vararg args: Any): String {
        val message = getMessage(key)
        
        return try {
            var formattedMessage = message
            args.forEachIndexed { index, arg ->
                formattedMessage = formattedMessage.replace("{$index}", arg.toString())
                formattedMessage = formattedMessage.replace("{${getArgName(index)}}", arg.toString())
            }
            
            if (args.isNotEmpty() && message.contains("{0")) {
                MessageFormat.format(formattedMessage, *args)
            } else {
                formattedMessage
            }
        } catch (e: Exception) {
            plugin.logger.warning("Mesaj formatlanamadı ($key): ${e.message}")
            message
        }
    }

    fun getMessage(key: String, params: Map<String, Any>): String {
        var message = getMessage(key)
        
        params.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value.toString())
        }
        
        return message
    }

    fun getFormattedMessage(key: String, vararg args: Any): String {
        val prefix = getMessage("general.prefix")
        val message = getMessage(key, *args)
        return prefix + message
    }

    fun getSuccessMessage(message: String): String {
        return getMessage("general.success", message)
    }

    fun getErrorMessage(message: String): String {
        return getMessage("general.error", message)
    }

    fun getWarningMessage(message: String): String {
        return getMessage("general.warning", message)
    }

    fun getInfoMessage(message: String): String {
        return getMessage("general.info", message)
    }

    fun getLoadingMessage(message: String): String {
        return getMessage("general.loading", message)
    }

    fun getCommandDescription(command: String): String {
        return getMessage("commands.$command.description")
    }

    fun getCommandUsage(command: String): String {
        return getMessage("commands.$command.usage")
    }

    fun formatTime(seconds: Long): String {
        if (seconds <= 0) return getMessage("time.now")
        if (seconds == Long.MAX_VALUE) return getMessage("time.permanent")
        
        val years = seconds / 31536000
        val months = (seconds % 31536000) / 2592000
        val weeks = (seconds % 2592000) / 604800
        val days = (seconds % 604800) / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        val parts = mutableListOf<String>()
        
        if (years > 0) parts.add("$years ${getTimeUnit("year", years)}")
        if (months > 0) parts.add("$months ${getTimeUnit("month", months)}")
        if (weeks > 0) parts.add("$weeks ${getTimeUnit("week", weeks)}")
        if (days > 0) parts.add("$days ${getTimeUnit("day", days)}")
        if (hours > 0) parts.add("$hours ${getTimeUnit("hour", hours)}")
        if (minutes > 0) parts.add("$minutes ${getTimeUnit("minute", minutes)}")
        if (secs > 0) parts.add("$secs ${getTimeUnit("second", secs)}")
        
        return when (parts.size) {
            0 -> getMessage("time.now")
            1 -> parts[0]
            2 -> "${parts[0]} ve ${parts[1]}"
            else -> "${parts.dropLast(1).joinToString(", ")} ve ${parts.last()}"
        }
    }

    private fun getTimeUnit(unit: String, count: Long): String {
        return if (count == 1L) {
            getMessage("time.$unit")
        } else {
            getMessage("time.${unit}s")
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }

    fun formatPercentage(value: Double, max: Double): String {
        val percentage = if (max > 0) (value / max * 100) else 0.0
        return String.format("%.1f%%", percentage)
    }

    fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }

    fun formatBoolean(value: Boolean): String {
        return if (value) {
            getMessage("general.enabled")
        } else {
            getMessage("general.disabled")
        }
    }

    fun formatList(items: List<String>, separator: String = ", "): String {
        return when (items.size) {
            0 -> getMessage("general.none")
            1 -> items[0]
            2 -> "${items[0]} ve ${items[1]}"
            else -> "${items.dropLast(1).joinToString(separator)} ve ${items.last()}"
        }
    }

    fun changeLanguage(language: String): Boolean {
        return try {
            loadMessages(language)
            currentLanguage = language
            
            plugin.configManager.setConfigValue("general.language", language)
            
            plugin.logger.info("Dil değiştirildi: $language")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Dil değiştirilemedi ($language): ${e.message}")
            false
        }
    }

    fun getCurrentLanguage(): String = currentLanguage

    fun getSupportedLanguages(): List<String> {
        val messagesDir = File(plugin.dataFolder, "messages")
        if (!messagesDir.exists()) return listOf("en", "tr")
        
        return messagesDir.listFiles { _, name -> name.endsWith(".yml") }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: listOf("en", "tr")
    }

    fun reloadMessages() {
        try {
            loadMessages(currentLanguage)
            loadFallbackMessages()
            plugin.logger.info("Mesajlar yeniden yüklendi!")
        } catch (e: Exception) {
            plugin.logger.severe("Mesajlar yeniden yüklenemedi: ${e.message}")
        }
    }

    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "cached_messages" to messageCache.size,
            "current_language" to currentLanguage,
            "supported_languages" to getSupportedLanguages().size,
            "cache_memory_usage" to formatSize(
                messageCache.values.sumOf { it.length * 2L }
            )
        )
    }

    fun clearCache() {
        messageCache.clear()
        plugin.logger.info("Mesaj cache'i temizlendi!")
    }

    fun getAllMessageKeys(): Set<String> {
        return messages.getKeys(true).filter { messages.isString(it) }.toSet()
    }

    fun hasMessage(key: String): Boolean {
        return messages.contains(key) || (::fallbackMessages.isInitialized && fallbackMessages.contains(key))
    }

    private fun getArgName(index: Int): String {
        return when (index) {
            0 -> "message"
            1 -> "player"
            2 -> "reason"
            3 -> "time"
            4 -> "amount"
            5 -> "target"
            6 -> "source"
            7 -> "value"
            8 -> "type"
            9 -> "error"
            else -> "arg$index"
        }
    }

    private fun applyAdvancedFormatting(message: String): String {
        var formatted = message
        
        val hexPattern = "&#([A-Fa-f0-9]{6})".toRegex()
        formatted = hexPattern.replace(formatted) { matchResult ->
            val hex = matchResult.groupValues[1]
            "&x&${hex[0]}&${hex[1]}&${hex[2]}&${hex[3]}&${hex[4]}&${hex[5]}"
        }
        return formatted
    }

    fun createBorderedMessage(title: String, content: String, footer: String): String {
        val formattedTitle = applyAdvancedFormatting(title)
        val formattedContent = applyAdvancedFormatting(content)
        val formattedFooter = applyAdvancedFormatting(footer)
        
        return ChatColor.translateAlternateColorCodes('&', "$formattedTitle\n$formattedContent\n$formattedFooter")
    }

    fun createGradientHeader(header: String, content: String): String {
        val formattedHeader = applyAdvancedFormatting(header)
        val formattedContent = applyAdvancedFormatting(content)
        
        return ChatColor.translateAlternateColorCodes('&', "$formattedHeader\n\n$formattedContent")
    }

    fun createMinimalistAction(content: String): String {
        val formatted = applyAdvancedFormatting(content)
        return ChatColor.translateAlternateColorCodes('&', formatted)
    }

    fun getModernVerifyStarted(code: String): String {
        val title = getMessage("verify_modern.started.title")
        val content = getMessage("verify_modern.started.content", code)
        val footer = getMessage("verify_modern.started.footer")
        
        return createBorderedMessage(title, content, footer)
    }

    fun getModernVerifySuccess(): String {
        val header = getMessage("verify_modern.success.header")
        val content = getMessage("verify_modern.success.content")
        
        return createGradientHeader(header, content)
    }

    fun getModernVerifyAlreadyLinked(): String {
        val title = getMessage("verify_modern.already_linked.title")
        val content = getMessage("verify_modern.already_linked.content")
        val footer = getMessage("verify_modern.already_linked.footer")
        
        return createBorderedMessage(title, content, footer)
    }

    fun getModernVerifyCooldown(time: Long): String {
        val content = getMessage("verify_modern.cooldown.content", time)
        return createMinimalistAction(content)
    }

    fun getModernVerifyTimeout(): String {
        val title = getMessage("verify_modern.timeout.title")
        val content = getMessage("verify_modern.timeout.content")
        val footer = getMessage("verify_modern.timeout.footer")
        
        return createBorderedMessage(title, content, footer)
    }

    fun getModernUnlinkSuccess(): String {
        val header = getMessage("unlink_modern.success.header")
        val content = getMessage("unlink_modern.success.content")
        
        return createGradientHeader(header, content)
    }

    private fun validateMessage(key: String, message: String): Boolean {
        if (message.isBlank()) {
            plugin.logger.warning("Boş mesaj: $key")
            return false
        }
        
        if (message.length > 1000) {
            plugin.logger.warning("Çok uzun mesaj: $key (${message.length} karakter)")
        }
        
        val invalidPlaceholders = message.count { it == '{' } != message.count { it == '}' }
        if (invalidPlaceholders) {
            plugin.logger.warning("Geçersiz placeholder: $key")
        }
        
        return true
    }
}