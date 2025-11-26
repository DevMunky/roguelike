package dev.munky.roguelike.server.instance

import dev.munky.roguelike.server.instance.town.TownInstance

class InstanceManager {
    val town by lazy { TownInstance.create() }
}