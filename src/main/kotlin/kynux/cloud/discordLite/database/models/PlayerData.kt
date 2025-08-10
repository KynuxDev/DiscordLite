package kynux.cloud.discordLite.database.models

import java.time.LocalDateTime

data class PlayerData(
    val uuid: String,
    val discordId: String?,
    val minecraftUsername: String,
    val twoFAEnabled: Boolean = false,
    val linkedAt: LocalDateTime?,
    val lastLogin: LocalDateTime?
) {
    companion object {
        fun empty(uuid: String, username: String): PlayerData {
            return PlayerData(
                uuid = uuid,
                discordId = null,
                minecraftUsername = username,
                twoFAEnabled = false,
                linkedAt = null,
                lastLogin = null
            )
        }
    }
    
    fun isLinked(): Boolean = discordId != null
    
    fun withDiscordId(discordId: String): PlayerData {
        return copy(
            discordId = discordId,
            linkedAt = LocalDateTime.now()
        )
    }
    
    fun withLastLogin(): PlayerData {
        return copy(lastLogin = LocalDateTime.now())
    }
    
    fun withTwoFA(enabled: Boolean): PlayerData {
        return copy(twoFAEnabled = enabled)
    }
    
    fun unlink(): PlayerData {
        return copy(
            discordId = null,
            twoFAEnabled = false,
            linkedAt = null
        )
    }
}