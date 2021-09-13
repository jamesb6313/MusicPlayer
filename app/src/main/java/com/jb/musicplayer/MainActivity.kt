package com.jb.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.jb.musicplayer.databinding.ActivityMainBinding
import java.util.ArrayList

class MainActivity : AppCompatActivity() {
    var audioList: ArrayList<AudioSongs>? = null
    private lateinit var layout: View
    private lateinit var binding: ActivityMainBinding
    private val MY_PERMISSIONS_READ_STORAGE = 42

    lateinit var mAdapter: MySongRecyclerViewAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission: ", "Granted")
            } else {
                Log.i("Permission: ", "Denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        layout = binding.mainLayout
        setContentView(view)

        audioList = ArrayList<AudioSongs>()
        askForPermissions()
    }


    private fun askForPermissions() {
        @PermissionChecker.PermissionResult val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                MY_PERMISSIONS_READ_STORAGE)
        } else {
            initializeView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_PERMISSIONS_READ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeView()
            } else {
                Toast.makeText(this@MainActivity, R.string.permissions_please, Toast.LENGTH_SHORT).show()
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
                //playAudio(position)
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
                val title =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))


                // Save to audioList
                audioList!!.add(AudioSongs(title, album, artist))
            }
            //Collections.shuffle(audioList)
            audioList!!.shuffle()
        }
        val songCount = cursor!!.count
        cursor.close()
        return songCount
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
}