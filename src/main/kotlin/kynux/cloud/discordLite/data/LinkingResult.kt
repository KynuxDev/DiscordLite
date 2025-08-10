package kynux.cloud.discordLite.data

import kynux.cloud.discordLite.database.models.PlayerData

data class LinkingResult(
    val success: Boolean,
    val message: String,
    val playerName: String? = null,
    val data: PlayerData? = null
)