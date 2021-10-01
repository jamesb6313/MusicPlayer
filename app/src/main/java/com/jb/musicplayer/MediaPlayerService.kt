package com.jb.musicplayer


import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.*
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer.*
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import android.app.PendingIntent
import java.lang.Exception


class MediaPlayerService : Service(), OnCompletionListener,
    OnPreparedListener, OnErrorListener, OnSeekCompleteListener,
    OnInfoListener,
    OnBufferingUpdateListener {
    // Binder given to clients
    private val iBinder: IBinder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null

    //path to the audio file
    //val mediaFile: String? = null

    //Used to pause/resume MediaPlayer
    private var resumePosition = 0

    //List of available Audio files
    private var audioList: ArrayList<AudioSongs>? = null
    private var audioIndex = -1
    private var activeAudio //an object of the currently playing audio
            : AudioSongs? = null

    //MediaSession
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null

    //
    //https://www.geeksforgeeks.org/how-to-manage-audio-focus-in-android/
    var mFocusRequest : AudioFocusRequest? = null

    // Audio manager instance to manage or
    // handle the audio interruptions
    var mAudioManager : AudioManager? = null

    // Audio attributes instance to set the playback
    // attributes for the media player instance
    // these attributes specify what type of media is
    // to be played and used to callback the audioFocusChangeListener
    var mPlaybackAttributes: AudioAttributes? = null

    // media player is handled according to the
    // change in the focus which Android system grants for
    var mAudioFocusChangeListener =
        OnAudioFocusChangeListener { focusState ->
            //Invoked when the audio focus of the system is updated.
            when (focusState) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.d("MPServiceInfo", "onChangeFocus GAIN - resume playback")
                    // resume playback
                    if (mediaPlayer == null) initMediaPlayer() else if (!mediaPlayer!!.isPlaying) mediaPlayer!!.start()
                    mediaPlayer!!.setVolume(1.0f, 1.0f)
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.d(
                        "MPServiceInfo",
                        "onChangeFocus LOSS - Lost focus for an unbounded amount of time: stop playback and release media player"
                    )
                    // Lost focus for an unbounded amount of time: stop playback and release media player
                    if (mediaPlayer!!.isPlaying) mediaPlayer!!.stop()
                    mediaPlayer!!.release()
                    mediaPlayer = null
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Lost focus for a short time, but we have to stop
                    // playback. We don't release the media player because playback
                    // is likely to resume
                    Log.d("MPServiceInfo", "onChangeFocus LOSS_TRANSIENT - resume playback")
                    if (mediaPlayer!!.isPlaying) mediaPlayer !!. pause ()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Lost focus for a short time, but it's ok to keep playing
                    // at an attenuated level
                    Log.d("MPServiceInfo", "onChangeFocus LOSS_TRANSIENT_CAN_DUCK - Lost focus for a short time, but it's ok to keep playing at an attenuated level")
                    if (mediaPlayer!!.isPlaying) mediaPlayer!!.setVolume(0.1f, 0.1f)
                }
            }
        }

    //@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (mediaSessionManager != null) return  //mediaSessionManager exists
        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        // Create a new MediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //Get MediaSessions transport controls
        transportControls = mediaSession!!.controller.transportControls
        //set MediaSession -> ready to receive media commands
        mediaSession!!.isActive = true

        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        //https://developer.android.com/guide/topics/media-apps/video-app/building-a-video-player-activity
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        //Set mediaSession's MetaData
        updateMetaData()

        // Attach Callback to receive MediaSession updates
        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {

//https://stackoverflow.com/questions/28798116/how-to-use-the-new-mediasession-class-to-receive-media-button-presses-on-android
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                super.onMediaButtonEvent(mediaButtonIntent)
                //Toast.makeText(this@MediaPlayerService, "Media Button Event", Toast.LENGTH_LONG).show()
                //Log.i("MPServiceInfo", "onMediaButtonEvent called: $mediaButtonIntent")


//https://stackoverflow.com/questions/55030914/handle-media-button-from-service-in-android-8-0
                val keyEvent: KeyEvent? = mediaButtonIntent.extras!![Intent.EXTRA_KEY_EVENT] as KeyEvent?
                if (keyEvent != null) {
                    Toast.makeText(this@MediaPlayerService, "Media Button Event action: " + keyEvent.action, Toast.LENGTH_LONG).show()

                    if (keyEvent.action == KeyEvent.ACTION_UP) {
                        Toast.makeText(this@MediaPlayerService, "Button Up - Media Button Event: " + keyEvent.keyCode, Toast.LENGTH_LONG).show()
                        if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ) {                             //keyCode = 86
                            Log.i("MPServiceInfo", "keyEvent KEYCODE_MEDIA_PLAY")
                            restartCurrent()
                            updateMetaData()
                            buildNotification(PlaybackStatus.PLAYING)
                            return true
                        } else {
                            if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||                      //Home spkr keyCode = 127: keyEvent KEYCODE_MEDIA_NEXT
                                keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                            ) {                                                                         //Car Audio keyCode = 90
                                Log.i("MPServiceInfo", "keyEvent KEYCODE_MEDIA_NEXT")
                                //onSkipToNext()
                                skipToNext()
                                updateMetaData()
                                buildNotification(PlaybackStatus.PLAYING)
                                return true
                            } else {
                                if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||              //Home spkr keyCode = 88: keyEvent KEYCODE_MEDIA_PREVIOUS
                                    keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                                ) {                                                                     //Car Audio keyCode = 89
                                    Log.i("MPServiceInfo", "keyEvent KEYCODE_MEDIA_PREVIOUS")
                                    //onSkipToPrevious()
                                    skipToPrevious()
                                    updateMetaData()
                                    buildNotification(PlaybackStatus.PLAYING)
                                    return true
                                }
                            }
                        }
                    }
                }

