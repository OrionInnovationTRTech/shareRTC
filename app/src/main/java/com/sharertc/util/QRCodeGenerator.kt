package com.sharertc.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

fun generateQRCode(text: String, width: Int = 900, height: Int = 900): Bitmap {
    val multiFormatWriter = MultiFormatWriter()
    val bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, width, height)

    // BitMatrix'i Bitmap'e dönüştürme
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(
                x,
                y,
                if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
        }
    }
    return bitmap
}