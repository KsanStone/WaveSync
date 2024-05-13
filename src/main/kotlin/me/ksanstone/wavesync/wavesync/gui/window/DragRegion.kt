package me.ksanstone.wavesync.wavesync.gui.window

import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.Node

class DragRegion(private val base: Node) {
    private val excludedBounds = ArrayList<Node>()

    /**
     * check if any given point is in the specified area
     * (excluded areas are considered)
     * @param p the [Point2D] (screen coordinates)
     * @return true if the point is inside the specified area
     */
    fun contains(p: Point2D): Boolean {
        return this.contains(p.x, p.y)
    }

    /**
     * check if any given point is in the specified area
     * (excluded areas are considered)
     * @param x the x coordinate (in screen coordinates)
     * @param y the y coordinate (in screen coordinates)
     * @return true if the point is inside the specified area
     */
    fun contains(x: Double, y: Double): Boolean {
        // check if point is in an excluded region
        if (excludedBounds.isNotEmpty()) {
            for (node in excludedBounds) {
                // return false if point is in an excluded region
                if (nodeToScreenBounds(node).contains(x, y)) return false
            }
        }
        val baseBounds = nodeToScreenBounds(base)
        // return if point is contained in the specified region
        return baseBounds.maxX > x && baseBounds.minX < x && baseBounds.maxY > y && baseBounds.minY < y
    }

    private fun nodeToScreenBounds(node: Node): Bounds {
        return node.localToScreen(node.boundsInLocal)
    }


    /**
     * adds a node to exclude its area
     * @param node the node to exclude
     */
    fun addExcludeBounds(node: Node): DragRegion {
        excludedBounds.add(node)
        return this
    }
}