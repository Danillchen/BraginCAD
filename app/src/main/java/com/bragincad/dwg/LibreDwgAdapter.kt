package com.bragincad.dwg

import com.bragincad.model.*

class LibreDwgAdapter : DwgReader {
    external fun nativeRead(path: String): DoubleArray
    override fun read(path: String): DrawingModel {
        val started = System.nanoTime()
        val raw = nativeRead(path)
        require(raw.size >= 3) { "LibreDWG returned no metadata" }
        val result = ArrayList<Primitive>(raw[1].toInt())
        var i = 3
        while (i < raw.size) when (raw[i++].toInt()) {
            1 -> result += Primitive.Line(raw[i++], raw[i++], raw[i++], raw[i++])
            2 -> result += Primitive.Circle(raw[i++], raw[i++], raw[i++])
            3 -> result += Primitive.Arc(raw[i++], raw[i++], raw[i++], raw[i++], raw[i++])
            4 -> { val n=raw[i++].toInt(); val closed=raw[i++] != 0.0; result += Primitive.Polyline(raw.copyOfRange(i, i+n*2), closed); i += n*2 }
            5 -> result += Primitive.Ellipse(raw[i++], raw[i++], raw[i++], raw[i++], raw[i++], raw[i++], raw[i++])
            else -> error("Unknown native primitive")
        }
        return DrawingModel(result, "AC${raw[0].toInt()}", raw[2].toInt(), (System.nanoTime()-started)/1_000_000)
    }
    companion object { init { System.loadLibrary("bragincad") } }
}

