package dev.onetools

import android.os.Bundle
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs only these fixed operations in the Shizuku UserService process. No shell interpolation. */
class ToolService : IOneToolsService.Stub() {
    private val readers = Executors.newCachedThreadPool()
    private fun command(vararg args: String): Bundle {
        val process = ProcessBuilder(*args).redirectErrorStream(true).start()
        val output = readers.submit<String> { process.inputStream.bufferedReader().use { it.readText().take(100_000) } }
        return try {
            check(process.waitFor(8, TimeUnit.SECONDS)) { "処理がタイムアウトしました" }
            Bundle().apply { putInt("exit", process.exitValue()); putString("output", output.get(2, TimeUnit.SECONDS)); putString("command", args.joinToString(" ")) }
        } finally { process.destroyForcibly(); output.cancel(true) }
    }
    override fun battery() = command("/system/bin/dumpsys", "battery")
    // Stable IHealth AIDL transaction 12 = getHealthInfo, a read-only getter.
    // Older HIDL-only devices fail independently; never probe other transaction numbers.
    override fun batteryHalInfo() = command("/system/bin/service", "call", "android.hardware.health.IHealth/default", "12")
    override fun batteryProperty(propertyId: Int): Bundle {
        // AOSP BatteryManager: 7 = manufacture date, 8 = first use, 10 = SOH.
        // Whitelist getters; never expose arbitrary service transactions or property IDs.
        require(propertyId in setOf(7, 8, 10))
        return command("/system/bin/service", "call", "batteryproperties", "1", "i32", propertyId.toString())
    }
    override fun dischargeStats(): Bundle {
        val result = command("/system/bin/dumpsys", "batterystats", "--charged")
        // Return only aggregate discharge data, never per-app usage or history.
        if(result.getInt("exit", -1) == 0) {
            result.putString("output", result.getString("output").orEmpty().lineSequence()
                .map { it.trim() }.filter { it.startsWith("Time on battery:") || it.startsWith("Discharge:") }
                .joinToString("\n"))
        }
        return result
    }
    override fun shutter(): Bundle {
        check(DeviceSupport.isSamsung(android.os.Build.MANUFACTURER)) { "この機能はGalaxy向けです" }
        return command("/system/bin/settings", "--user", "current", "get", "system", KEY)
    }
    override fun setShutter(value: Int): Bundle {
        check(DeviceSupport.isSamsung(android.os.Build.MANUFACTURER)) { "この機能はGalaxy向けです" }
        require(value == 0 || value == 1)
        return command("/system/bin/settings", "--user", "current", "put", "system", KEY, value.toString())
    }
    override fun uid() = android.os.Process.myUid()
    override fun destroy() { readers.shutdownNow(); kotlin.system.exitProcess(0) }
    companion object { const val KEY = "csc_pref_camera_forced_shuttersound_key" }
}
