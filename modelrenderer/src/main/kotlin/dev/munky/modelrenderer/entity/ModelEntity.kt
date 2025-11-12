package dev.munky.modelrenderer.entity

import dev.munky.modelrenderer.ModelPlatform
import dev.munky.modelrenderer.skeleton.Animation
import dev.munky.modelrenderer.skeleton.Bone
import dev.munky.modelrenderer.skeleton.Cube
import dev.munky.modelrenderer.skeleton.Model
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID
import kotlin.collections.filter
import kotlin.time.Duration.Companion.milliseconds

class ModelEntity(
    val model: Model
) {
    var state: State = State.None
        private set
    var scale: Float = 1f
    val bones = model.bones.map { ModelEntityBone(it.value, this@ModelEntity, null) }
    var level : ModelPlatform.Level? = null
    var position: Vector3d = Vector3d(.0)
        set(value) {
            moveAll(value)
            field = value
        }

    private fun moveAll(to: Vector3d) {
        val mat = Matrix4d().translation(to)
        for (bone in bones) {
            bone.applyTransform(mat)
        }
    }

    fun setLevel(level: ModelPlatform.Level) {
        this.level = level
    }

    fun spawn() {
        for (bone in bones) {
            bone.spawn()
            moveAll(position)
        }
    }

    fun despawn() {
        for (bone in bones) {
            bone.despawn()
        }
    }
}

/**
 * State machine for a model in-game.
 */
sealed interface State {
    /**
     * Represents a currently playing animation. [frame] is the current
     * frame in ticks that should be rendered during the tick this state is set.
     */
    data class PlayingAnimation(val animationId: UUID, val frame: Int) : State // Start playing animation : Lerping
    data object StoppingAnimation : State // Reset state entirely
    class Compound(vararg val states: State) : State
    data object None : State
}

sealed class ModelPartEntity {
    abstract val uuid: UUID
    abstract val name: String
    abstract val owner: ModelEntity
    abstract val parent: ModelEntityBone?
    val id: String = (parent?.id ?: "") + name

    open fun spawn() {}
    open fun despawn() {}

    fun update(state: State): Unit = when(state) {
        is State.None, is State.StoppingAnimation -> applyTransform(Matrix4d())
        is State.Compound -> for (s in state.states) update(s)
        is State.PlayingAnimation -> when (this) {
            is ModelEntityBone -> {
                val anim = owner.model.animations[state.animationId]!! // should be valid when setting state machine
                playAnimation(anim, state.frame)
            }
            else -> {}
        }
    }

    fun playAnimation(animation: Animation, frame: Int) {
        // No animator for this model part.
        val animator = animation.animators[uuid] ?: return
        val lastFrame = frame.coerceAtLeast(1) - 1
        val minTime = animation.length * ((lastFrame * 50.0).milliseconds / animation.length)
        val maxTime = animation.length * ((frame * 50.0).milliseconds / animation.length)
        val keyframes = animator.keyframes.filter { it.time in minTime..maxTime }
        val finalMat = Matrix4d()
        for (keyframe in keyframes) {
            for (data in keyframe.dataPoints) when (data) {
                is Animation.Animator.KeyFrame.DataPoint.Vec3d -> when (keyframe.channel) {
                    Animation.Animator.KeyFrame.Channel.SCALE -> finalMat.scale(data.x, data.y, data.z)
                    Animation.Animator.KeyFrame.Channel.POSITION -> finalMat.translate(data.x, data.y, data.z)
                    Animation.Animator.KeyFrame.Channel.ROTATION -> finalMat.rotateXYZ(data.x, data.y, data.z)
                    else -> error("Unreachable ($keyframe)")
                }
                is Animation.Animator.KeyFrame.DataPoint.Sound -> {
                    val modelOrigin = owner.position
                    val translate = finalMat.getTranslation(Vector3d())
                    val soundPos = modelOrigin.add(translate)
                    owner.level.playSound(soundPos, data.effect)
                }
            }
        }
        applyTransform(finalMat)
    }

    abstract fun applyTransform(mat: Matrix4dc)
}

/**
 * Grouping of cubes and bones that move as one.
 */
class ModelEntityBone(
    val bone: Bone,
    override val owner: ModelEntity,
    override val parent: ModelEntityBone?
) : ModelPartEntity() {
    override val uuid: UUID = bone.uuid
    override val name: String = bone.name
    val bones: Map<UUID, ModelEntityBone>
    val cubes: Map<UUID, CubeEntity>

    init {
        val bones = HashMap<UUID, ModelEntityBone>()
        val cubes = HashMap<UUID, CubeEntity>()
        for ((uuid, element) in bone.children) when (element) {
            is Bone -> bones[uuid] = ModelEntityBone(element, owner, this)
            is Cube -> cubes[uuid] = CubeEntity(element, owner, this)
        }
        this.bones = bones
        this.cubes = cubes
    }

    override fun spawn() {
        for (bone in bones.values) {
            bone.spawn()
        }
        for (cube in cubes.values) {
            cube.spawn()
        }
    }

    override fun applyTransform(mat: Matrix4dc) {
        for (bone in bones.values) {
            bone.applyTransform(mat)
        }
        for (cube in cubes.values) {
            cube.applyTransform(mat)
        }
    }
}

/**
 * The visual part of the model
 */
class CubeEntity(
    val cube: Cube,
    override val owner: ModelEntity,
    override val parent: ModelEntityBone
) : ModelPartEntity() {
    override val uuid: UUID = cube.uuid
    override val name: String = cube.name

    var entity: ModelPlatform.ItemDisplayEntity? = null
        private set

    override fun spawn() {
        val level = owner.level ?: error("ModelEntity has no level. Call ModelEntity.setLevel(ModelPlatform.Level) first.")
        entity = level.spawnItemDisplay(cube.from.x,cube.from.y,cube.from.z, id)
    }

    override fun applyTransform(mat: Matrix4dc) {
        val final = cube.transform.mul(mat, Matrix4d())
        val translate = final.getTranslation(Vector3d())
        val rotate = final.getUnnormalizedRotation(Quaterniond())
        val scale = final.getScale(Vector3d())
        val entity = entity ?: error("ItemDisplayEntity not yet spawned. Call ModelEntity.spawn() first.")
        entity.move(translate)
        entity.scale(scale)
        entity.rotateRightHanded(rotate)
    }
}