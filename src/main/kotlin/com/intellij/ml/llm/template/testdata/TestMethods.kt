/*
 * File serves the purpose of testing the plugin.
 */
package com.intellij.ml.llm.template.testdata

fun floydWarshall(graph: Array<IntArray>): Array<IntArray> {
    val n = graph.size
    val dist = Array(n) { i -> graph[i].clone() }

    for (k in 0 until n) {
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (dist[i][k] != Int.MAX_VALUE && dist[k][j] != Int.MAX_VALUE
                    && dist[i][k] + dist[k][j] < dist[i][j]
                ) {
                    dist[i][j] = dist[i][k] + dist[k][j]
                }
            }
        }
    }

    return dist
}

class CentroidDecomposition(val graph: List<List<Int>>) {
    private val size = graph.size
    private val subSize = IntArray(size)
    private val isCentroid = BooleanArray(size)
    private var root = 0

    init {
        decompose(0, -1)
    }

    private fun decompose(node: Int, parent: Int) {
        subSize[node] = 1
        var centroid = -1
        for (child in graph[node]) {
            if (child != parent) {
                decompose(child, node)
                subSize[node] += subSize[child]
                if (centroid == -1 || subSize[child] > subSize[centroid]) {
                    centroid = child
                }
            }
        }
        if (size - subSize[node] > 0 && size - subSize[node] < subSize[centroid]) {
            centroid = parent
        }
        if (centroid != -1) {
            isCentroid[centroid] = true
            if (parent == -1) {
                root = centroid
            }
        }
    }

    fun getCentroids(): List<Int> {
        val centroids = mutableListOf<Int>()
        for (node in 0 until size) {
            if (isCentroid[node]) {
                centroids.add(node)
            }
        }
        return centroids
    }
}
