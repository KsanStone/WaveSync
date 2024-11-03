package me.ksanstone.wavesync.wavesync.service

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javafx.collections.FXCollections
import javafx.geometry.Orientation
import javafx.scene.Node
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutLeaf
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.LeafLayoutPreference
import org.springframework.stereotype.Service

@Service
class DragLayoutSerializerService {

    private val gson = Gson()

    fun serializeFull(full: List<AppLayout>): String {
        val elems = JsonArray()
        full.forEach {
            elems.add(JsonObject().apply {
                this.addProperty("id", it.id)
                this.addProperty("windowId", it.windowId)
                this.add("layout", serializeNode(it.layout.layoutRoot))
            })
        }
        return gson.toJson(elems)
    }

    fun deserializeFull(input: String, nodeFactory: NodeFactory): MutableList<Triple<String, String?, DragLayoutNode>> {
        if (input == "") throw IllegalArgumentException("Input cannot be empty")
        val elems = gson.fromJson(input, JsonArray::class.java)
        return elems.map {
            Triple(
                it.asJsonObject.get("id").asString,
                it.asJsonObject.get("windowId")?.asString,
                deserializeNode(it.asJsonObject.get("layout").asJsonObject, nodeFactory)
            )
        }.toMutableList()
    }

    private fun serializeNode(node: DragLayoutNode): JsonElement {
        val children = JsonArray(node.children.size)
        for (child in node.children) {
            children.add(serializeLeaf(child))
        }

        val dividers = JsonArray(node.dividerLocations.size)
        for (divider in node.dividerLocations) {
            dividers.add(divider)
        }

        val obj = JsonObject()
        obj.add("children", children)
        obj.add("dividers", dividers)
        obj.add("orientation", gson.toJsonTree(node.orientation))
        return obj
    }

    private fun serializeLeaf(leaf: DragLayoutLeaf): JsonElement {
        if (leaf.isComponent) {
            val obj = JsonObject()
            obj.addProperty("type", "component")
            obj.addProperty("id", leaf.id)
            return obj
        } else {
            val obj = JsonObject()
            obj.addProperty("type", "node")
            obj.add("data", serializeNode(leaf.node!!))
            return obj
        }
    }

    private fun deserializeNode(obj: JsonObject, nodeFactory: NodeFactory): DragLayoutNode {
        val children = obj.getAsJsonArray("children").map { deserializeLeaf(it.asJsonObject, nodeFactory) }
        val dividers = obj.getAsJsonArray("dividers").map { it.asDouble }
        val orientation = gson.fromJson(obj.get("orientation"), Orientation::class.java)

        return DragLayoutNode(
            "", children = FXCollections.observableList(children.toMutableList()),
            dividerLocations = FXCollections.observableArrayList(dividers), orientation = orientation
        )
    }

    private fun deserializeLeaf(obj: JsonObject, nodeFactory: NodeFactory): DragLayoutLeaf {
        val type = obj.get("type").asString
        if (type == "node") {
            return DragLayoutLeaf(node = deserializeNode(obj.get("data").asJsonObject, nodeFactory))
        } else if (type == "component") {
            val id = obj.get("id").asString
            val product = nodeFactory.createNode(id)!!
            return DragLayoutLeaf(component = product.node, layoutPreference = product.layoutPreference, id = id)
        }
        throw IllegalArgumentException("Invalid leaf type")
    }

    @FunctionalInterface
    fun interface NodeFactory {

        /**
         * Constructs an instance of the javafx [Node] with the given id
         */
        fun createNode(nodeId: String): ProducedNode?
    }

    data class ProducedNode(val node: Node, val layoutPreference: LeafLayoutPreference)

}