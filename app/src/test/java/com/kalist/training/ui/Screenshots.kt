package com.kalist.training.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import java.io.File

/** Saves a PNG of the current screen to build/screenshots (for reviewing layouts without a device). */
fun ComposeTestRule.screenshot(name: String) {
    val dir = System.getProperty("screenshotDir") ?: return
    waitForIdle()
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    File(dir).mkdirs()
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
