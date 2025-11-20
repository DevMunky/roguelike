package dev.munky.modelrenderer.entity

import dev.munky.modelrenderer.ModelPlatform
import dev.munky.modelrenderer.skeleton.Animation
import dev.munky.modelrenderer.skeleton.Bone
import dev.munky.modelrenderer.skeleton.Cube
import dev.munky.modelrenderer.skeleton.Model
import dev.munky.roguelike.common.launch
import dev.munky.roguelike.common.toRadians
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import org.joml.AxisAngle4d
import org.joml.Matrix4d
import org.joml.Matrix4dStack
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
    var scale: Double = 1.0
    val bones = ArrayList<ModelEntityBone>()
    var level : ModelPlatform.Level? = null
    var rootEntity: ModelPlatform.ItemDisplayEntity? = null
    var position: Vector3d = Vector3d(.0)
        set(value) {
            moveAll(value)
            field = value
        }
    private var spawned: Boolean = false

    private fun moveAll(to: Vector3d) {
        if (!spawned) return
        rootEntity?.teleport(to)
        val mat = Matrix4dStack(30) // todo calculate maximum bone depth
        mat.scale(scale)
        mat.rotateX(to.x().toRadians())
        for (bone in bones) {
            bone.applyTransform(mat)
        }
    }

    fun spawn() {
        Dispatchers.Default.launch {
            rootEntity = level!!.spawnItemDisplay(
                model.visibleBox.x(),
                model.visibleBox.y(),
                model.visibleBox.z(),
                "minecraft:air")
            bones.clear()

            val newBones = model.bones.map { ModelEntityBone(it.value, this@ModelEntity, null) }
            newBones.forEach { it.spawn() }
            spawned = true

            bones.addAll(newBones)
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
    data class PlayingAnimation(val animationId: UUID, val frame: Int) : State // Start playing animation : Lerp
    data object StoppingAnimation : State // Reset the state entirely
    class Compound(vararg val states: State) : State
    data object None : State
}

sealed class ModelPartEntity(
    val uuid: UUID,
    val name: String,
    val owner: ModelEntity,
    val parent: ModelEntityBone?,
) {
    val location: String = (parent?.location?.plus("/") ?: "") + name
    val id: String = owner.model.name + ":" + location

    open suspend fun spawn() {}
    open fun despawn() {}

    fun update(state: State): Unit = when(state) {
        is State.None, is State.StoppingAnimation -> applyTransform(Matrix4dStack(30)) // todo calculate remaining bone depth
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
        val stack = Matrix4dStack(30) // todo calculate remaining bone depth
        for (keyframe in keyframes) {
            for (data in keyframe.dataPoints) when (data) {
                is Animation.Animator.KeyFrame.DataPoint.Vec3d -> when (keyframe.channel) {
                    Animation.Animator.KeyFrame.Channel.SCALE -> stack.scale(data.x, data.y, data.z)
                    Animation.Animator.KeyFrame.Channel.POSITION -> stack.translate(data.x, data.y, data.z)
                    Animation.Animator.KeyFrame.Channel.ROTATION -> stack.rotateXYZ(data.x, data.y, data.z)
                    else -> error("Unreachable ($keyframe)")
                }
                is Animation.Animator.KeyFrame.DataPoint.Sound -> {
                    val modelOrigin = owner.position
                    val translate = stack.getTranslation(Vector3d())
                    val soundPos = modelOrigin.add(translate)
                    owner.level?.playSound(soundPos, data.effect)
                }
            }
        }
        applyTransform(stack)
    }

    abstract fun applyTransform(mat: Matrix4dStack)
}

/**
 * Grouping of cubes and bones that move as one.
 */
class ModelEntityBone(
    val bone: Bone,
    owner: ModelEntity,
    parent: ModelEntityBone?
) : ModelPartEntity(bone.uuid, bone.name, owner, parent) {
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

    override suspend fun spawn() {
        for (bone in bones.values) {
            bone.spawn()
        }
        for (cube in cubes.values) {
            cube.spawn()
        }
    }

    fun localTransform() : Matrix4d {
        val mat = Matrix4d()
        if (parent != null) {
            val localOrigin = bone.origin.sub(parent.bone.origin, Vector3d())
            mat.translate(localOrigin)
        }
        mat
            .translate(bone.pivot)
            // Blockbench uses ZYX order for Euler rotations
            .rotateZ(bone.rotation.z().toRadians())
            .rotateY(bone.rotation.y().toRadians())
            .rotateX(bone.rotation.x().toRadians())
            .translate(-bone.pivot.x(), -bone.pivot.y(), -bone.pivot.z())
        return mat
    }

    /**
     * @param mat The parents transforms and the supplemental one.
     */
    override fun applyTransform(mat: Matrix4dStack) {
        mat.pushMatrix().mul(localTransform())
        for (bone in bones.values) {
            bone.applyTransform(mat)
        }
        for (cube in cubes.values) {
            cube.applyTransform(mat)
        }
        mat.popMatrix()
    }
}

/**
 * The visual part of the model
 */
class CubeEntity(
    val cube: Cube,
    owner: ModelEntity,
    parent: ModelEntityBone
) : ModelPartEntity(cube.uuid, cube.name, owner, parent) {

    var entity: ModelPlatform.ItemDisplayEntity? = null
        private set

    override suspend fun spawn() {
        val level = owner.level ?: error("ModelEntity has no level. Call ModelEntity.setLevel(ModelPlatform.Level) first.")
        entity = level.spawnItemDisplay(cube.from.x(),cube.from.y(),cube.from.z(), id)
        entity!!.ride(owner.rootEntity!!)
    }

    fun localTransform() : Matrix4d {
        val mat = Matrix4d()
        // Apply rotation around pivot, then move the cube to its 'from' position
        mat
            .translate(cube.pivot)
            // Blockbench uses ZYX order for Euler rotations
            .rotateZ(cube.rotation?.z()?.toRadians() ?: .0)
            .rotateY(cube.rotation?.y()?.toRadians() ?: .0)
            .rotateX(cube.rotation?.x()?.toRadians() ?: .0)
            .translate(-cube.pivot.x(), -cube.pivot.y(), -cube.pivot.z())
            .translate(cube.from)
        return mat
    }

    override fun applyTransform(mat: Matrix4dStack) {
        // Cubes inherit their parent bone transforms; apply parent first, then local
        val final = Matrix4d(mat).mul(localTransform())
        val translate = final.getTranslation(Vector3d()).mul(0.1)
        val rotate = final.getUnnormalizedRotation(Quaterniond())
        val scale = final.getScale(Vector3d()).mul(0.1)
        val entity = entity ?: error("ItemDisplayEntity not yet spawned. Call ModelEntity.spawn() first (cube '$id').")
        entity.translate(translate)
        entity.scale(scale)
        entity.rotateRightHanded(rotate)
    }
}