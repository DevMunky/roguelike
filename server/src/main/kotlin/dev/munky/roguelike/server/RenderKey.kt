package dev.munky.roguelike.server

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec

object RenderKey {
    data object Position : RenderContext.Key<Pos>
    data object Vector : RenderContext.Key<Vec>
}