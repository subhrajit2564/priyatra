package com.priyatra.guide.data.remote

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.priyatra.guide.BuildConfig
import com.priyatra.guide.PriyaTraApplication
import com.priyatra.guide.data.TripJson
import com.priyatra.guide.data.TripsCatalogFile
import com.priyatra.guide.data.db.AppSettingEntity
import com.priyatra.guide.auth.AdminPhoneConfig
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.data.db.PriyaTraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Supabase (PostgREST) shared catalog: one row in `public.priyatra_state` (see `supabase_setup.sql`).
 * When [BuildConfig] URL/key are set in `local.properties`, the app syncs the trip catalog
 * and admin phone CSV across devices. Room remains the on-device cache.
 */
object CatalogCloudSync {
    private const val TAG = "CatalogCloudSync"
    private const val TABLE = "priyatra_state"
    private val json = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean {
        val u = BuildConfig.SUPABASE_URL
        return u.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
            u.trim().startsWith("https://", ignoreCase = true) &&
            u.toHttpUrlOrNull() != null
    }

    private fun restBase(): String? {
        val b = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (b.isEmpty()) return null
        return "$b/rest/v1"
    }

    private fun serviceHeaders() = okhttp3.Headers.Builder()
        .add("apikey", BuildConfig.SUPABASE_ANON_KEY)
        .add("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        .add("Accept", "application/json")
        .add("Content-Profile", "public")

    private data class StateRow(
        val id: Int = 1,
        @SerializedName("catalog_json") val catalogJson: String,
        /** Comma-separated normalized admin digits; same as [AdminPhoneConfig.KEY_ADMIN_PHONES_CSV] locally. */
        @SerializedName("admin_phone_digits") val adminPhoneDigits: String? = null,
    )

    private fun applyToRoom(context: android.content.Context, row: StateRow) {
        val app = context.applicationContext
        val db = PriyaTraDatabase.getInstance(app)
        val catalog: TripsCatalogFile = try {
            TripJson.gson.fromJson(row.catalogJson, TripsCatalogFile::class.java)
                ?: TripsCatalogFile()
        } catch (e: Exception) {
            Log.e(TAG, "parse catalog_json", e)
            TripsCatalogFile()
        }
        db.tripDao().replaceCatalog(catalog.trips)
        val admin = row.adminPhoneDigits?.trim()?.takeIf { it.isNotEmpty() }
        if (admin != null) {
            db.settingsDao().set(
                AppSettingEntity(AdminPhoneConfig.KEY_ADMIN_PHONES_CSV, admin),
            )
        }
    }

    /**
     * Cold start: if cloud has a row, pull into Room. If cloud is empty, push local (seeded) catalog.
     */
    suspend fun runStartupSync(context: android.content.Context) = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext
        when (val g = getRemoteRow()) {
            is GetResult.Found -> {
                applyToRoom(context, g.row)
                withContext(Dispatchers.Main) {
                    TripRepository.reloadCatalog(context)
                }
            }
            is GetResult.Empty -> {
                val n = PriyaTraDatabase.getInstance(context).tripDao().count()
                if (n > 0) {
                    pushToRemote(context)
                }
            }
            is GetResult.Failure -> { /* keep local / offline */ }
        }
    }

    /**
     * User-triggered: pull from cloud and overwrite local if a row exists.
     * @return true if a row was applied.
     */
    suspend fun forcePullAndApply(context: android.content.Context): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        return@withContext when (val g = getRemoteRow()) {
            is GetResult.Found -> {
                applyToRoom(context, g.row)
                withContext(Dispatchers.Main) {
                    TripRepository.reloadCatalog(context)
                }
                true
            }
            else -> false
        }
    }

    private sealed class GetResult {
        data class Found(val row: StateRow) : GetResult()
        data object Empty : GetResult()
        data object Failure : GetResult()
    }

    private fun getRemoteRow(): GetResult {
        val rest = restBase() ?: return GetResult.Failure
        val url = try {
            "$rest/$TABLE?id=eq.1&select=catalog_json,admin_phone_digits,id&limit=1"
        } catch (e: Exception) {
            return GetResult.Failure
        }
        val req = Request.Builder()
            .url(url)
            .get()
            .headers(
                serviceHeaders()
                    .add("Content-Type", "application/json")
                    .build(),
            )
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GET $TABLE ${resp.code}")
                    return GetResult.Failure
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return GetResult.Empty
                val arr = TripJson.gson.fromJson(body, Array<StateRow>::class.java) ?: return GetResult.Empty
                val first = arr.firstOrNull() ?: return GetResult.Empty
                GetResult.Found(first)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET", e)
            GetResult.Failure
        }
    }

    private fun postUpsertState(row: StateRow) {
        val rest = restBase() ?: return
        val bodyJson = TripJson.gson.toJson(listOf(row))
        // Single-row upsert: body must be a JSON array for PostgREST merge.
        val url = "$rest/$TABLE?on_conflict=id"
        val req = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(json))
            .headers(
                serviceHeaders()
                    .add("Content-Type", "application/json")
                    .add("Prefer", "return=representation,resolution=merge-duplicates")
                    .build(),
            )
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                Log.w(TAG, "POST upsert ${r.code} ${r.message} ${r.body?.string()}")
            }
        }
    }

    fun pushToRemote(context: android.content.Context) {
        if (!isConfigured()) return
        val app = context.applicationContext
        val db = PriyaTraDatabase.getInstance(app)
        val trips = db.tripDao().getAllWithPhones().map {
            com.priyatra.guide.data.db.TripEntityMappers.toStoredTrip(it)
        }
        val admin = AdminPhoneConfig.loadDigitsList(db.settingsDao())
            .joinToString(",")
            .ifEmpty { AdminPhoneConfig.DEFAULT_ADMIN_PHONES_CSV }
        val file = com.priyatra.guide.data.TripsCatalogFile(trips = trips)
        val row = StateRow(
            id = 1,
            catalogJson = TripJson.gson.toJson(file),
            adminPhoneDigits = admin,
        )
        runCatching { postUpsertState(row) }
            .onFailure { Log.w(TAG, "push", it) }
    }

    fun requestPushAfterLocalSave(context: android.content.Context) {
        if (!isConfigured()) return
        val ac = context.applicationContext as? PriyaTraApplication ?: return
        ac.applicationScope.launch(Dispatchers.IO) {
            pushToRemote(context.applicationContext)
        }
    }
}