//                val bundle: Bundle? = mediaButtonIntent.getExtras()
//                if (bundle != null) {
//                    for (key in bundle.keySet()) {
//                        val value: Any = bundle.get(key)!!
//                        Log.i("MPServiceInfo", String.format("Key = %s, value.toString() = %s, value.javaClass = (%s)", key, value.toString(), value.javaClass.name)
//                        )
//                    }
//                }

                return true
            }

            // Implement callbacks
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                Toast.makeText(this@MediaPlayerService, "Media Button Event called: onPause()", Toast.LENGTH_LONG).show()
                Log.i("MPServiceInfo", "onMediaButtonEvent called: onPause()")
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSED)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                Toast.makeText(this@MediaPlayerService, "Media Button Event called: onSkipToNext()", Toast.LENGTH_LONG).show()
                Log.i("MPServiceInfo", "onMediaButtonEvent called: onSkipToNext()")
                skipToNext()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                Toast.makeText(this@MediaPlayerService, "Media Button Event called: onSkipToPrevious()", Toast.LENGTH_LONG).show()
                Log.i("MPServiceInfo", "onMediaButtonEvent called: onSkipToPrevious()")
                skipToPrevious()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onStop() {
                super.onStop()
                removeNotification()

                Log.i("MPServiceInfo", "initMediaSession() MediaSession.onStop - stop service")
                //Stop the service
                stopSelf()
            }

            override fun onSeekTo(position: Long) {
                super.onSeekTo(position)
            }
        })
    }

    private fun updateMetaData() {
        val albumArt = BitmapFactory.decodeResource(
            resources,
            R.drawable.image5
        ) //replace with medias albumArt

        // Update the current metadata
        mediaSession!!.setMetadata(
            MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio?.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio?.album)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio?.title)
                .build()
        )
    }

    private fun restartCurrent() {

        //Update stored index
        StorageUtil(applicationContext).storeAudioIndex(audioIndex)
        stopMedia()
        //reset mediaPlayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun skipToNext() {
        if (audioIndex == audioList!!.size - 1) {
            //if last in playlist
            audioIndex = 0
            activeAudio = audioList!![audioIndex]
        } else {
            //get next in playlist
            activeAudio = audioList!![++audioIndex]
        }

        //Update stored index
        StorageUtil(applicationContext).storeAudioIndex(audioIndex)
        stopMedia()
        //reset mediaPlayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun skipToPrevious() {
        if (audioIndex == 0) {
            //if first in playlist
            //set index to the last of audioList
            audioIndex = audioList!!.size - 1
            activeAudio = audioList!![audioIndex]
        } else {
            //get previous in playlist
            activeAudio = audioList!![--audioIndex]
        }

        //Update stored index
        StorageUtil(applicationContext).storeAudioIndex(audioIndex)
        stopMedia()
        //reset mediaPlayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    //
    override fun onBind(intent: Intent): IBinder {
        return iBinder
    }

    override fun onBufferingUpdate(mp: MediaPlayer, percent: Int) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    override fun onCompletion(mp: MediaPlayer) {
        //Invoked when playback of a media source has completed.
        stopMedia()
        //stop the service
        stopSelf()
    }

    //Handle errors
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //Invoked when there has been an error during an asynchronous operation.
        when (what) {
            MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.d(
                "MPServiceInfo",
                "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra"
            )
            MEDIA_ERROR_SERVER_DIED -> Log.d(
                "MPServiceInfo",
                "MEDIA ERROR SERVER DIED $extra"
            )
            MEDIA_ERROR_UNKNOWN -> Log.d(
                "MPServiceInfo",
                "MEDIA ERROR UNKNOWN $extra"
            )
        }
        return false
    }

    override fun onInfo(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //Invoked to communicate some information.
        return false
    }

    override fun onPrepared(mp: MediaPlayer) {
        //Invoked when the media source is ready for playback.
        playMedia()
    }

    override fun onSeekComplete(mp: MediaPlayer) {
        //Invoked indicating the completion of a seek operation.
    }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        //Set up MediaPlayer event listeners
        mediaPlayer!!.setOnCompletionListener(this)
        mediaPlayer!!.setOnErrorListener(this)
        mediaPlayer!!.setOnPreparedListener(this)
        mediaPlayer!!.setOnBufferingUpdateListener(this)
        mediaPlayer!!.setOnSeekCompleteListener(this)
        mediaPlayer!!.setOnInfoListener(this)
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer!!.reset()

        Log.d("MPServiceInfo","initMediaPlayer - mediaPlayer!!.reset() just called")
        mediaPlayer!!.setAudioAttributes(
            AudioAttributes.Builder()
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )

        try {
            //mediaPlayer!!.setDataSource(activeAudio?.data) //- this works

            /* Add to manifest - android:requestLegacyExternalStorage="true"
            This attribute is "false" by default on apps targeting Android Q. (Android 10 API 29)
            */

            // This works for Android 9 (API 28) & my Phone Android 11
            // with above in manifest now works for Android 10 (API 29)
            val filePath = activeAudio?.data
            val file = File(filePath!!)
            val inputStream = FileInputStream(file)
            mediaPlayer!!.setDataSource(inputStream.fd)
            inputStream.close()
            // This works for Android 9 (API 28) & my Phone Android 11

            mediaPlayer!!.prepareAsync()  // causes fatal error in emulator (move inside try stmt works here)
        } catch (e: IOException) {
            Log.e("MPServiceInfo", "Error in setDataSource - error message = " + e.message)
            e.printStackTrace()
            Log.e("MPServiceInfo", "End of stacktrace - NEED TO STOP SERVICE")
            stopSelf()
        }
        //mediaPlayer!!.prepareAsync()  // causes fatal error in emulator (move inside try stmt)

        //Added to allow next song to play after last song finishes
        mediaPlayer!!.setOnCompletionListener {
            Toast.makeText(this@MediaPlayerService, "Song complete", Toast.LENGTH_SHORT).show()
            Log.i("MPServiceInfo", "Song completed, next song called")

            skipToNext()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun playMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            resumePosition = mediaPlayer!!.currentPosition
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(resumePosition)
            mediaPlayer!!.start()
        }
    }

    //used in onDestroy
    @RequiresApi(Build.VERSION_CODES.O)
    private fun removeAudioFocus(): Boolean {
        Log.d("MPServiceInfo","removeAudioFocus() - abandonAudioFocusRequest")
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                mAudioManager!!.abandonAudioFocusRequest(mFocusRequest!!)
    }

    //The system calls this method when an activity, requests the service be started
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            //Load data from SharedPreferences
            val storage = StorageUtil(applicationContext)
            audioList = storage.loadAudio()
            audioIndex = storage.loadAudioIndex()
            if (audioIndex != -1 && audioIndex < audioList!!.size) {
                //index is in a valid range
                activeAudio = audioList!![audioIndex]
            } else {
                stopSelf()
            }
        } catch (e: NullPointerException) {
            stopSelf()
        }

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mPlaybackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        mFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(mPlaybackAttributes!!)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(mAudioFocusChangeListener)
            .build()

        //Request audio focus
        val focusRequest  = mAudioManager!!.requestAudioFocus(mFocusRequest!!)
        when (focusRequest) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                Log.d("MPServiceInfo","Could not gain focus - AUDIOFOCUS_REQUEST_FAILED")
                stopSelf()
            }
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                Log.d("MPServiceInfo","Continue - AUDIOFOCUS_REQUEST_GRANTED")
            }
        }
