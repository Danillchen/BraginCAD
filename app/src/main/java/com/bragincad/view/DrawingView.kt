package com.bragincad.view

import android.content.Context
import android.graphics.*
import android.view.*
import com.bragincad.model.*
import kotlin.math.*

class DrawingView(context:Context):View(context) {
    var model:DrawingModel?=null;set(value){field=value;visibleLayers=BooleanArray(value?.layers?.size?:0){i->value?.layers?.get(i)?.initiallyVisible?:true};fitPending=true;measureStart=null;measureEnd=null;invalidate()}
    var onMeasurement:((distance:Double,unit:DrawingUnit,snapped:Boolean)->Unit)?=null
    var onFirstMeasurePoint:(()->Unit)?=null
    private var visibleLayers=BooleanArray(0)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(20,25,31);style=Paint.Style.STROKE;strokeWidth=1.2f}
    private val measurePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(211,47,47);style=Paint.Style.STROKE;strokeWidth=3f}
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(20,25,31);style=Paint.Style.FILL;typeface=Typeface.SANS_SERIF}
    private var scale=1f;private var tx=0f;private var ty=0f;private var fitPending=true
    private var measuring=false;private var measureStart:Point2?=null;private var measureEnd:Point2?=null;private var anySnapped=false
    private var lastX=0f;private var lastY=0f;private var moved=false
    private val detector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(d:ScaleGestureDetector):Boolean{val old=scale;scale=(scale*d.scaleFactor).coerceIn(1e-5f,1e5f);tx=d.focusX-(d.focusX-tx)*scale/old;ty=d.focusY-(d.focusY-ty)*scale/old;invalidate();return true}
    })

    fun setLayerVisible(id:Int,visible:Boolean){if(id in visibleLayers.indices){visibleLayers[id]=visible;invalidate()}}
    fun isLayerVisible(id:Int)=visibleLayers.getOrElse(id){true}
    fun startMeasurement(){measuring=true;measureStart=null;measureEnd=null;anySnapped=false;invalidate()}
    fun cancelMeasurement(){measuring=false;measureStart=null;measureEnd=null;invalidate()}
    fun fitToDrawing(){fitPending=true;invalidate()}

    override fun onTouchEvent(e:MotionEvent):Boolean{
        detector.onTouchEvent(e);if(detector.isInProgress)return true
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y;moved=false}
            MotionEvent.ACTION_MOVE->{val dx=e.x-lastX;val dy=e.y-lastY;if(abs(dx)+abs(dy)>3)moved=true;if(!measuring){tx+=dx;ty+=dy;invalidate()};lastX=e.x;lastY=e.y}
            MotionEvent.ACTION_UP->if(measuring&&!moved)selectMeasurePoint(e.x,e.y)
        };return true
    }

    private fun selectMeasurePoint(screenX:Float,screenY:Float){
        val raw=Point2(((screenX-tx)/scale).toDouble(),((ty-screenY)/scale).toDouble());val snapped=snapEndpoint(raw,(24f*resources.displayMetrics.density/scale).toDouble());val point=snapped?:raw
        anySnapped=anySnapped||snapped!=null
        if(measureStart==null){measureStart=point;onFirstMeasurePoint?.invoke()}else{measureEnd=point;val a=measureStart!!;onMeasurement?.invoke(hypot(point.x-a.x,point.y-a.y),model?.units?:DrawingUnit.UNKNOWN,anySnapped);measuring=false};invalidate()
    }

    private fun snapEndpoint(target:Point2,tolerance:Double):Point2?{
        val m=model?:return null;var best:Point2?=null;var bestD=tolerance*tolerance
        fun candidate(x:Double,y:Double){val d=(x-target.x).pow(2)+(y-target.y).pow(2);if(d<=bestD){bestD=d;best=Point2(x,y)}}
        for(p in m.primitives){if(!isLayerVisible(p.layerId))continue;when(p){
            is Primitive.Line->{candidate(p.x1,p.y1);candidate(p.x2,p.y2)}
            is Primitive.Polyline->{for(i in p.points.indices step 2)candidate(p.points[i],p.points[i+1])}
            is Primitive.Arc->{candidate(p.cx+p.radius*cos(p.start),p.cy+p.radius*sin(p.start));candidate(p.cx+p.radius*cos(p.end),p.cy+p.radius*sin(p.end))}
            is Primitive.Ellipse->{fun ep(a:Double){val major=hypot(p.ax,p.ay);val angle=atan2(p.ay,p.ax);val lx=major*cos(a);val ly=major*p.ratio*sin(a);candidate(p.cx+lx*cos(angle)-ly*sin(angle),p.cy+lx*sin(angle)+ly*cos(angle))};ep(p.start);ep(p.end)}
            is Primitive.Circle->Unit
            is Primitive.Text->Unit
        }};return best
    }

    override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.rgb(247,247,247));val m=model?:return;if(fitPending){fit(m);fitPending=false};c.save();c.translate(tx,ty);c.scale(scale,-scale);paint.strokeWidth=1.2f/scale.coerceAtLeast(1e-5f);for(p in m.primitives)if(isLayerVisible(p.layerId)&&p !is Primitive.Text)draw(c,p);c.restore();drawTexts(c,m);drawMeasurement(c)}
    private fun drawTexts(c:Canvas,m:DrawingModel){for(p in m.primitives){if(p !is Primitive.Text||!isLayerVisible(p.layerId)||p.height<=0.0)continue;val size=(p.height*scale).toFloat();if(size<0.35f)continue;textPaint.textSize=size;textPaint.textScaleX=p.widthFactor.toFloat().coerceIn(0.1f,10f);val lines=(if(p.multiline)cleanMtext(p.value)else p.value).split('\n');val horizontal=if(p.multiline)(p.horizontal-1).mod(3)else p.horizontal;val vertical=if(p.multiline)(p.horizontal-1)/3 else p.vertical;c.save();c.translate((tx+p.x*scale).toFloat(),(ty-p.y*scale).toFloat());c.rotate(-Math.toDegrees(p.rotation).toFloat());for((index,line)in lines.withIndex()){val width=textPaint.measureText(line);val x=when(horizontal){1->-width/2;2->-width;else->0f};val blockHeight=lines.size*size*1.2f*p.lineSpacing.toFloat();val origin=when(vertical){0->size;1->size+blockHeight/2;2->size+blockHeight;3->0f;else->0f};c.drawText(line,x,origin+index*size*1.2f*p.lineSpacing.toFloat(),textPaint)};c.restore()};textPaint.textScaleX=1f}
    private fun cleanMtext(raw:String):String{return raw.replace("\\P","\n").replace("\\~"," ").replace("%%d","°",true).replace(Regex("""\\S([^;]+);""")){it.groupValues[1].replace('#','/').replace('^','/')}.replace(Regex("""\\[ACFHQTW][^;]*;"""),"").replace(Regex("""\\[LlOoKk]"""),"").replace("{","").replace("}","")}
    private fun drawMeasurement(c:Canvas){val a=measureStart?:return;val b=measureEnd;val ax=(tx+a.x*scale).toFloat();val ay=(ty-a.y*scale).toFloat();c.drawCircle(ax,ay,8f,measurePaint);if(b!=null){val bx=(tx+b.x*scale).toFloat();val by=(ty-b.y*scale).toFloat();c.drawLine(ax,ay,bx,by,measurePaint);c.drawCircle(bx,by,8f,measurePaint)}}
    private fun draw(c:Canvas,p:Primitive)=when(p){
        is Primitive.Line->c.drawLine(p.x1.toFloat(),p.y1.toFloat(),p.x2.toFloat(),p.y2.toFloat(),paint)
        is Primitive.Circle->c.drawCircle(p.cx.toFloat(),p.cy.toFloat(),p.radius.toFloat(),paint)
        is Primitive.Arc->{val r=RectF((p.cx-p.radius).toFloat(),(p.cy-p.radius).toFloat(),(p.cx+p.radius).toFloat(),(p.cy+p.radius).toFloat());c.drawArc(r,Math.toDegrees(p.start).toFloat(),Math.toDegrees(p.end-p.start).toFloat(),false,paint)}
        is Primitive.Polyline->{val path=Path();if(p.points.size>=2){path.moveTo(p.points[0].toFloat(),p.points[1].toFloat());for(i in 2 until p.points.size step 2)path.lineTo(p.points[i].toFloat(),p.points[i+1].toFloat());if(p.closed)path.close();c.drawPath(path,paint)}else Unit}
        is Primitive.Ellipse->{val major=hypot(p.ax,p.ay);c.save();c.rotate(Math.toDegrees(atan2(p.ay,p.ax)).toFloat(),p.cx.toFloat(),p.cy.toFloat());val r=RectF((p.cx-major).toFloat(),(p.cy-major*p.ratio).toFloat(),(p.cx+major).toFloat(),(p.cy+major*p.ratio).toFloat());c.drawArc(r,Math.toDegrees(p.start).toFloat(),Math.toDegrees(p.end-p.start).toFloat(),false,paint);c.restore()}
        is Primitive.Text->Unit
    }
    private fun fit(m:DrawingModel){var minX=Double.POSITIVE_INFINITY;var minY=minX;var maxX=Double.NEGATIVE_INFINITY;var maxY=maxX;fun add(x:Double,y:Double){minX=min(minX,x);minY=min(minY,y);maxX=max(maxX,x);maxY=max(maxY,y)};m.primitives.forEach{p->when(p){is Primitive.Line->{add(p.x1,p.y1);add(p.x2,p.y2)};is Primitive.Circle->{add(p.cx-p.radius,p.cy-p.radius);add(p.cx+p.radius,p.cy+p.radius)};is Primitive.Arc->{add(p.cx-p.radius,p.cy-p.radius);add(p.cx+p.radius,p.cy+p.radius)};is Primitive.Polyline->for(i in p.points.indices step 2)add(p.points[i],p.points[i+1]);is Primitive.Ellipse->{val r=hypot(p.ax,p.ay);add(p.cx-r,p.cy-r);add(p.cx+r,p.cy+r)};is Primitive.Text->{add(p.x,p.y);add(p.x+max(p.boxWidth,p.height),p.y+p.height)}}};if(minX.isFinite()&&maxX>minX&&maxY>minY){scale=min((width-40)/(maxX-minX),(height-40)/(maxY-minY)).toFloat().coerceAtLeast(1e-5f);tx=(width-(minX+maxX)*scale).toFloat()/2;ty=(height+(minY+maxY)*scale).toFloat()/2}}
}
