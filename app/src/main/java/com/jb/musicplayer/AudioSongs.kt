package com.jb.musicplayer

import java.io.Serializable

class AudioSongs(
    var data: String,
    var title: String,
    var album: String,
    var artist: String
) : Serializable