package com.bragincad

import android.os.Debug
import android.util.Log
import java.io.File

internal object DwgOpenDiagnostics {
    private data class Entry(val stage:String,val time:Long,val javaHeap:Long,val nativeHeap:Long,val pssKb:Long,val detail:String)
    private val entries=ArrayList<Entry>();private var output:File?=null;private var filename="";private var bytes=0L
    @Synchronized fun begin(cacheDir:File,file:File,name:String){output=File(cacheDir,"dwg-open-debug.json");filename=name;bytes=file.length();entries.clear();stage("01_TEMP_READY","temp=${file.name}")}
    @Synchronized fun stage(name:String,detail:String=""){val r=Runtime.getRuntime();entries+=Entry(name,System.currentTimeMillis(),r.totalMemory()-r.freeMemory(),Debug.getNativeHeapAllocatedSize(),Debug.getPss(),detail);Log.i("DWG_OPEN_STAGE","$name $detail");write()}
    @Synchronized fun failure(t:Throwable)=stage("FAILED","${t.javaClass.name}: ${t.message.orEmpty()}")
    private fun esc(s:String)=s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
    private fun write(){val file=output?:return;val stages=entries.joinToString(","){"{\"stage\":\"${esc(it.stage)}\",\"timestampMs\":${it.time},\"javaHeapBytes\":${it.javaHeap},\"nativeHeapBytes\":${it.nativeHeap},\"pssKb\":${it.pssKb},\"detail\":\"${esc(it.detail)}\"}"};file.writeText("{\"filename\":\"${esc(filename)}\",\"bytes\":$bytes,\"stages\":[$stages]}")}
}
