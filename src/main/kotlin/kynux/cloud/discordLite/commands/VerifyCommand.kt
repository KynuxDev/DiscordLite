package kynux.cloud.discordLite.commands

import kynux.cloud.discordLite.DiscordLite
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class VerifyCommand(private val plugin: DiscordLite) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.messageManager.getErrorMessage("Bu komut sadece oyuncular tarafından kullanılabilir!"))
            return true
        }
        
        if (!sender.hasPermission("discordlite.verify")) {
            sender.sendMessage(plugin.messageManager.getMessage("no_permission"))
            return true
        }
        
        plugin.linkingManager.startVerification(sender)
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return emptyList()
    }
}