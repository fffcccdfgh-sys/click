package com.fffcccdfgh.androidclicker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidManifestOrientationTest {
    @Test
    fun appAllowsDesktopRotationAndResizing() {
        val manifest = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val androidNs = "http://schemas.android.com/apk/res/android"
        val application = manifest.getElementsByTagName("application").item(0)

        assertNotEquals("false", application.attributes.getNamedItemNS(androidNs, "resizeableActivity")?.nodeValue)

        val activities = manifest.getElementsByTagName("activity")
        for (index in 0 until activities.length) {
            val activity = activities.item(index)
            assertNull(activity.attributes.getNamedItemNS(androidNs, "screenOrientation"))
        }
    }
}
