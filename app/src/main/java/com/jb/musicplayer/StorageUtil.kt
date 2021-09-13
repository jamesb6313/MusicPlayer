package com.jb.musicplayer

//add gson dependency
import android.content.Context
import android.content.SharedPreferences

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import java.lang.reflect.Type
import java.util.*


class StorageUtil(private val context: Context?) {
    private val STORAGE = " com.jb.audioapp.STORAGE"
    private var mPreferences: SharedPreferences? = null

    fun storeAudio(arrayList: ArrayList<AudioSongs>?) {
        mPreferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val editor = mPreferences!!.edit()
        val gson = Gson()
        val json: String = gson.toJson(arrayList)
        editor.putString("targetArrayList", json)
        editor.apply()
    }

    fun loadAudio(): ArrayList<AudioSongs> {
        mPreferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = mPreferences!!.getString("targetArrayList", null)
        val type: Type =
            object : TypeToken<ArrayList<AudioSongs?>?>() {}.getType()
        return gson.fromJson(json, type)
    }

    fun storeAudioIndex(index: Int) {
        mPreferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val editor = mPreferences!!.edit()
        editor.putInt("audioIndex", index)
        editor.apply()
    }

    fun loadAudioIndex(): Int {
        mPreferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        return mPreferences!!.getInt("audioIndex", -1) //return -1 if no data found
    }

    fun clearCachedAudioPlaylist() {
        mPreferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val editor = mPreferences!!.edit()
        editor.clear()
        editor.commit()
    }

}