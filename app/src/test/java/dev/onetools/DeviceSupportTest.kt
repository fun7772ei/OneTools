package dev.onetools

import org.junit.Assert.*
import org.junit.Test

class DeviceSupportTest {
    @Test fun manufacturerCheckIsCaseInsensitiveAndDoesNotAssumeMissingManufacturer() {
        for (name in listOf("samsung", "SAMSUNG", "Samsung", " samsung ")) assertTrue(DeviceSupport.isSamsung(name))
        for (name in listOf(null, "", "unknown", "google", "samsung-other", "Galaxy")) assertFalse(DeviceSupport.isSamsung(name))
    }
}
