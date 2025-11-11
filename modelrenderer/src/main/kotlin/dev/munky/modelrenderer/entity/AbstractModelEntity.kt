package dev.munky.modelrenderer.entity

import dev.munky.modelrenderer.ModelRendererPlatform.Companion.platform
import dev.munky.modelrenderer.skeleton.Bone
import dev.munky.modelrenderer.skeleton.Cube
import dev.munky.modelrenderer.skeleton.Model
import dev.munky.modelrenderer.util.Vec3d
import java.util.UUID

abstract class AbstractModelEntity(val model: Model) {
    abstract var position: Vec3d
    var scale: Float = 1f
    val bones = model.bones.map { ModelEntityBone(it.value, this@AbstractModelEntity, null) }

    fun create(x: Double, y: Double, z: Double) {

    }
}

sealed class ModelPartEntity {
    abstract val name: String
    abstract val owner: AbstractModelEntity
    abstract val parent: ModelEntityBone?
    val id: String = (parent?.id ?: "") + name
}

/**
 * Grouping of cubes and bones that move as one.
 */
class ModelEntityBone(
    val bone: Bone,
    override val owner: AbstractModelEntity,
    override val parent: ModelEntityBone?
) : ModelPartEntity() {
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
}

/**
 * The visual part of the model
 */
class CubeEntity(
    val cube: Cube,
    override val owner: AbstractModelEntity,
    override val parent: ModelEntityBone
) : ModelPartEntity() {
    override val name: String = cube.name
    val mc = platform().spawnItemDisplay(owner, id)
}