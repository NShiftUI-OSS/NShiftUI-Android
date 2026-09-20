package com.nshiftui

class NShiftPluginNode(
    model: NShiftPluginModel,
    parent: NShiftPluginNode? = null,
    val renderId: NShiftRenderID = NShiftRenderID.root,
) {
    var model: NShiftPluginModel = model

    var children: List<NShiftPluginNode> = emptyList()
        private set

    var slotChildren: Map<NShiftSlotName, List<NShiftPluginNode>> = emptyMap()
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
