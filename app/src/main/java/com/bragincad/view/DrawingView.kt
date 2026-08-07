package com.bragincad.view

import android.content.Context
import android.graphics.*
import android.view.*
import com.bragincad.model.*
import kotlin.math.*

class DrawingView(context: Context) : View(context) {
    var model: DrawingModel? = null; set(value) { field=value; fitPending=true; invalidate() }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(20,25,31); style=Paint.Style.STROKE; strokeWidth=1.2f }
    private var scale=1f; private var tx=0f; private var ty=0f; private var fitPending=true
    private val detector=ScaleGestureDetector(context, object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(d:ScaleGestureDetector):Boolean { val old=scale; scale=(scale*d.scaleFactor).coerceIn(1e-5f,1e5f); tx=d.focusX-(d.focusX-tx)*scale/old; ty=d.focusY-(d.focusY-ty)*scale/old; invalidate(); return true }
    })
    private var lastX=0f; private var lastY=0f
    override fun onTouchEvent(e:MotionEvent):Boolean { detector.onTouchEvent(e); if(!detector.isInProgress){ when(e.actionMasked){ MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y}; MotionEvent.ACTION_MOVE->{tx+=e.x-lastX;ty+=e.y-lastY;lastX=e.x;lastY=e.y;invalidate()} } }; return true }
    override fun onDraw(c:Canvas){ super.onDraw(c); c.drawColor(Color.rgb(247,247,247)); val m=model?:return; if(fitPending){fit(m);fitPending=false}; c.save(); c.translate(tx,ty); c.scale(scale,-scale); paint.strokeWidth=1.2f/scale.coerceAtLeast(1e-5f); for(p in m.primitives) draw(c,p); c.restore() }
    private fun draw(c:Canvas,p:Primitive)=when(p){
        is Primitive.Line->c.drawLine(p.x1.toFloat(),p.y1.toFloat(),p.x2.toFloat(),p.y2.toFloat(),paint)
        is Primitive.Circle->c.drawCircle(p.cx.toFloat(),p.cy.toFloat(),p.radius.toFloat(),paint)
        is Primitive.Arc->{val r=RectF((p.cx-p.radius).toFloat(),(p.cy-p.radius).toFloat(),(p.cx+p.radius).toFloat(),(p.cy+p.radius).toFloat()); c.drawArc(r,Math.toDegrees(p.start).toFloat(),Math.toDegrees(p.end-p.start).toFloat(),false,paint)}
        is Primitive.Polyline->{val path=Path(); if(p.points.size>=2){path.moveTo(p.points[0].toFloat(),p.points[1].toFloat()); for(i in 2 until p.points.size step 2) path.lineTo(p.points[i].toFloat(),p.points[i+1].toFloat()); if(p.closed)path.close();c.drawPath(path,paint)} else Unit}
        is Primitive.Ellipse->{ val major=hypot(p.ax,p.ay); c.save();c.rotate(Math.toDegrees(atan2(p.ay,p.ax)).toFloat(),p.cx.toFloat(),p.cy.toFloat());val r=RectF((p.cx-major).toFloat(),(p.cy-major*p.ratio).toFloat(),(p.cx+major).toFloat(),(p.cy+major*p.ratio).toFloat());c.drawArc(r,Math.toDegrees(p.start).toFloat(),Math.toDegrees(p.end-p.start).toFloat(),false,paint);c.restore() }
    }
    private fun fit(m:DrawingModel){var minX=Double.POSITIVE_INFINITY;var minY=minX;var maxX=Double.NEGATIVE_INFINITY;var maxY=maxX;fun add(x:Double,y:Double){minX=min(minX,x);minY=min(minY,y);maxX=max(maxX,x);maxY=max(maxY,y)};m.primitives.forEach{p->when(p){is Primitive.Line->{add(p.x1,p.y1);add(p.x2,p.y2)};is Primitive.Circle->{add(p.cx-p.radius,p.cy-p.radius);add(p.cx+p.radius,p.cy+p.radius)};is Primitive.Arc->{add(p.cx-p.radius,p.cy-p.radius);add(p.cx+p.radius,p.cy+p.radius)};is Primitive.Polyline->for(i in p.points.indices step 2)add(p.points[i],p.points[i+1]);is Primitive.Ellipse->{val r=hypot(p.ax,p.ay);add(p.cx-r,p.cy-r);add(p.cx+r,p.cy+r)}}};if(minX.isFinite()){scale=(min((width-40)/(maxX-minX),(height-40)/(maxY-minY))).toFloat().coerceAtLeast(1e-5f);tx=(width-(minX+maxX)*scale).toFloat()/2;ty=(height+(minY+maxY)*scale).toFloat()/2}}
}
