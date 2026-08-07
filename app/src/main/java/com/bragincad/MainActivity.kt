package com.bragincad

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.*
import com.bragincad.dwg.LibreDwgAdapter
import com.bragincad.view.DrawingView
import kotlin.concurrent.thread

class MainActivity:Activity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val status=TextView(this).apply{setPadding(20,12,20,12);text="BraginCAD — opening real DWG…"};val view=DrawingView(this);root.addView(status);root.addView(view,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);val file=filesDir.resolve("test_01.dwg");assets.open("test_01.dwg").use{input->file.outputStream().use(input::copyTo)};thread(name="dwg-parser"){try{val model=LibreDwgAdapter().read(file.absolutePath);Log.i("BraginCAD","version=${model.version} primitives=${model.primitives.size} unsupported=${model.unsupported} parseMs=${model.parseMs}");runOnUiThread{status.text="${model.version} • ${model.primitives.size} primitives • ${model.parseMs} ms";view.model=model}}catch(t:Throwable){Log.e("BraginCAD","DWG parse failed",t);runOnUiThread{status.text="DWG error: ${t.message}"}}}}
}
