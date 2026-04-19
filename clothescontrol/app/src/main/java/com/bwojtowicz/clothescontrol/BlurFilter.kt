package com.bwojtowicz.clothescontrol

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

class BlurFilter(context: Context) {
    private val rs: RenderScript = RenderScript.create(context)

    fun blur(bitmap: Bitmap, radius: Float): Bitmap {
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        val effectiveRadius = radius.coerceIn(0f, 25f)
        script.setRadius(effectiveRadius)
        script.setInput(input)
        script.forEach(output)
        output.copyTo(bitmap)

        return bitmap
    }

    fun multiPassBlur(bitmap: Bitmap, radius: Float, passes: Int): Bitmap {
        var tempBitmap = bitmap.copy(bitmap.config, true)
        val passRadius = radius / passes

        for (i in 0 until passes) {
            tempBitmap = blur(tempBitmap, passRadius)
        }

        return tempBitmap
    }
}
