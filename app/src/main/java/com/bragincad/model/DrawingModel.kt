package com.bragincad.model

sealed interface Primitive {
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : Primitive
    data class Circle(val cx: Double, val cy: Double, val radius: Double) : Primitive
    data class Arc(val cx: Double, val cy: Double, val radius: Double, val start: Double, val end: Double) : Primitive
    data class Polyline(val points: DoubleArray, val closed: Boolean) : Primitive
    data class Ellipse(val cx: Double, val cy: Double, val ax: Double, val ay: Double, val ratio: Double, val start: Double, val end: Double) : Primitive
}

data class DrawingModel(
    val primitives: List<Primitive>,
    val version: String,
    val unsupported: Int,
    val parseMs: Long
)

