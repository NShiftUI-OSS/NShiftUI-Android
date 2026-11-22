package com.nshiftui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NShiftPluginNode(
    model: NShiftPluginModel,
    parent: NShiftPluginNode? = null,
    val renderId: NShiftRenderID = NShiftRenderID.root,
) {
    // Snapshot state: composables reading these recompose only when this node
    // changes, without the whole tree being keyed or rebuilt.
    var model: NShiftPluginModel by mutableStateOf(model)

    var children: List<NShiftPluginNode> by mutableStateOf(emptyList())
        private set

    var slotChildren: Map<NShiftSlotName, List<NShiftPluginNode>> by mutableStateOf(emptyMap())
        private set

    var parent: NShiftPluginNode? = parent
        private set

    val id: NShiftRenderID
        get() = renderId

    init {
        this.children = model.children.mapIndexed { index, childModel ->
            NShiftPluginNode(
                model = childModel,
                parent = this,
                renderId = renderId.appendingChild(index),
            )
        }
        this.slotChildren = model.slots.mapValues { (name, models) ->
            models.mapIndexed { index, childModel ->
                NShiftPluginNode(
                    model = childModel,
                    parent = this,
                    renderId = renderId.appendingSlot(name, index),
                )
            }
        }
    }

    fun replaceChildren(models: List<NShiftPluginModel>) {
        children = models.mapIndexed { index, childModel ->
            NShiftPluginNode(
                model = childModel,
                parent = this,
                renderId = renderId.appendingChild(index),
            )
        }
    }

    /**
     * Inserts nodes for [models] at [index]. Nodes before [index] keep their
     * identity; nodes after it are rebuilt because render IDs are positional.
     */
    fun insertChildren(
        models: List<NShiftPluginModel>,
        index: Int,
    ): Pair<List<NShiftPluginNode>, List<NShiftPluginNode>> {
        val position = index.coerceIn(0, children.size)
        val kept = children.subList(0, position).toList()
        val removed = children.subList(position, children.size).toList()
        val rebuilt = (models + removed.map { it.model }).mapIndexed { offset, childModel ->
            NShiftPluginNode(
                model = childModel,
                parent = this,
                renderId = renderId.appendingChild(position + offset),
            )
        }
        children = kept + rebuilt
        return removed to rebuilt
    }

    /** Appends nodes for [models] to the slot [name], keeping existing nodes. */
    fun appendSlotChildren(
        name: NShiftSlotName,
        models: List<NShiftPluginModel>,
    ): List<NShiftPluginNode> {
        val existing = slotChildren[name].orEmpty()
        val added = models.mapIndexed { offset, childModel ->
            NShiftPluginNode(
                model = childModel,
                parent = this,
                renderId = renderId.appendingSlot(name, existing.size + offset),
            )
        }
        slotChildren = slotChildren + (name to existing + added)
        return added
    }

    fun replaceSlotChildren(name: NShiftSlotName, models: List<NShiftPluginModel>) {
        val next = slotChildren.toMutableMap()
        next[name] = models.mapIndexed { index, childModel ->
            NShiftPluginNode(
                model = childModel,
                parent = this,
                renderId = renderId.appendingSlot(name, index),
            )
        }
        slotChildren = next
    }

    fun replaceAllSlotChildren(slots: Map<NShiftSlotName, List<NShiftPluginModel>>) {
        slotChildren = slots.mapValues { (name, models) ->
            models.mapIndexed { index, childModel ->
                NShiftPluginNode(
                    model = childModel,
                    parent = this,
                    renderId = renderId.appendingSlot(name, index),
                )
            }
        }
    }
}
