package com.nusu.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import org.json.JSONArray
import org.json.JSONObject

/** Tool declarations Gemini sees + the code that runs when it calls one. Add new tools here. */
class PhoneTools(private val ctx: Context) {

    private fun str(d: String) = JSONObject().put("type", "STRING").put("description", d)
    private fun int(d: String) = JSONObject().put("type", "INTEGER").put("description", d)

    private fun fn(name: String, desc: String, vararg p: Pair<String, JSONObject>): JSONObject {
        val f = JSONObject().put("name", name).put("description", desc)
        if (p.isNotEmpty()) {
            val props = JSONObject()
            p.forEach { props.put(it.first, it.second) }
            f.put("parameters", JSONObject()
                .put("type", "OBJECT")
                .put("properties", props)
                .put("required", JSONArray(p.map { it.first })))
        }
        return f
    }

    fun declarations(): JSONArray = JSONArray()
        .put(fn("open_app", "Open an installed app by display name. Use the English name, e.g. WhatsApp, YouTube, Settings.",
            "app_name" to str("App display name")))
        .put(fn("dial_number", "Open the phone dialer with a number (user presses call).",
            "number" to str("Phone number")))
        .put(fn("play_youtube", "Search YouTube and show results. Then use read_screen and tap_text to pick a video.",
            "query" to str("Song or video to search")))
        .put(fn("set_alarm", "Set an alarm.",
            "hour" to int("0-23"), "minute" to int("0-59"), "label" to str("Alarm label")))
        .put(fn("send_whatsapp", "Open a WhatsApp chat and press send. Phone number must include country code.",
            "phone" to str("Number with country code"), "message" to str("Message text")))
        .put(fn("read_screen", "Return the visible text on the current screen. Items marked [tap] are clickable."))
        .put(fn("tap_text", "Tap the on-screen element containing this text or description.",
            "text" to str("Text to tap")))
        .put(fn("type_text", "Type text into the focused or first text field on screen.",
            "text" to str("Text to type")))
        .put(fn("scroll", "Scroll the current screen.",
            "direction" to str("up or down")))
        .put(fn("press_key", "Press a system key.",
            "key" to str("back, home, recents or notifications")))

    fun execute(name: String, a: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(a.getString("app_name"))
            "dial_number" -> {
                launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(a.getString("number")))))
                "dialer opened"
            }
            "play_youtube" -> playYoutube(a.getString("query"))
            "set_alarm" -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, a.getInt("hour"))
                    .putExtra(AlarmClock.EXTRA_MINUTES, a.getInt("minute"))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "NUSU"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                launch(i)
                "alarm set"
            }
            "send_whatsapp" -> sendWhatsapp(a.getString("phone"), a.getString("message"))
            "read_screen" -> a11y { it.readScreen() }
            "tap_text" -> a11y { if (it.tapText(a.getString("text"))) "tapped" else "not found" }
            "type_text" -> a11y { if (it.typeText(a.getString("text"))) "typed" else "no text field found" }
            "scroll" -> a11y { if (it.scroll(a.getString("direction") == "down")) "scrolled" else "cannot scroll" }
            "press_key" -> a11y { if (it.press(a.getString("key"))) "done" else "unknown key" }
            else -> "unknown tool: $name"
        }
    } catch (e: Exception) {
        "error: ${e.message}"
    }

    private fun a11y(block: (NusuAccessibilityService) -> String): String {
        val s = NusuAccessibilityService.instance
            ?: return "error: accessibility service is not enabled"
        return block(s)
    }

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        (NusuAccessibilityService.instance ?: ctx).startActivity(i)
    }

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val q = name.trim().lowercase()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "app not found: $name"
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "cannot launch $name"
        launch(intent)
        return "opened ${match.loadLabel(pm)}"
    }

    private fun playYoutube(query: String): String {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        return try {
            launch(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube"))
            "YouTube search opened"
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, uri))
            "search opened in browser"
        }
    }

    private fun sendWhatsapp(phone: String, msg: String): String {
        val digits = phone.filter { it.isDigit() }
        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(msg))
        try {
            launch(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
        } catch (e: Exception) {
            return "WhatsApp is not installed"
        }
        val s = NusuAccessibilityService.instance
            ?: return "chat opened, but accessibility is off so the message was not sent"
        repeat(8) {
            Thread.sleep(700)
            if (listOf("Send", "পাঠান").any { s.tapText(it) }) return "message sent"
        }
        return "chat opened but the Send button was not found"
    }
}
