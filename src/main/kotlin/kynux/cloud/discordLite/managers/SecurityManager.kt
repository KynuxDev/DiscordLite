package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import kynux.cloud.discordLite.database.models.SecurityEventType
import kynux.cloud.discordLite.database.models.SecurityLog
import net.dv8tion.jda.api.EmbedBuilder
import org.bukkit.entity.Player
import java.awt.Color
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SecurityManager(private val plugin: DiscordLite) {
    
    private val threatLevel = ConcurrentHashMap<String, ThreatLevel>()
    private val suspiciousActivity = ConcurrentHashMap<String, MutableList<SuspiciousEvent>>()
    private val securityMetrics = SecurityMetrics()
    
    enum class ThreatLevel(val level: Int, val color: Color, val displayName: String) {
        LOW(1, Color.GREEN, "Düşük"),
        MEDIUM(2, Color.YELLOW, "Orta"),
        HIGH(3, Color.ORANGE, "Yüksek"),
        CRITICAL(4, Color.RED, "Kritik")
    }
    
    data class SuspiciousEvent(
        val eventType: String,
        val description: String,
        val severity: Int,
        val timestamp: LocalDateTime,
        val playerIP: String? = null,
        val additionalData: Map<String, Any> = emptyMap()
    )
    
    data class SecurityMetrics(
        val totalEvents: AtomicInteger = AtomicInteger(0),
        val criticalEvents: AtomicInteger = AtomicInteger(0),
        val blockedAttempts: AtomicInteger = AtomicInteger(0),
        val activeBans: AtomicInteger = AtomicInteger(0)
    )
    
    fun initialize() {
        plugin.logger.info("SecurityManager başlatılıyor...")
        
        threatLevel.clear()
        suspiciousActivity.clear()
        
        performInitialSecurityScan()
        
        startPeriodicSecurityCheck()
        
        plugin.logger.info("SecurityManager başarıyla başlatıldı!")
    }
    
    fun shutdown() {
        plugin.logger.info("SecurityManager kapatılıyor...")
        
        generateFinalSecurityReport()
        
        threatLevel.clear()
        suspiciousActivity.clear()
        
        plugin.logger.info("SecurityManager kapatıldı!")
    }

    fun logEvent(eventType: String, playerName: String?, description: String, ipAddress: String? = null, severity: Int = 1) {
        try {
            val securityLog = SecurityLog.create(
                eventType = getSecurityEventType(eventType),
                description = description,
                playerUUID = getPlayerUUID(playerName),
                ipAddress = ipAddress,
                details = "Severity: $severity"
            )
            
            plugin.databaseManager.executeAsync {
                try {
                    plugin.databaseManager.provider.saveSecurityLog(securityLog)
                } catch (e: Exception) {
                    plugin.logger.severe("Güvenlik log kaydetme hatası: ${e.message}")
                }
            }
            
            securityMetrics.totalEvents.incrementAndGet()
            if (severity >= 3) {
                securityMetrics.criticalEvents.incrementAndGet()
            }
            
            when (severity) {
                1, 2 -> plugin.logChannelManager.logSecurity("🔒 Güvenlik Olayı", description)
                3 -> plugin.logChannelManager.logSecurity("⚠️ Yüksek Risk", description)
                4, 5 -> plugin.logChannelManager.logEmergency("Kritik Güvenlik Olayı", description)
            }
            
            if (ipAddress != null && severity >= 2) {
                trackSuspiciousActivity(ipAddress, eventType, description, severity)
            }
            
            plugin.logger.info("Güvenlik olayı loglandı: $eventType - $description")
            
        } catch (e: Exception) {
            plugin.logger.severe("Güvenlik olay loglama hatası: ${e.message}")
        }
    }

    private fun trackSuspiciousActivity(ipAddress: String, eventType: String, description: String, severity: Int) {
        val suspiciousEvent = SuspiciousEvent(
            eventType = eventType,
            description = description,
            severity = severity,
            timestamp = LocalDateTime.now(),
            playerIP = ipAddress
        )
        
        suspiciousActivity.computeIfAbsent(ipAddress) { mutableListOf() }.add(suspiciousEvent)
        
        val recentEvents = suspiciousActivity[ipAddress]?.filter {
            java.time.Duration.between(it.timestamp, LocalDateTime.now()).toMinutes() <= 60 
        } ?: emptyList()
        
        val riskScore = calculateRiskScore(recentEvents)
        updateThreatLevel(ipAddress, riskScore)
        
        if (riskScore >= 80) {
            takeAutomaticSecurityMeasures(ipAddress, riskScore)
        }
    }

    private fun calculateRiskScore(events: List<SuspiciousEvent>): Int {
        if (events.isEmpty()) return 0
        
        var score = 0
        
        score += events.size * 10
        
        score += events.sumOf { it.severity * 15 }
        
        val uniqueTypes = events.map { it.eventType }.distinct().size
        score += uniqueTypes * 5
        
        val timeSpan = if (events.size > 1) {
            java.time.Duration.between(events.first().timestamp, events.last().timestamp).toMinutes()
        } else 1
        
        if (timeSpan <= 5) score += 30
        
        return minOf(score, 100)
    }

    private fun updateThreatLevel(ipAddress: String, riskScore: Int) {
        val newThreatLevel = when {
            riskScore >= 80 -> ThreatLevel.CRITICAL
            riskScore >= 60 -> ThreatLevel.HIGH
            riskScore >= 30 -> ThreatLevel.MEDIUM
            else -> ThreatLevel.LOW
        }
        
        val oldLevel = threatLevel[ipAddress]
        if (oldLevel != newThreatLevel) {
            threatLevel[ipAddress] = newThreatLevel
            
            plugin.logChannelManager.logSecurity(
                "🎯 Tehdit Seviyesi Değişikliği",
                "**IP:** `$ipAddress`\n**Eski Seviye:** ${oldLevel?.displayName ?: "Yok"}\n**Yeni Seviye:** ${newThreatLevel.displayName}\n**Risk Skoru:** $riskScore"
            )
            
            plugin.logger.warning("Tehdit seviyesi değişti: $ipAddress -> ${newThreatLevel.displayName} (Skor: $riskScore)")
        }
    }

    private fun takeAutomaticSecurityMeasures(ipAddress: String, riskScore: Int) {
        plugin.logger.warning("Otomatik güvenlik önlemleri alınıyor: $ipAddress (Risk: $riskScore)")
        
        val banDuration = when {
            riskScore >= 90 -> 3600 * 24
            riskScore >= 80 -> 3600
            else -> 1800
        }
        
        val success = plugin.ipBanManager.banIP(
            ipAddress = ipAddress,
            reason = "Otomatik güvenlik önlemi - Risk skoru: $riskScore",
            bannedBy = "DiscordLite-AutoSecurity",
            durationSeconds = banDuration
        )
        
        if (success) {
            securityMetrics.blockedAttempts.incrementAndGet()
            plugin.logChannelManager.logEmergency(
                "🛡️ Otomatik Güvenlik Önlemi",
                "Yüksek risk tespit edildi ve otomatik önlemler alındı",
                "**IP:** `$ipAddress`\n**Risk Skoru:** $riskScore\n**Alınan Önlem:** IP Ban ($banDuration saniye)\n**Zaman:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}"
            )
        }
    }

    fun generateSecurityReport(startDate: LocalDateTime, endDate: LocalDateTime): SecurityReport {
        return plugin.databaseManager.executeSync {
            try {
                val logs = plugin.databaseManager.provider.getSecurityLogsByDateRange(startDate, endDate) ?: emptyList()

                val eventsByType = logs.groupBy { it.eventType }
                val criticalEvents = logs.filter { it.eventType == SecurityEventType.FAILED_LOGIN || it.eventType == SecurityEventType.SYSTEM_ERROR }
                val ipAnalysis = analyzeIPPatterns(logs)

                SecurityReport(
                    startDate = startDate,
                    endDate = endDate,
                    totalEvents = logs.size,
                    criticalEvents = criticalEvents.size,
                    eventsByType = eventsByType.mapValues { it.value.size },
                    topRiskyIPs = ipAnalysis.take(10),
                    currentThreatLevels = threatLevel.toMap(),
                    recommendations = generateSecurityRecommendations(logs)
                )

            } catch (e: Exception) {
                plugin.logger.severe("Güvenlik raporu oluşturma hatası: ${e.message}")
                null
            }
        } ?: SecurityReport.empty()
    }

    private fun analyzeIPPatterns(logs: List<SecurityLog>): List<IPAnalysis> {
        return logs.filter { !it.ipAddress.isNullOrBlank() }
            .groupBy { it.ipAddress!! }
            .map { (ip, ipLogs) ->
                IPAnalysis(
                    ipAddress = ip,
                    eventCount = ipLogs.size,
                    riskScore = calculateIPRiskScore(ipLogs),
                    lastActivity = ipLogs.maxOfOrNull { it.timestamp } ?: LocalDateTime.now(),
                    eventTypes = ipLogs.map { it.eventType }.distinct()
                )
            }
            .sortedByDescending { it.riskScore }
    }

    private fun calculateIPRiskScore(logs: List<SecurityLog>): Int {
        var score = 0
        
        score += logs.size * 5
        
        score += logs.map { it.eventType }.distinct().size * 10
        
        score += logs.count { it.eventType == SecurityEventType.FAILED_LOGIN } * 15
        
        val timeSpan = if (logs.size > 1) {
            java.time.Duration.between(logs.first().timestamp, logs.last().timestamp).toHours()
        } else 1
        
        if (timeSpan <= 1) score += 25
        
        return minOf(score, 100)
    }

    private fun generateSecurityRecommendations(logs: List<SecurityLog>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val failedLogins = logs.count { it.eventType == SecurityEventType.FAILED_LOGIN }
        if (failedLogins > 50) {
            recommendations.add("Yüksek sayıda başarısız giriş denemesi tespit edildi. 2FA zorunluluğu düşünülebilir.")
        }
        
        val uniqueIPs = logs.mapNotNull { it.ipAddress }.distinct().size
        if (uniqueIPs > 100) {
            recommendations.add("Çok sayıda farklı IP'den aktivite tespit edildi. IP whitelist kullanımı düşünülebilir.")
        }
        
        val recentEvents = logs.filter { 
            java.time.Duration.between(it.timestamp, LocalDateTime.now()).toHours() <= 24 
        }
        if (recentEvents.size > logs.size * 0.8) {
            recommendations.add("Güvenlik olaylarının çoğu son 24 saat içinde gerçekleşmiş. Sistem yakından izlenmeli.")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Güvenlik durumu normal görünüyor. Mevcut güvenlik önlemleri sürdürülebilir.")
        }
        
        return recommendations
    }

    private fun performInitialSecurityScan() {
        plugin.logger.info("Başlangıç güvenlik taraması yapılıyor...")
        
        plugin.databaseManager.executeAsync {
            try {
                val recentLogs = plugin.databaseManager.provider.getSecurityLogsByDateRange(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now()
                )
                
                val ipPatterns = analyzeIPPatterns(recentLogs)
                ipPatterns.forEach { analysis ->
                    if (analysis.riskScore >= 50) {
                        threatLevel[analysis.ipAddress] = when {
                            analysis.riskScore >= 80 -> ThreatLevel.CRITICAL
                            analysis.riskScore >= 60 -> ThreatLevel.HIGH
                            else -> ThreatLevel.MEDIUM
                        }
                    }
                }
                
                plugin.logger.info("Güvenlik taraması tamamlandı: ${ipPatterns.size} IP analiz edildi")
                
            } catch (e: Exception) {
                plugin.logger.warning("Başlangıç güvenlik taraması hatası: ${e.message}")
            }
        }
    }

    private fun startPeriodicSecurityCheck() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            try {
                cleanupOldSuspiciousActivity()
                
                updateActiveBanCount()
                
                checkWeeklyReport()
                
            } catch (e: Exception) {
                plugin.logger.warning("Periyodik güvenlik kontrolü hatası: ${e.message}")
            }
        }, 6000L, 6000L) // Her 5 dakika
    }

    private fun cleanupOldSuspiciousActivity() {
        val cutoffTime = LocalDateTime.now().minusHours(24)
        
        suspiciousActivity.values.forEach { events ->
            events.removeIf { it.timestamp.isBefore(cutoffTime) }
        }
        
        suspiciousActivity.entries.removeIf { it.value.isEmpty() }
    }

    private fun updateActiveBanCount() {
        val activeBans = plugin.ipBanManager.listActiveBans().size
        securityMetrics.activeBans.set(activeBans)
    }

    private fun checkWeeklyReport() {
        val now = LocalDateTime.now()
        if (now.dayOfWeek == java.time.DayOfWeek.MONDAY && now.hour == 9) {
            generateWeeklyReport()
        }
    }

    private fun generateWeeklyReport() {
        val endDate = LocalDateTime.now()
        val startDate = endDate.minusDays(7)
        
        val report = generateSecurityReport(startDate, endDate)
        sendWeeklyReportToDiscord(report)
    }

    private fun sendWeeklyReportToDiscord(report: SecurityReport) {
        val embed = EmbedBuilder().apply {
            setTitle("📊 Haftalık Güvenlik Raporu")
            setDescription("${report.startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} - ${report.endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
            
            addField("📈 Toplam Olay", report.totalEvents.toString(), true)
            addField("🚨 Kritik Olay", report.criticalEvents.toString(), true)
            addField("🛡️ Engellenen Deneme", securityMetrics.blockedAttempts.get().toString(), true)
            
            addField("🎯 En Riskli IP'ler", 
                report.topRiskyIPs.take(5).joinToString("\n") { 
                    "`${it.ipAddress}` (Risk: ${it.riskScore})" 
                }, false)
            
            addField("💡 Öneriler", 
                report.recommendations.joinToString("\n") { "• $it" }, false)
            
            setColor(Color.BLUE)
            setTimestamp(java.time.Instant.now())
            setFooter("DiscordLite Security System", null)
        }.build()
        
        plugin.logChannelManager.logSystem("Haftalık Güvenlik Raporu", "Otomatik güvenlik raporu oluşturuldu")
        
        plugin.discordManager.getLogChannel()?.sendMessageEmbeds(embed)?.queue()
    }

    private fun generateFinalSecurityReport() {
        plugin.logChannelManager.logSystem(
            "🔒 SecurityManager Kapatıldı",
            "**Toplam İşlenen Olay:** ${securityMetrics.totalEvents.get()}\n**Kritik Olay:** ${securityMetrics.criticalEvents.get()}\n**Engellenen Deneme:** ${securityMetrics.blockedAttempts.get()}\n**Aktif Tehdit:** ${threatLevel.size}"
        )
    }

    private fun saveSecurityLogToDB(securityLog: SecurityLog) {
        plugin.databaseManager.saveSecurityLog(securityLog)
    }
    
    private fun getSecurityLogsByDateRangeFromDB(startDate: LocalDateTime, endDate: LocalDateTime): List<SecurityLog> {
        return plugin.databaseManager.getSecurityLogsByDateRange(startDate, endDate)
    }

    private fun getSecurityEventType(eventType: String): SecurityEventType {
        return when (eventType.uppercase()) {
            "LOGIN", "SUCCESSFUL_LOGIN" -> SecurityEventType.LOGIN
            "FAILED_LOGIN", "LOGIN_FAILED" -> SecurityEventType.FAILED_LOGIN
            "ACCOUNT_LINKED", "LINK" -> SecurityEventType.ACCOUNT_LINKED
            "ACCOUNT_UNLINKED", "UNLINK" -> SecurityEventType.ACCOUNT_UNLINKED
            "IP_BAN", "BAN" -> SecurityEventType.IP_BAN
            "IP_UNBAN", "UNBAN" -> SecurityEventType.IP_UNBAN
            "SUSPICIOUS_ACTIVITY", "SUSPICIOUS" -> SecurityEventType.SUSPICIOUS_ACTIVITY
            "BRUTE_FORCE_ATTEMPT", "BRUTE_FORCE" -> SecurityEventType.BRUTE_FORCE_ATTEMPT
            "RATE_LIMIT_EXCEEDED", "RATE_LIMIT" -> SecurityEventType.RATE_LIMIT_EXCEEDED
            "ADMIN_ACTION", "ADMIN" -> SecurityEventType.ADMIN_ACTION
            "PERMISSION_CHANGE", "PERMISSION" -> SecurityEventType.PERMISSION_CHANGE
            "ROLE_SYNC", "ROLE" -> SecurityEventType.ROLE_SYNC
            "CONFIG_CHANGE", "CONFIG" -> SecurityEventType.CONFIG_CHANGE
            "UNAUTHORIZED_ACCESS", "UNAUTHORIZED" -> SecurityEventType.UNAUTHORIZED_ACCESS
            "DATA_BREACH_ATTEMPT", "DATA_BREACH" -> SecurityEventType.DATA_BREACH_ATTEMPT
            "INVALID_TOKEN", "TOKEN" -> SecurityEventType.INVALID_TOKEN
            "DISCORD_ERROR", "DISCORD" -> SecurityEventType.DISCORD_ERROR
            "DATABASE_ERROR", "DATABASE" -> SecurityEventType.DATABASE_ERROR
            "PLUGIN_RELOAD", "RELOAD" -> SecurityEventType.PLUGIN_RELOAD
            "PLUGIN_SHUTDOWN", "SHUTDOWN" -> SecurityEventType.PLUGIN_SHUTDOWN
            else -> SecurityEventType.SYSTEM_ERROR
        }
    }
    
    private fun getPlayerUUID(playerName: String?): String? {
        if (playerName == null) return null
        return plugin.server.getPlayer(playerName)?.uniqueId?.toString()
    }

    fun getSecurityStats(): Map<String, Any> {
        return mapOf(
            "total_events" to securityMetrics.totalEvents.get(),
            "critical_events" to securityMetrics.criticalEvents.get(),
            "blocked_attempts" to securityMetrics.blockedAttempts.get(),
            "active_bans" to securityMetrics.activeBans.get(),
            "active_threats" to threatLevel.size,
            "suspicious_ips" to suspiciousActivity.size
        )
    }
    
    data class SecurityReport(
        val startDate: LocalDateTime,
        val endDate: LocalDateTime,
        val totalEvents: Int,
        val criticalEvents: Int,
        val eventsByType: Map<SecurityEventType, Int>,
        val topRiskyIPs: List<IPAnalysis>,
        val currentThreatLevels: Map<String, ThreatLevel>,
        val recommendations: List<String>
    ) {
        companion object {
            fun empty() = SecurityReport(
                LocalDateTime.now(), LocalDateTime.now(), 0, 0, 
                emptyMap(), emptyList(), emptyMap(), emptyList()
            )
        }
    }
    
    data class IPAnalysis(
        val ipAddress: String,
        val eventCount: Int,
        val riskScore: Int,
        val lastActivity: LocalDateTime,
        val eventTypes: List<SecurityEventType>
    )
}