package com.aireceptionist.app.data

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.aireceptionist.app.utils.Logger

/**
 * ContentProvider that exposes read-only AI receptionist metrics
 * to trusted first-party components (widgets, tiles, etc.).
 *
 * Supported URIs:
 *   content://com.aireceptionist.app.provider/ai_status
 *   content://com.aireceptionist.app.provider/call_stats
 */
class AIDataProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.aireceptionist.app.provider"

        private const val PATH_AI_STATUS  = "ai_status"
        private const val PATH_CALL_STATS = "call_stats"

        val URI_AI_STATUS:  Uri = Uri.parse("content://$AUTHORITY/$PATH_AI_STATUS")
        val URI_CALL_STATS: Uri = Uri.parse("content://$AUTHORITY/$PATH_CALL_STATS")

        private const val CODE_AI_STATUS  = 1
        private const val CODE_CALL_STATS = 2
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).also {
        it.addURI(AUTHORITY, PATH_AI_STATUS,  CODE_AI_STATUS)
        it.addURI(AUTHORITY, PATH_CALL_STATS, CODE_CALL_STATS)
    }

    override fun onCreate(): Boolean {
        Logger.d("AIDataProvider", "Provider initialised")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            CODE_AI_STATUS -> {
                MatrixCursor(arrayOf("status", "version", "model_loaded")).apply {
                    addRow(arrayOf("active", "1.0", 1))
                }
            }
            CODE_CALL_STATS -> {
                MatrixCursor(arrayOf("total_calls", "ai_handled", "transferred")).apply {
                    addRow(arrayOf(0, 0, 0))
                }
            }
            else -> {
                Logger.w("AIDataProvider", "Unknown URI: $uri")
                null
            }
        }
    }

    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        CODE_AI_STATUS  -> "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_AI_STATUS"
        CODE_CALL_STATS -> "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_CALL_STATS"
        else            -> null
    }

    // Provider is read-only – mutations are not supported
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
