package com.bragincad.view

import android.graphics.Typeface
import com.bragincad.model.Primitive

internal data class ResolvedFont(val typeface:Typeface,val source:String,val shx:Boolean)

internal object FontResolver {
    fun resolve(text:Primitive.Text):ResolvedFont {
        val file=text.fontName.lowercase()
        val shx=file.endsWith(".shx")
        val typeface=when {
            file.contains("cour") || file.contains("mono") -> Typeface.MONOSPACE
            file.contains("times") || file.contains("roman") -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }
        return ResolvedFont(typeface,if(file.isBlank())"android:sans-serif" else file,shx)
    }
}
