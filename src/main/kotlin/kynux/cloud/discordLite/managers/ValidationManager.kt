package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import org.bukkit.entity.Player
import java.net.InetAddress
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class ValidationManager(private val plugin: DiscordLite) {
    
    private val inputCache = ConcurrentHashMap<String, ValidationResult>()
    private val securityPatterns = mutableMapOf<String, Pattern>()
    
    companion object {
        private const val MAX_STRING_LENGTH = 1000
        private const val MAX_CACHE_SIZE = 1000
        private const val MAX_NESTING_DEPTH = 10
        
        private val SQL_INJECTION_PATTERNS = listOf(
            "(?i)(union|select|insert|update|delete|drop|create|alter)\\s+",
            "(?i)'\\s*(or|and)\\s*'",
            "(?i)\\bor\\s+1\\s*=\\s*1\\b",
            "(?i)\\band\\s+1\\s*=\\s*1\\b",
            "(?i)--\\s*",
            "(?i)/\\*.*?\\*/",
            "(?i)xp_cmdshell",
            "(?i)sp_executesql"
        )
        
        private val XSS_PATTERNS = listOf(
            "(?i)<script[^>]*>.*?</script>",
            "(?i)javascript:",
            "(?i)vbscript:",
            "(?i)onload\\s*=",
            "(?i)onerror\\s*=",
            "(?i)onclick\\s*=",
            "(?i)<iframe[^>]*>",
            "(?i)<object[^>]*>",
            "(?i)<embed[^>]*>"
        )
        
        private val COMMAND_INJECTION_PATTERNS = listOf(
            "(?i)\\b(cmd|command|exec|system|eval)\\s*\\(",
            "(?i)[;&|`]",
            "(?i)\\$\\(.*\\)",
            "(?i)`.*`",
            "(?i)\\|\\s*(rm|del|format)",
            "(?i)>(\\s)*/dev/null"
        )
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val sanitizedValue: String? = null,
        val riskLevel: RiskLevel = RiskLevel.LOW,
        val detectedThreats: List<String> = emptyList()
    )
    
    enum class RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    enum class InputType {
        USERNAME, DISCORD_ID, IP_ADDRESS, COMMAND, MESSAGE, URL, EMAIL, UUID, JSON, SQL
    }
    
    fun initialize() {
        plugin.logger.info("ValidationManager başlatılıyor...")
        compileSecurityPatterns()
        startCacheCleanup()
        plugin.logger.info("ValidationManager başarıyla başlatıldı!")
    }
    
    fun shutdown() {
        plugin.logger.info("ValidationManager kapatılıyor...")
        inputCache.clear()
        securityPatterns.clear()
        plugin.logger.info("ValidationManager kapatıldı!")
    }
    
    fun validateInput(
        input: String,
        type: InputType,
        maxLength: Int = MAX_STRING_LENGTH,
        allowEmpty: Boolean = false,
        sanitize: Boolean = true
    ): ValidationResult {
        try {
            val cacheKey = generateCacheKey(input, type, maxLength, allowEmpty, sanitize)
            inputCache[cacheKey]?.let { return it }
            
            val basicResult = performBasicValidation(input, maxLength, allowEmpty)
            if (!basicResult.isValid) {
                return cacheAndReturn(cacheKey, basicResult)
            }
            
            val typeResult = performTypeValidation(input, type)
            if (!typeResult.isValid) {
                return cacheAndReturn(cacheKey, typeResult)
            }
            
            val securityResult = performSecurityValidation(input, type)
            if (!securityResult.isValid) {
                return cacheAndReturn(cacheKey, securityResult)
            }
            
            val sanitizedValue = if (sanitize) sanitizeInput(input, type) else input
            val riskLevel = calculateRiskLevel(input, type)
            
            val result = ValidationResult(
                isValid = true,
                sanitizedValue = sanitizedValue,
                riskLevel = riskLevel
            )
            
            return cacheAndReturn(cacheKey, result)
            
        } catch (e: Exception) {
            plugin.logger.severe("Validation error: ${e.message}")
            return ValidationResult(false, "Validation failed: ${e.message}")
        }
    }
    
    private fun performBasicValidation(input: String, maxLength: Int, allowEmpty: Boolean): ValidationResult {
        if (input.isEmpty()) {
            return if (allowEmpty) {
                ValidationResult(isValid = true)
            } else {
                ValidationResult(isValid = false, errorMessage = "Input cannot be empty")
            }
        }
        
        if (input.length > maxLength) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Input too long: ${input.length} > $maxLength",
                riskLevel = RiskLevel.MEDIUM
            )
        }
        
        if (containsControlCharacters(input)) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Input contains control characters",
                riskLevel = RiskLevel.MEDIUM,
                detectedThreats = listOf("CONTROL_CHARACTERS")
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun performTypeValidation(input: String, type: InputType): ValidationResult {
        return when (type) {
            InputType.USERNAME -> validateUsername(input)
            InputType.DISCORD_ID -> validateDiscordId(input)
            InputType.IP_ADDRESS -> validateIPAddress(input)
            InputType.COMMAND -> validateCommand(input)
            InputType.MESSAGE -> validateMessage(input)
            InputType.URL -> validateURL(input)
            InputType.EMAIL -> validateEmail(input)
            InputType.UUID -> validateUUID(input)
            InputType.JSON -> validateJSON(input)
            InputType.SQL -> validateSQL(input)
        }
    }
    
    private fun validateUsername(input: String): ValidationResult {
        if (!input.matches("^[a-zA-Z0-9_]{3,16}$".toRegex())) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid username format",
                riskLevel = RiskLevel.LOW
            )
        }
        
        val bannedWords = listOf("admin", "owner", "staff", "mod", "operator", "console")
        if (bannedWords.any { input.lowercase().contains(it) }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Username contains banned word",
                riskLevel = RiskLevel.MEDIUM,
                detectedThreats = listOf("BANNED_WORD")
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun validateDiscordId(input: String): ValidationResult {
        if (!input.matches("^\\d{17,19}$".toRegex())) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid Discord ID format",
                riskLevel = RiskLevel.LOW
            )
        }
        
        val discordId = input.toLongOrNull() ?: return ValidationResult(
            isValid = false,
            errorMessage = "Discord ID is not a valid number",
            riskLevel = RiskLevel.MEDIUM
        )
        
        if (discordId < 4194304) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Discord ID too old/invalid",
                riskLevel = RiskLevel.MEDIUM
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun validateIPAddress(input: String): ValidationResult {
        try {
            val inetAddress = InetAddress.getByName(input)
            
            if (inetAddress.isSiteLocalAddress && !plugin.configManager.isIPWhitelistEnabled()) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Private IP addresses not allowed",
                    riskLevel = RiskLevel.MEDIUM,
                    detectedThreats = listOf("PRIVATE_IP")
                )
            }
            
            if (inetAddress.isLoopbackAddress && input != "127.0.0.1") {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Loopback address not allowed",
                    riskLevel = RiskLevel.MEDIUM,
                    detectedThreats = listOf("LOOPBACK_IP")
                )
            }
            
            return ValidationResult(isValid = true)
            
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid IP address format",
                riskLevel = RiskLevel.LOW
            )
        }
    }
    
    private fun validateCommand(input: String): ValidationResult {
        val threats = mutableListOf<String>()
        
        COMMAND_INJECTION_PATTERNS.forEach { pattern ->
            if (Pattern.compile(pattern).matcher(input).find()) {
                threats.add("COMMAND_INJECTION")
            }
        }
        
        if (threats.isNotEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Command contains injection patterns",
                riskLevel = RiskLevel.CRITICAL,
                detectedThreats = threats
            )
        }
        
        val bannedCommands = listOf("stop", "restart", "reload", "op", "deop", "ban", "pardon")
        val command = input.split(" ")[0].lowercase()
        
        if (bannedCommands.contains(command)) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Command is banned",
                riskLevel = RiskLevel.HIGH,
                detectedThreats = listOf("BANNED_COMMAND")
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun validateMessage(input: String): ValidationResult {
        val threats = mutableListOf<String>()
        
        XSS_PATTERNS.forEach { pattern ->
            if (Pattern.compile(pattern).matcher(input).find()) {
                threats.add("XSS")
            }
        }
        
        SQL_INJECTION_PATTERNS.forEach { pattern ->
            if (Pattern.compile(pattern).matcher(input).find()) {
                threats.add("SQL_INJECTION")
            }
        }
        
        if (isSpamMessage(input)) {
            threats.add("SPAM")
        }
        
        if (threats.isNotEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Message contains security threats",
                riskLevel = if (threats.contains("SQL_INJECTION")) RiskLevel.CRITICAL else RiskLevel.HIGH,
                detectedThreats = threats
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun validateURL(input: String): ValidationResult {
        try {
            val url = java.net.URL(input)
            
            if (url.protocol !in listOf("http", "https")) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Only HTTP/HTTPS URLs allowed",
                    riskLevel = RiskLevel.MEDIUM,
                    detectedThreats = listOf("INVALID_PROTOCOL")
                )
            }
            
            if (isMaliciousDomain(url.host)) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Malicious domain detected",
                    riskLevel = RiskLevel.CRITICAL,
                    detectedThreats = listOf("MALICIOUS_DOMAIN")
                )
            }
            
            return ValidationResult(isValid = true)
            
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid URL format",
                riskLevel = RiskLevel.LOW
            )
        }
    }
    
    private fun validateEmail(input: String): ValidationResult {
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        
        if (!input.matches(emailPattern.toRegex())) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid email format",
                riskLevel = RiskLevel.LOW
            )
        }
        
        val domain = input.substringAfter("@")
        if (isDisposableEmailDomain(domain)) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Disposable email not allowed",
                riskLevel = RiskLevel.MEDIUM,
                detectedThreats = listOf("DISPOSABLE_EMAIL")
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun validateUUID(input: String): ValidationResult {
        try {
            UUID.fromString(input)
            return ValidationResult(isValid = true)
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid UUID format",
                riskLevel = RiskLevel.LOW
            )
        }
    }
    
    private fun validateJSON(input: String): ValidationResult {
        try {
            parseJsonSafely(input)
            
            if (getJsonNestingDepth(input) > MAX_NESTING_DEPTH) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "JSON nesting too deep",
                    riskLevel = RiskLevel.MEDIUM,
                    detectedThreats = listOf("DEEP_NESTING")
                )
            }
            
            return ValidationResult(isValid = true)
            
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid JSON format",
                riskLevel = RiskLevel.LOW
            )
        }
    }
    
    private fun validateSQL(input: String): ValidationResult {
        val threats = mutableListOf<String>()
        
        SQL_INJECTION_PATTERNS.forEach { pattern ->
            if (Pattern.compile(pattern).matcher(input).find()) {
                threats.add("SQL_INJECTION")
            }
        }
        
        if (threats.isNotEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "SQL contains injection patterns",
                riskLevel = RiskLevel.CRITICAL,
                detectedThreats = threats
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun performSecurityValidation(input: String, type: InputType): ValidationResult {
        val threats = mutableListOf<String>()
        
        if (containsUnicodeSecurity(input)) {
            threats.add("UNICODE_SECURITY")
        }
        
        if (containsEncodingAttack(input)) {
            threats.add("ENCODING_ATTACK")
        }
        
        if (containsDirectoryTraversal(input)) {
            threats.add("DIRECTORY_TRAVERSAL")
        }
        
        if (threats.isNotEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Security threats detected",
                riskLevel = RiskLevel.HIGH,
                detectedThreats = threats
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    private fun sanitizeInput(input: String, type: InputType): String {
        var sanitized = input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
        
        when (type) {
            InputType.MESSAGE -> {
                sanitized = sanitizeMessageContent(sanitized)
            }
            InputType.COMMAND -> {
                sanitized = sanitizeCommandContent(sanitized)
            }
            InputType.URL -> {
                sanitized = sanitizeURL(sanitized)
            }
            else -> { /* Diğer tipler için özel sanitization yok */ }
        }
        
        return sanitized
    }
    
    private fun calculateRiskLevel(input: String, type: InputType): RiskLevel {
        var riskScore = 0
        
        riskScore += when {
            input.length > 500 -> 2
            input.length > 200 -> 1
            else -> 0
        }
        
        val specialCharCount = input.count { !it.isLetterOrDigit() }
        riskScore += when {
            specialCharCount > input.length * 0.5 -> 3
            specialCharCount > input.length * 0.3 -> 2
            specialCharCount > input.length * 0.1 -> 1
            else -> 0
        }
        
        riskScore += when (type) {
            InputType.COMMAND, InputType.SQL -> 2
            InputType.MESSAGE, InputType.URL -> 1
            else -> 0
        }
        
        return when {
            riskScore >= 5 -> RiskLevel.CRITICAL
            riskScore >= 3 -> RiskLevel.HIGH
            riskScore >= 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
    
    private fun containsControlCharacters(input: String): Boolean {
        return input.any { it.isISOControl() && it !in listOf('\t', '\n', '\r') }
    }
    
    private fun containsUnicodeSecurity(input: String): Boolean {
        return input.any { 
            it.category == CharCategory.FORMAT ||
            it.category == CharCategory.PRIVATE_USE ||
            it.category == CharCategory.UNASSIGNED
        }
    }
    
    private fun containsEncodingAttack(input: String): Boolean {
        val encodingPatterns = listOf(
            "%[0-9a-fA-F]{2}".toRegex(),
            "\\\\u[0-9a-fA-F]{4}".toRegex(),
            "\\\\x[0-9a-fA-F]{2}".toRegex()
        )
        
        return encodingPatterns.any { pattern ->
            val matches = pattern.findAll(input).count()
            matches > input.length * 0.05
        }
    }
    
    private fun containsDirectoryTraversal(input: String): Boolean {
        val traversalPatterns = listOf(
            "../", "..\\", "/..", "\\..",
            "%2e%2e%2f", "%2e%2e%5c"
        )
        return traversalPatterns.any { input.lowercase().contains(it) }
    }
    
    private fun isSpamMessage(input: String): Boolean {
        val maxRepeatingChars = input.groupingBy { it }.eachCount().maxByOrNull { it.value }?.value ?: 0
        if (maxRepeatingChars > input.length * 0.6) return true
        
        val upperCaseRatio = input.count { it.isUpperCase() }.toDouble() / input.length
        if (upperCaseRatio > 0.8 && input.length > 10) return true
        
        return false
    }
    
    private fun isMaliciousDomain(domain: String): Boolean {
        val maliciousDomains = listOf(
            "malware.com", "phishing.org", "scam.net",
            "virus.exe", "trojan.download"
        )
        return maliciousDomains.any { domain.lowercase().contains(it) }
    }
    
    private fun isDisposableEmailDomain(domain: String): Boolean {
        val disposableDomains = listOf(
            "10minutemail.com", "temp-mail.org", "guerrillamail.com",
            "mailinator.com", "throwaway.email"
        )
        return disposableDomains.contains(domain.lowercase())
    }
    
    private fun parseJsonSafely(json: String): Boolean {
        var braceCount = 0
        var bracketCount = 0
        var inString = false
        var escaped = false
        
        for (char in json) {
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString -> when (char) {
                    '{' -> braceCount++
                    '}' -> braceCount--
                    '[' -> bracketCount++
                    ']' -> bracketCount--
                }
            }
        }
        
        return braceCount == 0 && bracketCount == 0 && !inString
    }
    
    private fun getJsonNestingDepth(json: String): Int {
        var maxDepth = 0
        var currentDepth = 0
        var inString = false
        var escaped = false
        
        for (char in json) {
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString -> when (char) {
                    '{', '[' -> {
                        currentDepth++
                        maxDepth = maxOf(maxDepth, currentDepth)
                    }
                    '}', ']' -> currentDepth--
                }
            }
        }
        
        return maxDepth
    }
    
    private fun sanitizeMessageContent(message: String): String {
        return message
            .replace(Regex("(https?://[^\\s]+)"), "[URL]")
            .replace(Regex("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\b"), "[CARD]")
            .take(500)
    }
    
    private fun sanitizeCommandContent(command: String): String {
        return command
            .replace(Regex("[;&|`$(){}\\[\\]<>\"']"), "")
            .trim()
    }
    
    private fun sanitizeURL(url: String): String {
        return try {
            val parsed = java.net.URL(url)
            "${parsed.protocol}://${parsed.host}${parsed.path}"
        } catch (e: Exception) {
            url.replace(Regex("[^a-zA-Z0-9:/._-]"), "")
        }
    }
    
    private fun compileSecurityPatterns() {
        try {
            SQL_INJECTION_PATTERNS.forEach { pattern ->
                securityPatterns["sql_$pattern"] = Pattern.compile(pattern)
            }
            
            XSS_PATTERNS.forEach { pattern ->
                securityPatterns["xss_$pattern"] = Pattern.compile(pattern)
            }
            
            COMMAND_INJECTION_PATTERNS.forEach { pattern ->
                securityPatterns["cmd_$pattern"] = Pattern.compile(pattern)
            }
            
            plugin.logger.info("${securityPatterns.size} güvenlik pattern'i derlendi")
            
        } catch (e: Exception) {
            plugin.logger.severe("Security patterns compilation failed: ${e.message}")
        }
    }
    
    private fun generateCacheKey(
        input: String, 
        type: InputType, 
        maxLength: Int, 
        allowEmpty: Boolean, 
        sanitize: Boolean
    ): String {
        val inputHash = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        
        return "${type.name}_${maxLength}_${allowEmpty}_${sanitize}_$inputHash"
    }
    
    private fun cacheAndReturn(key: String, result: ValidationResult): ValidationResult {
        if (inputCache.size < MAX_CACHE_SIZE) {
            inputCache[key] = result
        }
        return result
    }
    
    private fun startCacheCleanup() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            try {
                if (inputCache.size > MAX_CACHE_SIZE * 0.8) {
                    val keysToRemove = inputCache.keys.take(inputCache.size / 4)
                    keysToRemove.forEach { inputCache.remove(it) }
                    plugin.logger.fine("ValidationManager cache temizlendi: ${keysToRemove.size} entry kaldırıldı")
                }
            } catch (e: Exception) {
                plugin.logger.severe("Cache cleanup failed: ${e.message}")
            }
        }, 12000L, 12000L)
    }
    
    fun isValidUsername(username: String): Boolean {
        return validateInput(username, InputType.USERNAME, allowEmpty = false).isValid
    }
    
    fun isValidDiscordId(discordId: String): Boolean {
        return validateInput(discordId, InputType.DISCORD_ID, allowEmpty = false).isValid
    }
    
    fun isValidIPAddress(ip: String): Boolean {
        return validateInput(ip, InputType.IP_ADDRESS, allowEmpty = false).isValid
    }
    
    fun sanitizeMessage(message: String): String {
        return validateInput(message, InputType.MESSAGE, sanitize = true).sanitizedValue ?: message
    }
    
    fun sanitizeCommand(command: String): String {
        return validateInput(command, InputType.COMMAND, sanitize = true).sanitizedValue ?: command
    }
    
    fun validatePlayerAction(player: Player, action: String, context: Map<String, Any> = emptyMap()): ValidationResult {
        try {
            if (!player.hasPermission("discordlite.use")) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Player does not have permission",
                    riskLevel = RiskLevel.MEDIUM,
                    detectedThreats = listOf("PERMISSION_DENIED")
                )
            }
            
            val playerIP = player.address?.address?.hostAddress
            if (playerIP != null && !isValidIPAddress(playerIP)) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Invalid player IP",
                    riskLevel = RiskLevel.HIGH,
                    detectedThreats = listOf("INVALID_IP")
                )
            }
            
            return ValidationResult(isValid = true)
            
        } catch (e: Exception) {
            plugin.logger.severe("Player validation failed for ${player.name}: ${e.message}")
            
            return ValidationResult(
                isValid = false,
                errorMessage = "Player validation failed",
                riskLevel = RiskLevel.HIGH
            )
        }
    }
    
    fun getValidationStats(): Map<String, Any> {
        return mapOf(
            "cache_size" to inputCache.size,
            "compiled_patterns" to securityPatterns.size
        )
    }
}