package app.litterguard.core.domain.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Small image operations on row-major grids. Pure functions, no allocation beyond their result. */
internal object GridOps {
    /** Gradient magnitude `|dx| + |dy|` using central differences; edge cells reuse their own value. */
    fun gradient(
        values: IntArray,
        width: Int,
        height: Int,
    ): IntArray =
        IntArray(values.size) { i ->
            val x = i % width
            val y = i / width
            val dx = values[y * width + min(x + 1, width - 1)] - values[y * width + max(x - 1, 0)]
            val dy = values[min(y + 1, height - 1) * width + x] - values[max(y - 1, 0) * width + x]
            abs(dx) + abs(dy)
        }

    /** Morphological opening then closing with a 3×3 square: removes speckles, then fills small holes. */
    fun openThenClose(
        mask: BooleanArray,
        width: Int,
        height: Int,
    ): BooleanArray {
        val opened = dilate(erode(mask, width, height), width, height)
        return erode(dilate(opened, width, height), width, height)
    }

    private fun erode(
        mask: BooleanArray,
        width: Int,
        height: Int,
    ): BooleanArray = BooleanArray(mask.size) { i -> mask[i] && neighbourhoodAll(mask, i, width, height) }

    private fun dilate(
        mask: BooleanArray,
        width: Int,
        height: Int,
    ): BooleanArray = BooleanArray(mask.size) { i -> mask[i] || neighbourhoodAny(mask, i, width, height) }

    // Cells outside the grid are ignored, so shapes touching the border are not eroded away.
    private fun neighbourhoodAll(
        mask: BooleanArray,
        index: Int,
        width: Int,
        height: Int,
    ): Boolean {
        forEachNeighbour(index, width, height) { if (!mask[it]) return false }
        return true
    }

    private fun neighbourhoodAny(
        mask: BooleanArray,
        index: Int,
        width: Int,
        height: Int,
    ): Boolean {
        forEachNeighbour(index, width, height) { if (mask[it]) return true }
        return false
    }

    private inline fun forEachNeighbour(
        index: Int,
        width: Int,
        height: Int,
        action: (Int) -> Unit,
    ) {
        val x = index % width
        val y = index / width
        for (ny in max(0, y - 1)..min(height - 1, y + 1)) {
            for (nx in max(0, x - 1)..min(width - 1, x + 1)) {
                action(ny * width + nx)
            }
        }
    }

    /** The cells on both sides of a component's outline: its outermost cells and their neighbours outside it. */
    fun border(
        component: Component,
        width: Int,
        height: Int,
    ): Set<Int> {
        val inside = component.cells.toHashSet()
        val result = mutableSetOf<Int>()
        for (cell in component.cells) {
            forEachNeighbour(cell, width, height) { next ->
                if (next !in inside) {
                    result += next
                    result += cell
                }
            }
        }
        return result
    }

    /** A connected region of set cells (8-connectivity). */
    class Component(
        val cells: IntArray,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
    )

    fun components(
        mask: BooleanArray,
        width: Int,
        height: Int,
    ): List<Component> {
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val result = mutableListOf<Component>()
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            val cells = mutableListOf<Int>()
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            while (top > 0) {
                val cell = stack[--top]
                cells += cell
                minX = min(minX, cell % width)
                maxX = max(maxX, cell % width)
                minY = min(minY, cell / width)
                maxY = max(maxY, cell / width)
                forEachNeighbour(cell, width, height) { next ->
                    if (mask[next] && !seen[next]) {
                        seen[next] = true
                        stack[top++] = next
                    }
                }
            }
            result += Component(cells.toIntArray(), minX, minY, maxX, maxY)
        }
        return result
    }
}
