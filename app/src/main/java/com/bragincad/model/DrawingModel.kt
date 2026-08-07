package com.bragincad.model

data class Point2(val x: Double, val y: Double)

sealed interface Primitive {
    val layerId: Int
    data class Line(override val layerId:Int,val x1:Double,val y1:Double,val x2:Double,val y2:Double):Primitive
    data class Circle(override val layerId:Int,val cx:Double,val cy:Double,val radius:Double):Primitive
    data class Arc(override val layerId:Int,val cx:Double,val cy:Double,val radius:Double,val start:Double,val end:Double):Primitive
    data class Polyline(override val layerId:Int,val points:DoubleArray,val closed:Boolean):Primitive
    data class Ellipse(override val layerId:Int,val cx:Double,val cy:Double,val ax:Double,val ay:Double,val ratio:Double,val start:Double,val end:Double):Primitive
    data class Hatch(override val layerId:Int,val contours:Array<DoubleArray>,val solid:Boolean,val patternLines:Array<PatternLine>):Primitive
    data class Text(override val layerId:Int,val x:Double,val y:Double,val height:Double,val rotation:Double,val value:String,val horizontal:Int,val vertical:Int,val widthFactor:Double,val multiline:Boolean,val boxWidth:Double=0.0,val lineSpacing:Double=1.0,val styleName:String="Standard",val fontName:String="",val styleWidthFactor:Double=1.0,val oblique:Double=0.0):Primitive
}
data class PatternLine(val px:Double,val py:Double,val dx:Double,val dy:Double,val ox:Double,val oy:Double)

data class Layer(val id:Int,val name:String,val initiallyVisible:Boolean)

data class DrawingStats(
    val totalObjects:Int,
    val graphicalEntities:Int,
    val serviceObjects:Int,
    val structuralEntities:Int,
    val supportedEntities:Int,
    val unsupportedGraphicalEntities:Int,
    val visibleGraphicalEntities:Int,
    val unsupportedVisibleGraphicalEntities:Int,
    val renderPrimitiveCount:Int,
    val byType:Map<String,Int>,
    val unsupportedByType:Map<String,Int>
)

enum class DrawingUnit(val code:Int,val symbol:String,val description:String) {
    UNITLESS(0,"units","Unitless"),INCHES(1,"in","Inches"),FEET(2,"ft","Feet"),MILES(3,"mi","Miles"),
    MILLIMETERS(4,"mm","Millimeters"),CENTIMETERS(5,"cm","Centimeters"),METERS(6,"m","Meters"),KILOMETERS(7,"km","Kilometers"),
    MICROINCHES(8,"µin","Microinches"),MILS(9,"mil","Mils"),YARDS(10,"yd","Yards"),ANGSTROMS(11,"Å","Angstroms"),
    NANOMETERS(12,"nm","Nanometers"),MICRONS(13,"µm","Microns"),DECIMETERS(14,"dm","Decimeters"),DEKAMETERS(15,"dam","Dekameters"),
    HECTOMETERS(16,"hm","Hectometers"),GIGAMETERS(17,"Gm","Gigameters"),ASTRONOMICAL(18,"au","Astronomical units"),
    LIGHT_YEARS(19,"ly","Light years"),PARSECS(20,"pc","Parsecs"),UNKNOWN(-1,"units","Unknown");
    companion object { fun fromCode(code:Int)=entries.firstOrNull{it.code==code}?:UNKNOWN }
}

data class DrawingModel(
    val primitives:List<Primitive>,
    val layers:List<Layer>,
    val version:String,
    val units:DrawingUnit,
    val stats:DrawingStats,
    val parseMs:Long
)
