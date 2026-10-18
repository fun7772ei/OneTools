package dev.onetools
import org.junit.Assert.*
import org.junit.Test
class BatteryReferenceTest {
 @Test fun formatsOnlySingleValuesAndValidDates() {
  assertEquals("2025年2月19日", referenceValue("battery FirstUseDate", "[20250219]"))
  assertTrue(referenceValue("battery FirstUseDate", "[20250230]").startsWith("日付を確認できません"))
  assertEquals("98%", referenceValue("mSavedBatteryAsoc", "[98]"))
  assertEquals("[98, 97]", referenceValue("mSavedBatteryAsoc", "[98, 97]"))
 }
 @Test fun referencesKeepOriginalKeysAndDoNotInventUnits() {
  val rows = BatterySnapshot(mapOf("mSavedBatteryAsoc" to "[98]", "mSavedBatteryUsage" to "[72096]")).references()
  assertEquals(listOf("98%", "72096"), rows.map { it.value })
  assertEquals("mSavedBatteryAsoc", rows.first().key)
  assertEquals("充電校正の参考（ASOC）", rows.first().title)
 }
}
