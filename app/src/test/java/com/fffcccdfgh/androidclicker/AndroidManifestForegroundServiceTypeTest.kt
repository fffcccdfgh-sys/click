package com.fffcccdfgh.androidclicker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestForegroundServiceTypeTest {
    @Test
    fun onlyScreenCaptureServiceDeclaresMediaProjectionForegroundType() {
        val manifest = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val androidNs = "http://schemas.android.com/apk/res/android"
        val services = manifest.getElementsByTagName("service")
        val serviceTypesByName = buildMap {
            for (index in 0 until services.length) {
                val service = services.item(index)
                val name = service.attributes.getNamedItemNS(androidNs, "name")?.nodeValue ?: continue
                val foregroundType = service.attributes
                    .getNamedItemNS(androidNs, "foregroundServiceType")
                    ?.nodeValue
                    .orEmpty()
                put(name, foregroundType)
            }
        }

        assertTrue(
            serviceTypesByName.getValue(".ScreenCaptureForegroundService")
                .split('|')
                .contains("mediaProjection")
        )
        listOf(
            ".FloatingControlService",
            ".RunFloatingControlService",
            ".PvzFloatingControlService"
        ).forEach { serviceName ->
            assertFalse(
                "$serviceName must not request mediaProjection foreground service type",
                serviceTypesByName.getValue(serviceName)
                    .split('|')
                    .contains("mediaProjection")
            )
        }
    }
}
