package com.bragincad

import android.app.*
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.*
import com.bragincad.dwg.LibreDwgAdapter
import com.bragincad.model.DrawingModel
import com.bragincad.model.DrawingUnit
import com.bragincad.view.DrawingView
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity:Activity(){
    private lateinit var status:TextView;private lateinit var view:DrawingView
    private var model:DrawingModel?=null
    private val prefs by lazy{getSharedPreferences("bragincad",MODE_PRIVATE)}
    private data class Recent(val uri:String,val label:String)

    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        status=TextView(this).apply{setPadding(20,12,20,12);text="BraginCAD — opening regression DWG…"}
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun button(text:String,action:()->Unit)=Button(this).apply{this.text=text;setOnClickListener{action()}}
        actions.addView(button("Open"){openDocument()});actions.addView(button("Recent"){showRecent()});actions.addView(button("Layers"){showLayers()});actions.addView(button("Measure"){startMeasure()});actions.addView(button("Fit"){view.fitToDrawing()});actions.addView(button("Stats"){showStats()})
        val scroll=HorizontalScrollView(this).apply{addView(actions)}
        view=DrawingView(this).apply{
            onFirstMeasurePoint={status.text="Measurement: select second point (endpoint snap active)"}
            onMeasurement={distance,unit,snapped->status.text=formatDistance(distance,unit,snapped)}
        }
        root.addView(status);root.addView(scroll);root.addView(view,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        val asset=filesDir.resolve("test_01.dwg");assets.open("test_01.dwg").use{input->asset.outputStream().use(input::copyTo)};loadPath(asset,"test_01.dwg")
    }

    private fun openDocument(){
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/acad","application/x-acad","application/dwg","image/vnd.dwg","application/octet-stream"))}
        startActivityForResult(intent,OPEN_DWG)
    }

    @Deprecated("Activity result retained to keep the milestone dependency-free")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode!=OPEN_DWG||resultCode!=RESULT_OK)return
        val uri=data?.data?:return;val label=displayName(uri)?:"drawing.dwg"
        if(!label.endsWith(".dwg",true)){Toast.makeText(this,"Select a .dwg file",Toast.LENGTH_LONG).show();return}
        runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        addRecent(uri,label);loadUri(uri,label)
    }

    private fun loadUri(uri:Uri,label:String){
        status.text="Opening $label…";thread(name="dwg-copy-parse"){
            try{val local=File(cacheDir,"opened_${uri.toString().hashCode()}.dwg");contentResolver.openInputStream(uri).use{input->requireNotNull(input){"Cannot open file"};local.outputStream().use(input::copyTo)};parse(local,label)}
            catch(t:Throwable){showError(t)}
        }
    }
    private fun loadPath(file:File,label:String){status.text="Opening $label…";thread(name="dwg-parser"){try{parse(file,label)}catch(t:Throwable){showError(t)}}}
    private fun parse(file:File,label:String){
        val drawing=LibreDwgAdapter().read(file.absolutePath);val unsupported=drawing.stats.unsupportedByType.entries.sortedByDescending{it.value}.joinToString{ "${it.key}=${it.value}" };Log.i("BraginCAD","file=$label version=${drawing.version} objects=${drawing.stats.totalObjects} graphical=${drawing.stats.graphicalEntities} service=${drawing.stats.serviceObjects} structural=${drawing.stats.structuralEntities} supported=${drawing.stats.supportedEntities} unsupportedGraphical=${drawing.stats.unsupportedGraphicalEntities} coverage=${"%.2f".format(Locale.US,100.0*drawing.stats.supportedEntities/drawing.stats.graphicalEntities)}% layers=${drawing.layers.size} units=${drawing.units} parseMs=${drawing.parseMs} unsupportedRanking=[$unsupported]")
        runOnUiThread{model=drawing;view.model=drawing;status.text="$label • ${drawing.primitives.size} primitives • ${drawing.layers.size} layers • ${drawing.units.symbol} • ${drawing.parseMs} ms"}
    }
    private fun showError(t:Throwable){Log.e("BraginCAD","DWG open failed",t);runOnUiThread{status.text="DWG error: ${t.message}"}}

    private fun showLayers(){val drawing=model?:return;val names=drawing.layers.map{it.name}.toTypedArray();val checked=BooleanArray(names.size){view.isLayerVisible(it)};AlertDialog.Builder(this).setTitle("Layers (${names.size})").setMultiChoiceItems(names,checked){_,which,isChecked->view.setLayerVisible(which,isChecked)}.setPositiveButton("Done",null).show()}
    private fun startMeasure(){if(model==null)return;view.startMeasurement();status.text="Measurement: select first point (endpoint snap active)"}
    private fun formatDistance(value:Double,unit:DrawingUnit,snapped:Boolean):String{val suffix=if(snapped)" • endpoint snapped" else "";return if(unit==DrawingUnit.UNITLESS||unit==DrawingUnit.UNKNOWN)"Distance: ${"%.4f".format(Locale.US,value)} drawing units (unit unspecified)$suffix" else "Distance: ${"%.4f".format(Locale.US,value)} ${unit.symbol}$suffix"}

    private fun showStats(){val s=model?.stats?:return;val coverage=100.0*s.supportedEntities/s.graphicalEntities;val unsupported=s.unsupportedByType.entries.sortedByDescending{it.value}.joinToString("\n"){"${it.key}: ${it.value}"};AlertDialog.Builder(this).setTitle("DWG statistics").setMessage("Objects: ${s.totalObjects}\nGraphical entities: ${s.graphicalEntities}\nService objects: ${s.serviceObjects}\nStructural entities: ${s.structuralEntities}\nSupported/rendered: ${s.supportedEntities}\nUnsupported graphical: ${s.unsupportedGraphicalEntities}\nGraphical coverage: ${"%.2f".format(Locale.US,coverage)}%\n\nUnsupported ranking:\n$unsupported").setPositiveButton("Close",null).show()}

    private fun displayName(uri:Uri):String?{var cursor:Cursor?=null;return try{cursor=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null);if(cursor?.moveToFirst()==true)cursor.getString(0)else null}finally{cursor?.close()}}
    private fun recent():MutableList<Recent> = prefs.getString("recent","").orEmpty().split('\u001e').filter{it.contains('\u001f')}.map{val p=it.split('\u001f',limit=2);Recent(p[0],p[1])}.toMutableList()
    private fun addRecent(uri:Uri,label:String){val items=recent();items.removeAll{it.uri==uri.toString()};items.add(0,Recent(uri.toString(),label));prefs.edit().putString("recent",items.take(10).joinToString("\u001e"){"${it.uri}\u001f${it.label}"}).apply()}
    private fun showRecent(){val items=recent();if(items.isEmpty()){Toast.makeText(this,"No recent files",Toast.LENGTH_SHORT).show();return};AlertDialog.Builder(this).setTitle("Recent files").setItems(items.map{it.label}.toTypedArray()){_,which->val item=items[which];loadUri(Uri.parse(item.uri),item.label)}.setNegativeButton("Cancel",null).show()}

    companion object{private const val OPEN_DWG=1001}
}
