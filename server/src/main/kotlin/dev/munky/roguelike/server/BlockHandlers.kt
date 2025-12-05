package dev.munky.roguelike.server

import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.network.packet.client.play.ClientUpdateJigsawBlockPacket

fun registerBlockHandlers() {
    register(JigsawBlockHandler)
}

fun register(block: BlockHandler) = MinecraftServer.getBlockManager().registerHandler(block.key) { block }

object JigsawBlockHandler : BlockHandler {
    override fun getKey(): Key = Key.key("minecraft:jigsaw")
    override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
        return true
    }

    init {
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientUpdateJigsawBlockPacket::class.java) { packet, player ->
            val instance = player.instance
            val block = instance.getBlock(packet.location)
            val nbt = block.nbtOrEmpty()
            player.sendMessage("Packet = $packet")
        }
    }
}