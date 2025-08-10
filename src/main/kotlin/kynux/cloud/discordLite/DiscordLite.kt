package kynux.cloud.discordLite

import kynux.cloud.discordLite.managers.*
import kynux.cloud.discordLite.commands.DiscordLiteCommand
import kynux.cloud.discordLite.commands.VerifyCommand
import kynux.cloud.discordLite.listeners.PlayerJoinListener
import kynux.cloud.discordLite.listeners.PlayerMovementListener
import org.bukkit.plugin.java.JavaPlugin

class DiscordLite : JavaPlugin() {
    
    companion object {
        lateinit var instance: DiscordLite
            private set
    }
    
    lateinit var configManager: ConfigManager
    lateinit var messageManager: MessageManager
    lateinit var errorManager: ErrorManager
    lateinit var databaseManager: DatabaseManager
    lateinit var discordManager: DiscordManager
    lateinit var securityManager: SecurityManager
    lateinit var twoFAManager: TwoFAManager
    lateinit var permissionManager: PermissionManager
    lateinit var linkingManager: LinkingManager
    lateinit var ipBanManager: IPBanManager
    lateinit var logChannelManager: LogChannelManager
    lateinit var validationManager: ValidationManager

    override fun onEnable() {
        instance = this
        
        logger.info("DiscordLite Plugin başlatılıyor...")
        
        try {
            saveDefaultConfig()
            configManager = ConfigManager(this)
            configManager.loadConfig()
            
            messageManager = MessageManager(this)
            messageManager.initialize()
            
            errorManager = ErrorManager(this)
            errorManager.initialize()
            
            validationManager = ValidationManager(this)
            validationManager.initialize()
            
            databaseManager = DatabaseManager(this)
            databaseManager.initialize()
            
            discordManager = DiscordManager(this)
            discordManager.initialize()
            
            securityManager = SecurityManager(this)
            securityManager.initialize()
            
            ipBanManager = IPBanManager(this)
            ipBanManager.initialize()
            
            logChannelManager = LogChannelManager(this)
            logChannelManager.initialize()
            
            twoFAManager = TwoFAManager(this)
            twoFAManager.initialize()
            
            linkingManager = LinkingManager(this)
            
            permissionManager = PermissionManager(this)
            permissionManager.initialize()
            
            getCommand("discordlite")?.setExecutor(DiscordLiteCommand(this))
            getCommand("verify")?.setExecutor(VerifyCommand(this))
            
            server.pluginManager.registerEvents(PlayerJoinListener(this), this)
            server.pluginManager.registerEvents(PlayerMovementListener(this), this)
            
            logger.info("DiscordLite Plugin başarıyla yüklendi!")
            
        } catch (e: Exception) {
            logger.severe("DiscordLite Plugin yüklenirken hata oluştu: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        logger.info("DiscordLite Plugin kapatılıyor...")
        
        try {
            if (::securityManager.isInitialized) {
                securityManager.shutdown()
            }
            
            if (::ipBanManager.isInitialized) {
                ipBanManager.shutdown()
            }
            
            if (::logChannelManager.isInitialized) {
                logChannelManager.shutdown()
            }
            
            if (::twoFAManager.isInitialized) {
                twoFAManager.shutdown()
            }
            
            if (::permissionManager.isInitialized) {
                permissionManager.shutdown()
            }
            
            if (::linkingManager.isInitialized) {
                linkingManager.shutdown()
            }
            
            if (::discordManager.isInitialized) {
                discordManager.shutdown()
            }
            
            if (::messageManager.isInitialized) {
                messageManager.shutdown()
            }
            
            if (::validationManager.isInitialized) {
                validationManager.shutdown()
            }
            
            if (::errorManager.isInitialized) {
                errorManager.shutdown()
            }
            
            if (::databaseManager.isInitialized) {
                databaseManager.close()
            }
            
            logger.info("DiscordLite Plugin başarıyla kapatıldı!")
            
        } catch (e: Exception) {
            logger.severe("DiscordLite Plugin kapatılırken hata oluştu: ${e.message}")
            e.printStackTrace()
        }
    }
}