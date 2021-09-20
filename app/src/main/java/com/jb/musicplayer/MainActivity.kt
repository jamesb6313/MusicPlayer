package com.jb.musicplayer

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jb.musicplayer.databinding.ActivityMainBinding
import java.util.*


class MainActivity : AppCompatActivity() {
    var audioList: ArrayList<AudioSongs>? = null
    private lateinit var layout: View
    private lateinit var binding: ActivityMainBinding
    lateinit var mAdapter: MySongRecyclerViewAdapter
    private val REQUEST_ID_MULTIPLE_PERMISSIONS = 42

    var serviceBound = false

    private var player: MediaPlayerService? = null
    var initialSongIndex = 0
    val Broadcast_PLAY_NEW_AUDIO = "com.jb.musicplayer.PlayNewAudio"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        layout = binding.mainLayout
        setContentView(view)

        audioList = ArrayList<AudioSongs>()
        if (checkAndRequestPermissions())
            initializeView()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val listPermissionsNeeded: MutableList<String> = ArrayList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {  //API = 29
            val permissionMedia = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION)
            if (permissionMedia != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }

        val permissionReadStorage = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permissionReadStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionForegroundService = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.FOREGROUND_SERVICE
        )
        if (permissionForegroundService != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionsNeeded.toTypedArray(),
                REQUEST_ID_MULTIPLE_PERMISSIONS
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {
            REQUEST_ID_MULTIPLE_PERMISSIONS ->
                if (grantResults.isNotEmpty()) {
                    var permissionDenied = ""
                    for (per in permissions) {
                        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                            permissionDenied += per.trimIndent()
                            permissionDenied += " - "
                        }

                    }
                    Toast.makeText(
                        this@MainActivity,
                        permissionDenied,
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

    }

    fun initializeView() {
        loadAudio()
        setUpAdapter()
        initRecyclerView()
    }

    private fun setUpAdapter() {
        mAdapter = MySongRecyclerViewAdapter()

        audioList?.let { mAdapter.addItems(it) }
        mAdapter.setOnItemClickListener(onItemClickListener = object : OnItemClickListener {
            override fun onItemClick(position: Int, view: View?) {
                //mAdapter.getItem(position)

                //Toast.makeText(this@MainActivity,"Play songs", Toast.LENGTH_LONG).show()
                Log.i("Testing info", "SetUpAdapter() - current position is $position")
                playAudio(position)
            }
        })
    }

    private fun initRecyclerView() {
        val recyclerView: RecyclerView = findViewById<View>(R.id.recyclerview) as RecyclerView
        //recyclerView = rootView.findViewById(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(this@MainActivity, LinearLayoutManager.VERTICAL))
        recyclerView.adapter = mAdapter

        this.title = getString(R.string.app_name) + " Songs Found: " + audioList?.size
    }

    //loadAudio
    //////////////////////////////////////////////////////////
    private fun loadAudio() {
        try {
            myNewGetAudioFileCount()
        } catch (e: Exception) {
            myShowErrorDlg("Error = " + e.message)
// Cannot use Toast in catch stmt - Toast.makeText(this, " Error = " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun myNewGetAudioFileCount(): Int {

        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            null,
            selection,
            null,
            null
        )
        if (cursor != null && cursor.count > 0) {
            //audioList = ArrayList<AudioSongs>()
            while (cursor.moveToNext()) {
                val data =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
/*                val displayName =
                    cursor.getString(cursor.getColumnIndex((MediaStore.Audio.Media.DISPLAY_NAME)))
                val RelPath =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getString(cursor.getColumnIndex((MediaStore.Audio.Media.RELATIVE_PATH)))
                    } else {
                        TODO("VERSION.SDK_INT < Q")
                    }*/
                val title =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))


// data = /storage/emulated/0/Music/LinkinPark2.mp3
                // displayName = /LinkinPark2.mps
                // RelPath = Music/
                // Save to audioList
                audioList!!.add(AudioSongs(data, title, album, artist))
            }
            //Collections.shuffle(audioList)
            audioList!!.shuffle()
        }
        val songCount = cursor!!.count
        cursor.close()
        return songCount
    }

    private fun playAudio(audioIndex: Int) {
        try {
            //Check is service is active
            if (!serviceBound) {

                initialSongIndex = audioIndex
                Log.i("1 Song start index", "initialSongIndex = $initialSongIndex")
                //end

                //Store Serializable audioList to SharedPreferences
                val storage = StorageUtil(applicationContext)
                storage.storeAudio(audioList)
                storage.storeAudioIndex(audioIndex)
                val playerIntent = Intent(this, MediaPlayerService::class.java)
                //playerIntent.putExtra("StartIndex", initialSongIndex)

                ContextCompat.startForegroundService(this, playerIntent)
                //startService(playerIntent)
                bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            } else {
                Log.i("2 Song start index", "initialSongIndex = $initialSongIndex")

                //Store the new audioIndex to SharedPreferences
                val storage = StorageUtil(applicationContext)
                storage.storeAudioIndex(audioIndex)

                //Service is active
                //Send a broadcast to the service -> PLAY_NEW_AUDIO
                val broadcastIntent = Intent(Broadcast_PLAY_NEW_AUDIO)

                sendBroadcast(broadcastIntent)
            }
        } catch (e: NullPointerException)
        {
            Log.e("Testing Error", "Main Activity PlayAudio Error = NullPointerException")
            myShowErrorDlg("Error = " + e.message)
        }

        this.title = "Playing song " + (audioIndex + 1) + " of " + audioList?.size
    }

    private fun myShowErrorDlg(errMsg: String) {
        // build alert dialog
        val dialogBuilder = AlertDialog.Builder(this)

        // set message of alert dialog
        dialogBuilder.setMessage("Populate Music Folder with MP3 songs and try again.")
            // if the dialog is cancelable
            .setCancelable(false)
            // positive button text and action
            .setPositiveButton("Close App", DialogInterface.OnClickListener {
                    _, _ -> finish()
            })
        // negative button text and action
//            .setNegativeButton("Continue", DialogInterface.OnClickListener {
//                    dialog, id -> dialog.cancel()
//            })

        // create dialog box
        val alert = dialogBuilder.create()
        // set title for alert dialog box
        alert.setTitle(errMsg)
        // show alert dialog
        alert.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action ?: return
        when (action) {
            MediaPlayerService.CLOSE_ACTION -> exit()
        }
    }

    private fun exit() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false

            //service is active
            player!!.stopSelf()
            Toast.makeText(this@MainActivity, "exit() - Service not bound", Toast.LENGTH_SHORT).show()
        }

        stopService(Intent(this, MediaPlayerService::class.java))
        finish()
    }

    //menu
    ///////////////////////////////////////////////////////////
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if (id == R.id.action_settings) {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            startActivity(intent)
            return true
        }
        if (id == R.id.action_shuffle) {

            if (audioList != null && audioList!!.size > 1) {

                //todo - stop service before changing list order
                if (serviceBound) {
                    unbindService(serviceConnection)
                    serviceBound = false
                    //service is active
                    player!!.stopSelf()
                }

                //Collections.shuffle(audioList)
                audioList!!.shuffle()

                // update RecyclerView
                mAdapter.clear()
                audioList?.let { mAdapter.addItems(it) }
                mAdapter.update()

            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    //Service code
    /////////////////////////////////////////////////

    //Binding this Client to the AudioPlayer Service
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder: MediaPlayerService.LocalBinder = service as MediaPlayerService.LocalBinder
            player = binder.service
            serviceBound = true
            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putBoolean("serviceStatus", serviceBound)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            Toast.makeText(this@MainActivity, "onDestroy() - Service is still bound", Toast.LENGTH_SHORT).show()
            Log.i("Testing info","onDestroy() - Service is still bound")
        } else {
            Toast.makeText(this@MainActivity, "onDestroy() - Service not bound", Toast.LENGTH_SHORT).show()
            Log.i("Testing info","onDestroy() - Service not bound")
        }


//        if (serviceBound) {
//            unbindService(serviceConnection)
//            //service is active
//            player!!.stopSelf()
//        }
    }
}