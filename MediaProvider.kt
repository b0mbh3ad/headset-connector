package com.headsetconnector.agent

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

object MediaProvider {

    fun list(context: Context, type: String?): MiniHttp.Response {
        val collection = if (type == "videos") {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
        )
        val arr = JSONArray()
        val cursor = context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            var count = 0
            while (it.moveToNext() && count < 300) {
                val id = it.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val item = JSONObject()
                item.put("id", uri)
                item.put("name", it.getString(nameCol))
                item.put("date", it.getLong(dateCol) * 1000)
                item.put("size", it.getLong(sizeCol))
                arr.put(item)
                count += 1
            }
        }
        val root = JSONObject()
        root.put("items", arr)
        return MiniHttp.Response(200, "application/json; charset=utf-8", root.toString().toByteArray())
    }

    fun file(context: Context, id: String?): MiniHttp.Response {
        if (id.isNullOrBlank()) {
            return MiniHttp.Response(400, "application/json", "{}".toByteArray())
        }
        return try {
            val uri = Uri.parse(id)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            MiniHttp.Response(200, mime, bytes)
        } catch (e: Exception) {
            MiniHttp.Response(404, "application/json", "{}".toByteArray())
        }
    }
}
