package kynux.cloud.discordLite.managers

import kynux.cloud.discordLite.DiscordLite
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import org.bukkit.entity.Player
import org.bukkit.permissions.PermissionAttachment
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PermissionManager(private val plugin: DiscordLite) {
    
    private val permissionAttachments = ConcurrentHashMap<UUID, PermissionAttachment>()
    private val permissionCache = ConcurrentHashMap<UUID, Set<String>>()
    
    fun initialize() {
        plugin.logger.info("PermissionManager başlatılıyor...")
        
        plugin.server.onlinePlayers.forEach { player ->
            loadPlayerPermissions(player)
        }
        
        plugin.logger.info("PermissionManager başarıyla başlatıldı!")
    }
    
    fun shutdown() {
        plugin.logger.info("PermissionManager kapatılıyor...")
        
        permissionAttachments.values.forEach { attachment ->
            try {
                attachment.remove()
            } catch (e: Exception) {
                plugin.logger.warning("Permission attachment temizleme hatası: ${e.message}")
            }
        }
        
        permissionAttachments.clear()
        permissionCache.clear()
        
        plugin.logger.info("PermissionManager kapatıldı!")
    }

    fun loadPlayerPermissions(player: Player) {
        val uuid = player.uniqueId
        
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerData(uuid.toString())
                
                if (playerData != null && !playerData.discordId.isNullOrBlank()) {
                    val discordId = playerData.discordId
                    
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        updatePlayerPermissionsFromDiscord(player, discordId)
                    })
                } else {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        clearPlayerDiscordPermissions(player)
                    })
                }
                
            } catch (e: Exception) {
                plugin.logger.warning("Oyuncu permission yükleme hatası (${player.name}): ${e.message}")
            }
        }
    }

    fun updatePlayerPermissionsFromDiscord(player: Player, discordId: String) {
        try {
            val guild = plugin.discordManager.getGuild()
            if (guild == null) {
                plugin.logger.warning("Discord sunucusu bulunamadı!")
                return
            }
            
            val member = guild.getMemberById(discordId)
            if (member == null) {
                plugin.logger.warning("Discord üyesi bulunamadı: $discordId")
                clearPlayerDiscordPermissions(player)
                return
            }
            
            val discordPermissions = getPermissionsFromDiscordRoles(member)
            setPlayerPermissions(player, discordPermissions)
            
        } catch (e: Exception) {
            plugin.logger.severe("Discord permission güncelleme hatası: ${e.message}")
        }
    }

    private fun getPermissionsFromDiscordRoles(member: Member): Set<String> {
        val permissions = mutableSetOf<String>()
        val roleMapping = plugin.configManager.getPermissionToRoleMapping()
        
        member.roles.forEach { role ->
            roleMapping.forEach { (permission, roleId) ->
                if (role.id == roleId) {
                    permissions.add(permission)
                }
            }
            
            val roleName = role.name.lowercase()
            when {
                roleName.contains("admin") || roleName.contains("yönetici") -> {
                    permissions.addAll(getAdminPermissions())
                }
                roleName.contains("moderator") || roleName.contains("moderatör") -> {
                    permissions.addAll(getModeratorPermissions())
                }
                roleName.contains("staff") || roleName.contains("yetkili") -> {
                    permissions.addAll(getStaffPermissions())
                }
                roleName.contains("vip") -> {
                    permissions.add("discordlite.vip")
                }
                roleName.contains("premium") -> {
                    permissions.add("discordlite.premium")
                }
            }
        }
        
        return permissions
    }

    private fun setPlayerPermissions(player: Player, permissions: Set<String>) {
        val uuid = player.uniqueId
        
        permissionAttachments[uuid]?.remove()
        
        if (permissions.isNotEmpty()) {
            val attachment = player.addAttachment(plugin)
            
            permissions.forEach { permission ->
                attachment.setPermission(permission, true)
            }
            
            permissionAttachments[uuid] = attachment
            permissionCache[uuid] = permissions
            
            plugin.logger.info("${player.name} için ${permissions.size} permission ayarlandı: ${permissions.joinToString(", ")}")
        } else {
            permissionCache.remove(uuid)
        }
    }

    fun clearPlayerDiscordPermissions(player: Player) {
        val uuid = player.uniqueId
        
        permissionAttachments[uuid]?.remove()
        permissionAttachments.remove(uuid)
        permissionCache.remove(uuid)
        
        plugin.logger.info("${player.name} için Discord permission'ları temizlendi")
    }

    fun onPlayerQuit(player: Player) {
        val uuid = player.uniqueId
        
        permissionAttachments[uuid]?.remove()
        permissionAttachments.remove(uuid)
        permissionCache.remove(uuid)
    }

    fun getPlayerPermissions(player: Player): Set<String> {
        return permissionCache[player.uniqueId] ?: emptySet()
    }

    fun hasPermission(player: Player, permission: String): Boolean {
        return player.hasPermission(permission)
    }

    private fun getAdminPermissions(): Set<String> {
        return setOf(
            "discordlite.admin",
            "discordlite.admin.*",
            "discordlite.admin.unlink",
            "discordlite.admin.reset",
            "discordlite.admin.ban",
            "discordlite.admin.unban",
            "discordlite.admin.info",
            "discordlite.admin.logs",
            "discordlite.admin.reload",
            "discordlite.admin.bypass",
            "discordlite.use.*",
            "discordlite.link.*",
            "discordlite.2fa.*"
        )
    }

    private fun getModeratorPermissions(): Set<String> {
        return setOf(
            "discordlite.moderator",
            "discordlite.admin.info",
            "discordlite.admin.logs",
            "discordlite.admin.ban",
            "discordlite.admin.unban",
            "discordlite.use.*",
            "discordlite.link.*",
            "discordlite.2fa.*"
        )
    }

    private fun getStaffPermissions(): Set<String> {
        return setOf(
            "discordlite.staff",
            "discordlite.admin.info",
            "discordlite.admin.logs",
            "discordlite.use.*",
            "discordlite.link.*",
            "discordlite.2fa.*"
        )
    }

    fun updateRoleMappings() {
        plugin.logger.info("Discord rol eşlemeleri güncelleniyor...")
        
        plugin.server.onlinePlayers.forEach { player ->
            loadPlayerPermissions(player)
        }
        
        plugin.logger.info("Discord rol eşlemeleri güncellendi!")
    }

    fun onDiscordRoleUpdate(discordId: String) {
        plugin.databaseManager.executeAsync {
            try {
                val playerData = plugin.databaseManager.provider.getPlayerDataByDiscordId(discordId)
                
                if (playerData != null) {
                    val player = plugin.server.getPlayer(UUID.fromString(playerData.uuid))
                    
                    if (player != null && player.isOnline) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            updatePlayerPermissionsFromDiscord(player, discordId)
                        })
                    }
                }
                
            } catch (e: Exception) {
                plugin.logger.warning("Discord rol güncelleme hatası: ${e.message}")
            }
        }
    }

    fun getPermissionStats(): Map<String, Any> {
        return mapOf(
            "active_attachments" to permissionAttachments.size,
            "cached_permissions" to permissionCache.size,
            "total_permissions" to permissionCache.values.sumOf { it.size }
        )
    }
}