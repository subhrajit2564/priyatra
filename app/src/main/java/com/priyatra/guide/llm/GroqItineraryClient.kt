package com.priyatra.guide.llm

import android.util.Log
import com.google.gson.JsonSyntaxException
import com.priyatra.guide.BuildConfig
import com.priyatra.guide.data.HotelBooking
import com.priyatra.guide.data.TripJson
import com.priyatra.guide.data.TripPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * [Groq](https://console.groq.com/) OpenAI-compatible API — free tier, good for small volumes.
 * Set `GROQ_API_KEY` in `local.properties` (keeps the key out of VCS if that file is gitignored).
 *
 * Long admin notes or many days can exceed **request** limits; we clip inputs and cap `max_tokens`
 * so the API accepts the call (avoids "Request too large for model").
 */
class GroqItineraryClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val baseUrl: String
        get() = BuildConfig.LLM_BASE_URL.trim().trimEnd('/').ifEmpty { "https://api.groq.com/openai/v1" }

    private val model: String
        get() = BuildConfig.LLM_MODEL.trim().ifEmpty { "llama-3.3-70b-versatile" }

    private val maxOutTokens: Int
        get() = BuildConfig.LLM_MAX_TOKENS.coerceIn(1024, 8192)

    suspend fun generatePackage(
        tripName: String,
        destination: String,
        startDate: LocalDate,
        durationDays: Int,
        hotel: HotelBooking,
        days: List<DayAdminInput>,
    ): Result<TripPackage> = withContext(Dispatchers.IO) {
        val key = BuildConfig.GROQ_API_KEY.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Set GROQ_API_KEY in local.properties. Free key: https://console.groq.com/keys",
                ),
            )
        }
        val zone = ZoneId.of("Asia/Kolkata")
        val dayHints = buildCompactDayHints(days, durationDays)
        var userPrompt = buildUserPrompt(
            tripName = tripName,
            destination = destination,
            startDate = startDate,
            durationDays = durationDays,
            zoneId = zone.id,
            dayHints = dayHints,
            tripHotel = hotel,
        )
        if (userPrompt.length > MAX_USER_PROMPT_CHARS) {
            Log.w(TAG, "Prompt was ${userPrompt.length} chars; trimming to $MAX_USER_PROMPT_CHARS")
            userPrompt = userPrompt.substring(0, MAX_USER_PROMPT_CHARS) + "\n…[trimmed: shorten notes in the admin form to avoid this]"
        }

        val messages = JSONArray()
            .put(
                JSONObject().put("role", "system").put(
                    "content",
                    "You output one JSON object only, valid for Gson. Spots are visitor POIs (not the trip hotel). " +
                        "Fill history, highlights, access, food, souvenirs, and photo tips as instructed in the user message.",
                ),
            )
            .put(JSONObject().put("role", "user").put("content", userPrompt))

        val requestBody = JSONObject().apply {
            put("model", model)
            put("temperature", 0.3)
            put("max_tokens", maxOutTokens)
            put("messages", messages)
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val url = "$baseUrl/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "Groq request model=$model max_tokens=$maxOutTokens user_chars=${userPrompt.length}")

        runCatching {
            client.newCall(httpRequest).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Groq HTTP ${response.code}: $bodyStr")
                    error("Groq API ${response.code}: ${bodyStr.take(1200)}")
                }
                val text = parseAssistantJsonContent(bodyStr)
                if (text.isBlank()) {
                    error("Model returned empty content. Raw: ${bodyStr.take(800)}")
                }
                val plan = try {
                    TripJson.gson.fromJson(text, LlmPlanRoot::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Gson fail. Snippet: ${text.take(500)}", e)
                    error("Invalid JSON from model: ${e.message}\n---\n${text.take(1500)}")
                } ?: error("Empty plan after parse")
                if (plan.days.isEmpty() || plan.spots.isEmpty()) {
                    error("Model returned no itinerary (days or spots empty).")
                }
                val outDays = plan.days.map { dDto ->
                    val admin = days.find { it.dayIndex == dDto.dayIndex }
                    if (admin == null) error("Day ${dDto.dayIndex} from model has no admin row")
                    dDto.toDayPlan(admin)
                }
                val outSpots = plan.spots.map { it.toTravelSpot() }
                TripPackage(
                    title = plan.title?.takeIf { it.isNotBlank() } ?: tripName,
                    zone = zone,
                    destination = plan.destination?.takeIf { it.isNotBlank() } ?: destination,
                    supportPhone = hotel.phone.ifBlank { "+919876543210" },
                    transports = emptyList(),
                    drivers = emptyList(),
                    hotel = hotel,
                    extraHotels = null,
                    spots = outSpots,
                    days = outDays,
                )
            }
        }
    }

    private fun parseAssistantJsonContent(bodyStr: String): String {
        val root = JSONObject(bodyStr)
        root.optJSONObject("error")?.let { err ->
            val msg = err.optString("message", err.toString())
            error("Groq API error: $msg")
        }
        val choice0 = root.optJSONArray("choices")?.optJSONObject(0)
            ?: error("No choices in response: ${bodyStr.take(600)}")

        val finish = choice0.optString("finish_reason", "")
        if (finish == "length") {
            Log.w(TAG, "Completion may be truncated (finish_reason=length). Increase LLM_MAX_TOKENS in local.properties (max 8192) or reduce trip days.")
        }

        val message = choice0.optJSONObject("message")
        var content = message?.optString("content", "")?.trim().orEmpty()

        if (content.isEmpty()) {
            val parts = message?.optJSONArray("content")
            if (parts != null && parts.length() > 0) {
                val part0 = parts.optJSONObject(0)
                content = part0?.optString("text", part0?.optString("content", "")).orEmpty().trim()
            }
        }
        if (content.isEmpty()) {
            return ""
        }
        return extractJsonObjectText(content)
    }

    companion object {
        private const val TAG = "GroqItinerary"
        /** Groq rejects huge bodies; cap total user message (chars ≪ token limit, safe margin). */
        private const val MAX_USER_PROMPT_CHARS = 24_000
        private const val CLIP_NAME = 120
        private const val CLIP_DEST = 500
        private const val CLIP_LINE = 300
        private const val CLIP_STOPS = 900

        private fun clip(s: String, max: Int): String {
            val t = s.trim()
            if (t.length <= max) return t
            return t.substring(0, min(max, t.length)) + "…"
        }

        private fun buildCompactDayHints(days: List<DayAdminInput>, durationDays: Int): String {
            val byIndex = days.sortedBy { it.dayIndex }.take(durationDays.coerceAtLeast(1))
            return byIndex.joinToString("\n") { d ->
                val block = buildString {
                    append("D${d.dayIndex}: ")
                    append("drv ").append(clip(d.driverPocName, 40)).append(" ").append(clip(d.driverPocPhone, 24))
                    append(" | htl ").append(clip(d.hotelName, 80))
                    append(" | ").append(clip(d.hotelAddress, CLIP_LINE))
                    append(" | ll ").append(clip(d.hotelLat, 20)).append(",").append(clip(d.hotelLng, 20))
                    append(" | poc ").append(clip(d.hotelPocPhone, 24))
                    append("\n   stops: ")
                    append(clip(d.stopNotes, CLIP_STOPS))
                }
                block
            }
        }

        private fun buildUserPrompt(
            tripName: String,
            destination: String,
            startDate: LocalDate,
            durationDays: Int,
            zoneId: String,
            dayHints: String,
            tripHotel: HotelBooking,
        ): String = buildString {
            append("Build a $durationDays-day trip plan as ONE JSON object (title, destination, spots[], days[]).\n")
            append("Trip: ").append(clip(tripName, CLIP_NAME)).append(" | region: ")
                .append(clip(destination, CLIP_DEST)).append(" | start ").append(startDate)
                .append(" | zone ").append(zoneId).append('\n')
            append("The tour hotel (DO NOT add this as a spot; hotel is for context only): \"")
                .append(clip(tripHotel.name, 200)).append("\" at approx ")
                .append(tripHotel.lat).append(',').append(tripHotel.lng).append('\n')
            append("Day operator notes (driver/hotel/POC — keep; spots must be public attractions, not this hotel):\n")
            append(dayHints).append("\n\n")
            append(
                "For EVERY \"spots\"[] entry, generate rich visitor content (the hotel is NOT a spot). Each spot has:\n" +
                "1) history: 2–5 sentences of real, place-specific context.\n" +
                "2) highlights: 3–6 short bullet strings: famous or must-see points of this place.\n" +
                "3) reachabilityNote: if the sight is reachable by private car to parking/stop, say so; " +
                "if last-mile is walk-only, say what vehicle reaches where. Write clearly for travellers.\n" +
                "4) trekOrLocalNote: if car cannot reach the sight, or trek/shared jeep/porter/palanquin/parking-to-viewpoint: " +
                "give step-by-step access, approximate time (e.g. \"~40 min up\"), and where to get local transport. " +
                "Include parking or trailhead when relevant; the app will show map pins (spot lat/lng = main interest; " +
                "photoTips lat/lng = viewpoint or trailhead for navigation).\n" +
                "5) foods: 3–6 strings — best local dishes to try at or near this place.\n" +
                "6) souvenirs: 2–5 strings — things worth buying as a memory of this place.\n" +
                "7) photoTips: 2–4 items. For each: viewpoint (short title), description (lighting, angle, when to go), " +
                "exampleImageUrl: a real https link to a Wikimedia / openly licensed image of that view if you know one, else " +
                "\"\" ; lat, lng: GPS for that exact viewpoint (use plausible coordinates in the real area).\n\n" +
                "JSON fields and types: title, destination, " +
                "spots[{id,name,dayIndex,order,lat,lng,history,highlights[],reachabilityNote,trekOrLocalNote,foods[],souvenirs[]," +
                "photoTips:[{viewpoint,description,exampleImageUrl,lat,lng}]}], " +
                "days:[{dayIndex,title,summary,spotIds[]}]. " +
                "spot id unique lowercase. dayIndex 1..$durationDays, at least one spot per day. Use realistic coordinates in-region.",
            )
        }
    }
}

private fun extractJsonObjectText(raw: String): String {
    var t = raw.trim()
    if (t.startsWith("```")) {
        t = t.removePrefix("```json").removePrefix("```").trim()
        val close = t.lastIndexOf("```")
        if (close >= 0) t = t.substring(0, close).trim()
    }
    val start = t.indexOf('{')
    val end = t.lastIndexOf('}')
    if (start >= 0 && end > start) {
        return t.substring(start, end + 1)
    }
    return t
}