//        if (requestAudioFocus() == false) {
//            //Could not gain focus
//            stopSelf()
//        }
        if (mediaSessionManager == null) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()

        Log.i("MPServiceInfo","onDestroy() - MediaPlayerService")
        if (mediaPlayer != null) {
            stopMedia()
            try {
                mediaPlayer!!.reset()
            } catch (e: Exception) {
                Log.i("MPPlayerServiceInfo","mediaPlayer.reset - START TRACE")
                e.printStackTrace()
                Log.i("MPPlayerServiceInfo","mediaPlayer.reset - END TRACE")
            }
            mediaPlayer!!.release()
        }
        removeAudioFocus()
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
        removeNotification()

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(playNewAudio)

//        Log.i("MPServiceInfo","onDestroy() - this.stopSelf() called")
//        this.stopSelf()

        //clear cached playlist
        StorageUtil(applicationContext).clearCachedAudioPlaylist()
    }

    //Becoming noisy
    private val becomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia()
            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    private fun registerBecomingNoisyReceiver() {
        //register after getting audio focus
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    //This gets called if song playing and user selects a song from list
    //works fine
    private val playNewAudio: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            val message = "Broadcast Intent Detected " + (intent.action ?: "default null action")

            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.i("MPServiceInfo", message)

            //Get the new media index from SharedPreferences
            audioIndex = StorageUtil(applicationContext).loadAudioIndex()
            if (audioIndex != -1 && audioIndex < audioList!!.size) {
                //index is in a valid range
                activeAudio = audioList!![audioIndex]
            } else {
                stopSelf()
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia()
            mediaPlayer!!.reset()
            initMediaPlayer()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun registerPlayNewAudio() {
        val filter = IntentFilter(Broadcast_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudio, filter)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onCreate() {
        super.onCreate()

        val pendingCloseIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction(CLOSE_ACTION),
            0
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannelID = "player_channel_01"
            val channel = NotificationChannel(
                notificationChannelID,
                "Stop MusicPlayer Service Channel",
                NotificationManager.IMPORTANCE_LOW      // API = 26 this channel contains no sound
            )
            // Configure the notification channel.
            channel.description = "Song Notification Channel"
            channel.enableLights(true)
            channel.lightColor = Color.RED
            //notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            channel.enableVibration(false)

            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
            val notification = NotificationCompat.Builder(this, notificationChannelID)
                // Add a stop button
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop MusicPlayer Service", pendingCloseIntent)

                .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle("Stop Music Player")
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentText("MusicPlayer Service must be stopped")
                .setOngoing(true)
                .build()
            startForeground(PLAYER_NOTIFICATION_ID, notification)
        } else {
            val notification = NotificationCompat.Builder(this)
                // Add a stop button
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop MusicPlayer Service", pendingCloseIntent)

                .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle("Stop Music Player")
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentText("MusicPlayer Service must be stopped")
                .setOngoing(true)
                .build()
            startForeground(PLAYER_NOTIFICATION_ID, notification)
        }


        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener()
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver()
        //Listen for new Audio to play -- BroadcastReceiver
        registerPlayNewAudio()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun buildNotification(playbackStatus: PlaybackStatus) {
        /**
         * Notification actions -> playbackAction()
         *  0 -> Play
         *  1 -> Pause
         *  2 -> Next track
         *  3 -> Previous track
         */

        var notificationAction = android.R.drawable.ic_media_pause //needs to be initialized
        var playPauseAction: PendingIntent? = null

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus === PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause
            //create the pause action
            playPauseAction = playbackAction(1)
        } else if (playbackStatus === PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play
            //create the play action
            playPauseAction = playbackAction(0)
        }
        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.drawable.image5
        ) //replace with your own image

        val pendingDismissedIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction(CLOSE_ACTION),
            0
        )

//https://stackoverflow.com/questions/45462666/notificationcompat-builder-deprecated-in-android-o
//8 - Simple Sample
        val channelId = "song_channel_01"
        val notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        lateinit var notificationCompatBuilder: NotificationCompat.Builder


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                "Song Notification Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            // Configure the notification channel.
            notificationChannel.description = "Song Notification Channel"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.GREEN
            //notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(false)
            notificationManager.createNotificationChannel(notificationChannel)

            // Create a new Notification
            notificationCompatBuilder =
                NotificationCompat.Builder(this, channelId)
                    .setShowWhen(false) // Set the Notification style
                    .setStyle(
                        androidx.media.app.NotificationCompat.MediaStyle()
                            // Attach our MediaSession token
                            .setMediaSession(mediaSession!!.sessionToken)
                            // Show our playback controls in the compact notification view.
                            .setShowActionsInCompactView(0, 1, 2)
                    )
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    // Set the large and small icons
                    .setLargeIcon(largeIcon)
                    .setSmallIcon(R.drawable.ic_stat_music)
                    // Set Notification content information
                    .setContentText(activeAudio?.artist)
                    //.setContentTitle(activeAudio?.album)
                    //.setContentInfo(activeAudio?.title) // Add playback actions
                    .setContentTitle(activeAudio?.title)
                    .setContentInfo(activeAudio?.album) // Add playback actions
                    .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                    .addAction(notificationAction, "pause", playPauseAction)
                    .addAction(
                        android.R.drawable.ic_media_next,
                        "next",
                        playbackAction(2)
                    )
                    //.setDeleteIntent(dismissPendingIntent)
                    .setDeleteIntent(pendingDismissedIntent)
        } else {
            // Create a new Notification
            notificationCompatBuilder =
                NotificationCompat.Builder(this)
                    .setShowWhen(false) // Set the Notification style
                    .setStyle(
                        androidx.media.app.NotificationCompat.MediaStyle()
                            // Attach our MediaSession token
                            .setMediaSession(mediaSession!!.sessionToken)
                            // Show our playback controls in the compact notification view.
                            .setShowActionsInCompactView(0, 1, 2)
                    )
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    // Set the large and small icons
                    .setLargeIcon(largeIcon)
                    .setSmallIcon(R.drawable.ic_stat_music)
                    // Set Notification content information
                    .setContentText(activeAudio?.artist)
                    //.setContentTitle(activeAudio?.album)
                    //.setContentInfo(activeAudio?.title) // Add playback actions
                    .setContentTitle(activeAudio?.title)
                    .setContentInfo(activeAudio?.album) // Add playback actions
                    .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                    .addAction(notificationAction, "pause", playPauseAction)
                    .addAction(
                        android.R.drawable.ic_media_next,
                        "next",
                        playbackAction(2)
                    )
                    //.setDeleteIntent(dismissPendingIntent)
                    .setDeleteIntent(pendingDismissedIntent)
        }

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIFICATION_ID,
            notificationCompatBuilder.build()
        )

    }

    private fun removeNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackAction = Intent(this, MediaPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                // Play
                playbackAction.action = ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            1 -> {
                // Pause
                playbackAction.action = ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            2 -> {
                // Next track
                playbackAction.action = ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            3 -> {
                // Previous track
                playbackAction.action = ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            else -> {
            }
        }
        return null
    }

    private fun handleIncomingActions(playbackAction: Intent?) {
        if (playbackAction == null || playbackAction.action == null) return
        val actionString = playbackAction.action
        if (actionString.equals(ACTION_PLAY, ignoreCase = true)) {
            transportControls!!.play()
        } else if (actionString.equals(
                ACTION_PAUSE,
                ignoreCase = true
            )
        ) {
            transportControls!!.pause()
        } else if (actionString.equals(
                ACTION_NEXT,
                ignoreCase = true
            )
        ) {
            transportControls!!.skipToNext()
        } else if (actionString.equals(
                ACTION_PREVIOUS,
                ignoreCase = true
            )
        ) {
            transportControls!!.skipToPrevious()
        } else if (actionString.equals(
                ACTION_STOP,
                ignoreCase = true
            )
        ) {
            transportControls!!.stop()
        }
    }

    //
    //Handle incoming phone calls
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null

    //Handle incoming phone calls
    private fun callStateListener() {
        // Get the telephony manager
        telephonyManager =
            getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        //Starting listening for PhoneState changes
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(
                state: Int,
                incomingNumber: String
            ) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> if (mediaPlayer != null) {
                        pauseMedia()
                        ongoingCall = true
                    }
                    TelephonyManager.CALL_STATE_IDLE ->                         // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                }
            }
        }
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager!!.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    } //

    override fun onTaskRemoved(rootIntent: Intent?) {

        Log.i("MPServiceInfo", "Service stopped with onTaskRemoved()")
        Toast.makeText(this@MediaPlayerService, "MediaPlayer Service Stopped", Toast.LENGTH_LONG).show()
        //stop service
        this.stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val ACTION_PLAY = "com.jb.musicplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.jb.musicplayer.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.jb.musicplayer.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.jb.musicplayer.ACTION_NEXT"
        const val ACTION_STOP = "com.jb.musicplayer.ACTION_STOP"
        //const val ACTION_FAST_FORWARD = "com.jb.musicplayer.ACTION_FAST_FORWARD"
        //const val ACTION_REWIND = "com.jb.musicplayer.ACTION_REWIND"

        //AudioPlayer notification ID
        private const val NOTIFICATION_ID = 101

        const val PLAYER_NOTIFICATION_ID = 102
        const val CLOSE_ACTION: String = "close"

        const val Broadcast_PLAY_NEW_AUDIO = "com.jb.musicplayer.PlayNewAudio"
    }
}