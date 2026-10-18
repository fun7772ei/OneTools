package dev.onetools
import org.junit.Assert.*
import org.junit.Test
class BatteryEstimateTest {
 @Test fun computesFromBatteryOnlyTimeAndDischargeInMah() {
  val summary="Time on battery: 1h 30m 0s 0ms (50%) realtime, 1h 20m uptime\nDischarge: 600 mAh"
  val estimate=BatteryEstimate.calculate(summary,3_000_000.0)
  assertEquals(27_000_000L,estimate.remainingMillis)
  assertEquals("あと約7時間30分",estimate.display())
 }
 @Test fun parsesMillisecondsBeforeMinutesAndRejectsMalformedTime() {
  assertEquals(5_482_380L,BatteryEstimate.parseDuration("1h 31m 22s 380ms"))
  assertNull(BatteryEstimate.parseDuration("unknown"))
  assertNull(BatteryEstimate.parseDuration("99999999999999999999d"))
 }
 @Test fun refusesInsufficientAndUnsupportedMeasurements() {
  assertNull(BatteryEstimate.calculate("Time on battery: 10m 0ms (1%) realtime\nDischarge: 100 mAh",3_000_000.0).remainingMillis)
  assertNull(BatteryEstimate.calculate("Time on battery: 1h 0ms (1%) realtime\nDischarge: 0 mAh",3_000_000.0).remainingMillis)
  assertNull(BatteryEstimate.calculate("Time on battery: 1h 0ms (1%) realtime\nDischarge: 100 mAh",null).remainingMillis)
  assertNull(BatteryEstimate.calculate("Time on battery: 1h 0ms (1%) realtime\nDischarge: 100 mAh",Double.NaN).remainingMillis)
  assertEquals("予測できません",BatteryEstimate.calculate("unsupported",3_000_000.0).display())
 }
 @Test fun doesNotConfuseScreenOffOrOtherDischargeFieldsWithTotals() {
  val raw="Time on battery screen off: 1h 0ms\nScreen off discharge: 50 mAh"
  assertNull(BatteryEstimate.calculate(raw,3_000_000.0).remainingMillis)
 }
}
