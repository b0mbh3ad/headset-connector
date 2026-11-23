package com.headsetconnector.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("headsetconnector", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base_url", "").orEmpty()
        set(value) = sp.edit().putString("base_url", value.trim().trimEnd('/')).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(value) = sp.edit().putString("token", value).apply()

    var userId: String
        get() = sp.getString("user_id", "").orEmpty()
        set(value) = sp.edit().putString("user_id", value).apply()

    var username: String
        get() = sp.getString("username", "").orEmpty()
        set(value) = sp.edit().putString("username", value).apply()

    var displayName: String
        get() = sp.getString("display_name", "").orEmpty()
        set(value) = sp.edit().putString("display_name", value).apply()

    var avatar: String?
        get() = sp.getString("avatar", null)
        set(value) = sp.edit().putString("avatar", value).apply()

    var email: String?
        get() = sp.getString("email", null)
        set(value) = sp.edit().putString("email", value).apply()

    var headset: String?
        get() = sp.getString("headset", null)
        set(value) = sp.edit().putString("headset", value).apply()

    var headsetLabel: String?
        get() = sp.getString("headset_label", null)
        set(value) = sp.edit().putString("headset_label", value).apply()

    var bio: String?
        get() = sp.getString("bio", null)
        set(value) = sp.edit().putString("bio", value).apply()

    var accent: String?
        get() = sp.getString("accent", null)
        set(value) = sp.edit().putString("accent", value).apply()

    var banner: String?
        get() = sp.getString("banner", null)
        set(value) = sp.edit().putString("banner", value).apply()

    var badge: String?
        get() = sp.getString("badge", null)
        set(value) = sp.edit().putString("badge", value).apply()

    var theme: String
        get() = sp.getString("theme", "normal").orEmpty()
        set(value) = sp.edit().putString("theme", value).apply()

    // Код для подключения телефона к агенту на шлеме (как в PicoPilot).
    var agentPin: String
        get() {
            var pin = sp.getString("agent_pin", null)
            if (pin == null) {
                pin = (100000..999999).random().toString()
                sp.edit().putString("agent_pin", pin).apply()
            }
            return pin
        }
        set(value) = sp.edit().putString("agent_pin", value).apply()

    fun lastRead(chatId: String): Long = sp.getLong("read_$chatId", 0L)

    fun setLastRead(chatId: String, at: Long) {
        sp.edit().putLong("read_$chatId", at).apply()
    }

    fun clearSession() {
        sp.edit()
            .remove("token")
            .remove("user_id")
            .remove("username")
            .remove("display_name")
            .remove("avatar")
            .remove("email")
            .remove("headset")
            .remove("headset_label")
            .remove("bio")
            .remove("accent")
            .remove("banner")
            .remove("badge")
            .apply()
    }
}
