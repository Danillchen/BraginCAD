package com.bragincad.dwg

import com.bragincad.model.*

class LibreDwgAdapter : DwgReader {
    external fun nativeRead(path:String):Array<Any>
    override fun read(path:String):DrawingModel {
        val started=System.nanoTime();val payload=nativeRead(path)
        val raw=payload[0] as DoubleArray;require(raw.size>=8){"LibreDWG returned no metadata"}
        @Suppress("UNCHECKED_CAST") val names=payload[1] as Array<String>
        val visible=payload[2] as BooleanArray
        @Suppress("UNCHECKED_CAST") val typeLines=payload[3] as Array<String>
        @Suppress("UNCHECKED_CAST") val texts=payload[4] as Array<String>
        val result=ArrayList<Primitive>(raw[1].toInt());var i=8
        while(i<raw.size)when(raw[i++].toInt()){
            1->{val l=raw[i++].toInt();result+=Primitive.Line(l,raw[i++],raw[i++],raw[i++],raw[i++])}
            2->{val l=raw[i++].toInt();result+=Primitive.Circle(l,raw[i++],raw[i++],raw[i++])}
            3->{val l=raw[i++].toInt();result+=Primitive.Arc(l,raw[i++],raw[i++],raw[i++],raw[i++],raw[i++])}
            4->{val l=raw[i++].toInt();val n=raw[i++].toInt();val closed=raw[i++]!=0.0;result+=Primitive.Polyline(l,raw.copyOfRange(i,i+n*2),closed);i+=n*2}
            5->{val l=raw[i++].toInt();result+=Primitive.Ellipse(l,raw[i++],raw[i++],raw[i++],raw[i++],raw[i++],raw[i++],raw[i++])}
            6->{val l=raw[i++].toInt();result+=Primitive.Text(l,raw[i++],raw[i++],raw[i++],raw[i++],texts[raw[i++].toInt()],raw[i++].toInt(),raw[i++].toInt(),raw[i++],false)}
            7->{val l=raw[i++].toInt();result+=Primitive.Text(l,raw[i++],raw[i++],raw[i++],raw[i++],texts[raw[i++].toInt()],raw[i++].toInt(),0,1.0,true,raw[i++],raw[i++])}
            else->error("Unknown native primitive")
        }
        fun stats(prefix:String)=typeLines.filter{it.startsWith(prefix)}.associate{line->val split=line.lastIndexOf('=');line.substring(prefix.length,split) to line.substring(split+1).toInt()}
        val stats=DrawingStats(raw[3].toInt(),raw[4].toInt(),raw[5].toInt(),raw[6].toInt(),raw[1].toInt(),raw[2].toInt(),stats("A|"),stats("U|"))
        return DrawingModel(result,names.mapIndexed{id,name->Layer(id,name,visible.getOrElse(id){true})},"AC${raw[0].toInt()}",DrawingUnit.fromCode(raw[7].toInt()),stats,(System.nanoTime()-started)/1_000_000)
    }
    companion object { init { System.loadLibrary("bragincad") } }
}
