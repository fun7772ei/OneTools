package dev.onetools

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject
import java.util.Locale

/** Exact model lookup only. Heading aliases are not verified device Build.MODEL values. */
internal class GalaxyNotifyResolver(json: String) {
    private val devices = JSONObject(json).getJSONObject("devices")

    data class Channel(val carrier: String, val url: String?)
    data class Device(
        val model: String,
        val name: String,
        val automaticUrl: String?,
        val requiresChannel: Boolean,
        val channels: List<Channel>,
        val historyWarning: String?,
        val status: String
    )

    fun resolve(model: String): Device? {
        val key = normalize(model)
        val entry = devices.optJSONObject(key) ?: return null
        val requiresChannel = entry.getString("resolution") == "sales_channel_required"
        val candidates = entry.getJSONArray("candidates")
        val options = entry.optJSONArray("sales_channel_options") ?: candidates
        val channels = (0 until options.length()).map { index ->
            val option = options.getJSONObject(index)
            val carrier = option.getString("carrier")
            // Confirmed links must also be present in candidates, the authoritative URL mapping.
            val candidate = (0 until candidates.length()).map { candidates.getJSONObject(it) }
                .firstOrNull { it.getString("carrier") == carrier }
            Channel(carrier, if(option.optString("status") == "url_unconfirmed") null
                else candidate?.nullableString("url"))
        }
        return Device(key, entry.getString("device_name"),
            if(entry.getString("resolution") == "single_url") entry.nullableString("default_url") else null,
            requiresChannel, channels, entry.nullableString("history_warning"), entry.getString("status"))
    }

    companion object {
        fun normalize(model: String): String = model.trim().uppercase(Locale.ROOT)
        fun load(context: Context) = context.assets.open("galaxy_notify_lookup.json")
            .bufferedReader(Charsets.UTF_8).use { GalaxyNotifyResolver(it.readText()) }
    }
}

private fun JSONObject.nullableString(key: String): String? =
    if(!has(key) || isNull(key)) null else getString(key)

/** Stores the purchase channel per model, never the SIM/network operator. */
internal class GalaxyNotifySelection(private val preferences: SharedPreferences) {
    fun selected(device: GalaxyNotifyResolver.Device): GalaxyNotifyResolver.Channel? {
        val carrier = preferences.getString(key(device.model), null)
        return device.channels.firstOrNull { it.carrier == carrier }
    }
    fun select(device: GalaxyNotifyResolver.Device, channel: GalaxyNotifyResolver.Channel) {
        require(channel in device.channels)
        preferences.edit { putString(key(device.model), channel.carrier) }
    }
    fun reset(model: String) { preferences.edit { remove(key(model)) } }
    private fun key(model: String) = "purchase_channel_${GalaxyNotifyResolver.normalize(model)}"
}
