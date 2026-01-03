package dev.munky.roguelike.server.enemy

import dev.munky.roguelike.server.enemy.ai.Ai
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.Dungeon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.network.packet.server.play.DamageEventPacket
import net.minestom.server.network.packet.server.play.SoundEffectPacket

/**
 * Players don't interact with enemies, enemies kill them.
 */
open class Enemy(val data: EnemyData, val source: Source) : EntityCreature(EntityType.INTERACTION) {
    val visualDelegate = data.visual.createEntity()
    val ai = Ai(this, CoroutineScope(Dispatchers.Default + SupervisorJob()))

    init {
        navigator.setNodeGenerator(data.movement.generator)
        navigator.setNodeFollower { data.movement.follower(this@Enemy) }

        for (behavior in data.behaviors) {
            ai.addBehavior(behavior)
        }

        getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.125
    }

    override fun spawn() {
        visualDelegate.setInstance(instance, position)
        if (instance is RogueInstance)
            ai.start(instance as RogueInstance)
    }

    override fun despawn() {
        visualDelegate.remove()
        ai.stop()
    }

    override fun damage(damage: Damage): Boolean {
        if (isDead()) return false
        if (isImmune(damage.type)) return false

        (damage.source as? LivingEntity)?.let {
            ai.interrupt(Ai.ContextKey.TARGET, it)
        }

        val entityDamageEvent = EntityDamageEvent(this, damage, damage.getSound(visualDelegate))
        EventDispatcher.callCancellable(entityDamageEvent) {
            // Set the last damage type since the event is not cancelled
            this.lastDamage = entityDamageEvent.damage

            val remainingDamage = damage.amount

            if (entityDamageEvent.shouldAnimate()) {
                sendPacketToViewersAndSelf(
                    DamageEventPacket(
                        visualDelegate.entityId, damage.typeId,
                        if (damage.attacker == null) 0 else damage.attacker!!
                            .entityId + 1,
                        if (damage.source == null) 0 else damage.source!!
                            .entityId + 1,
                        damage.sourcePosition
                    )
                )
            }

            // Set the final entity health
            setHealth(health - remainingDamage)

            // play damage sound
            val sound = entityDamageEvent.sound
            if (sound != null) {
                sendPacketToViewersAndSelf(
                    SoundEffectPacket(
                        sound,
                        Sound.Source.HOSTILE,
                        position,
                        1.0f,
                        1.0f,
                        0
                    )
                )
            }
        }
        return !entityDamageEvent.isCancelled
    }

    override fun refreshPosition(newPosition: Pos?, ignoreView: Boolean, sendPackets: Boolean) {
        visualDelegate.refreshPosition(newPosition, ignoreView, sendPackets)
    }

    /**
     * Don't tick vanilla ai.
     */
    override fun aiTick(time: Long) {}

    sealed interface Source {
        data class DungeonRoom(val room: Dungeon.Room) : Source
        data object Stylo : Source
        data object WhyNot : Source
    }
}