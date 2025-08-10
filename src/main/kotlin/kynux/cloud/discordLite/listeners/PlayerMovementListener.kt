package kynux.cloud.discordLite.listeners

import kynux.cloud.discordLite.DiscordLite
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent

class PlayerMovementListener(private val plugin: DiscordLite) : Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        
        if (plugin.twoFAManager.isPlayerFrozen(player)) {
            val from = event.from
            val to = event.to
            
            if (to != null && (from.x != to.x || from.y != to.y || from.z != to.z)) {
                event.setTo(from)
                plugin.twoFAManager.handlePlayerMovement(player)
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        
        if (plugin.twoFAManager.isPlayerFrozen(player)) {
            val command = event.message.lowercase()
            val allowedCommands = listOf("/help", "/discordlite")
            
            val isAllowed = allowedCommands.any { command.startsWith(it) }
            
            if (!isAllowed) {
                event.isCancelled = true
                player.sendMessage("§c🔒 2FA doğrulaması sırasında komut kullanılamaz!")
                player.sendMessage("§cDiscord'dan onay bekleniyor...")
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        
        if (plugin.twoFAManager.isPlayerFrozen(player)) {
            event.isCancelled = true
            player.sendMessage("§c🔒 2FA doğrulaması sırasında sohbet edilemez!")
            player.sendMessage("§cDiscord'dan onay bekleniyor...")
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        
        if (plugin.twoFAManager.isPlayerFrozen(player)) {
            event.isCancelled = true
            player.sendMessage("§c🔒 2FA doğrulaması sırasında blok kırılamaz!")
            player.sendMessage("§cDiscord'dan onay bekleniyor...")
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        
        if (plugin.twoFAManager.isPlayerFrozen(player)) {
            event.isCancelled = true
            player.sendMessage("§c🔒 2FA doğrulaması sırasında blok yerleştirilemez!")
            player.sendMessage("§cDiscord'dan onay bekleniyor...")
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        
        if (plugin.twoFAManager.isPlayerFrozen(player)) {
            event.isCancelled = true
            player.sendMessage("§c🔒 2FA doğrulaması bekleniyor...")
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.damager is org.bukkit.entity.Player) {
            val player = event.damager as org.bukkit.entity.Player
            
            if (plugin.twoFAManager.isPlayerFrozen(player)) {
                event.isCancelled = true
                player.sendMessage("§c🔒 2FA doğrulaması sırasında saldırı yapılamaz!")
                player.sendMessage("§cDiscord'dan onay bekleniyor...")
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked is org.bukkit.entity.Player) {
            val player = event.whoClicked as org.bukkit.entity.Player
            
            if (plugin.twoFAManager.isPlayerFrozen(player)) {
                event.isCancelled = true
                player.sendMessage("§c🔒 2FA doğrulaması bekleniyor...")
            }
        }
    }
}