package dev.onetools
import org.junit.Assert.*
import org.junit.Test
class BatterySnapshotTest {
 @Test fun parsesCurrentStateWithoutHistoricalPollution() {
  val b = BatterySnapshot.parse("""Current Battery Service state:
  USB powered: true
  level: 83
  scale: 100
  temperature: 332
  voltage: 4316
  health: 2
  status: 2
  mSavedBatteryAsoc: [98]
[EventLogBuffer]
  level: 4
  temperature: 999
""")
  assertEquals(83,b.percent); assertEquals(332.0,b.number("temperature")!!,0.0)
  assertEquals("USB",b.sources); assertEquals("良好",b.health); assertEquals("[98]",b.value("mSavedBatteryAsoc"))
 }
 @Test fun missingOrInvalidValuesRemainUnknown() {
  assertNull(BatterySnapshot.parse("level: 5").percent)
  assertNull(BatterySnapshot.parse("level: 5\nscale: 0").percent)
  assertNull(BatterySnapshot.parse("level: 101\nscale: 100").percent)
  assertNull(BatterySnapshot.shutter("null")); assertNull(BatterySnapshot.shutter("2"))
  assertEquals(false,BatterySnapshot.shutter("0\n")); assertEquals(true,BatterySnapshot.shutter("1"))
 }
 @Test fun preservesUnknownVendorFieldsAndEnums() {
  val b = BatterySnapshot.parse("health: 99\nstatus: 88\nnew field: abc")
  assertEquals("不明",b.health); assertEquals("不明",b.status); assertEquals("abc",b.value("new field"))
 }
}
