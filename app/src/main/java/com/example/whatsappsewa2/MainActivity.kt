package com.example.whatsappsewa2

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

// =========================================================
// GOOGLE SHEET WEB_DATA CSV LINK
// =========================================================

private const val CSV_URL =
    "https://docs.google.com/spreadsheets/d/e/2PACX-1vT7VlT602hfD5eX7t_o5IKhkQdyVTfhLziXZV05t3zKBGi-GQElsArnLhT3Ljq_3MInCTUkkXxdDTDd/pub?gid=1120077762&single=true&output=csv"


// =========================================================
// ATTENDANCE DATA
// =========================================================

data class AttendancePerson(

    var badge: String,

    var name: String,

    var fatherHusband: String,

    var day: Int,

    var night: Int,

    var saturday: Int,

    var sunday: Int,

    var lipai: Int,

    var beas: Int,

    var mobile: String = "",

    var sent: Boolean = false
)


// =========================================================
// MAIN ACTIVITY
// =========================================================

class MainActivity : AppCompatActivity() {

    private lateinit var searchBox: EditText

    private lateinit var recyclerView: RecyclerView

    private lateinit var countsText: TextView

    private lateinit var updateButton: Button

    private val people =
        mutableListOf<AttendancePerson>()

    private lateinit var adapter: AttendanceAdapter


    // ---------------------------------------------------------
    // LOCAL STORAGE
    // ---------------------------------------------------------

    private val prefs by lazy {

        getSharedPreferences(
            "sewa_sender_attendance",
            Context.MODE_PRIVATE
        )
    }


    // ---------------------------------------------------------
    // CONTACT PERMISSION
    // ---------------------------------------------------------

    private var pendingPersonIndex = -1


    private val requestContactsPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                pickContact.launch(null)

            } else {

                Toast.makeText(
                    this,
                    "Contacts permission जरूरी है",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    // ---------------------------------------------------------
    // CONTACT PICKER
    // ---------------------------------------------------------

    private val pickContact =
        registerForActivityResult(
            ActivityResultContracts.PickContact()
        ) { uri ->

            uri?.let {

                readSelectedContact(it)
            }
        }


    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )


        searchBox =
            findViewById(
                R.id.etSearch
            )


        recyclerView =
            findViewById(
                R.id.recyclerView
            )


        countsText =
            findViewById(
                R.id.tvCounts
            )


        updateButton =
            findViewById(
                R.id.btnUpdate
            )


        // -----------------------------------------------------
        // RECYCLER VIEW
        // -----------------------------------------------------

        adapter =
            AttendanceAdapter()

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        recyclerView.adapter =
            adapter


        // -----------------------------------------------------
        // LOAD SAVED DATA
        // -----------------------------------------------------

        loadSavedAttendance()


        // -----------------------------------------------------
        // UPDATE BUTTON
        // -----------------------------------------------------

        updateButton.setOnClickListener {

            updateAttendanceFromSheet()
        }


        // -----------------------------------------------------
        // SEARCH
        // -----------------------------------------------------

        searchBox.setOnTextChanged {

            adapter.notifyDataSetChanged()
        }


        updateCounts()
    }


    // =========================================================
    // GOOGLE SHEET UPDATE
    // =========================================================

    private fun updateAttendanceFromSheet() {

        updateButton.isEnabled = false

        updateButton.text =
            "⏳ हाज़िरी Update हो रही है..."
