package com.example.whatsappsewa2

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class Contact(
    var name: String,
    var mobile: String,
    var sent: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private lateinit var messageBox: EditText
    private lateinit var searchBox: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var countsText: TextView

    private val contacts = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    private val prefs by lazy {
        getSharedPreferences("sewa_sender_new", Context.MODE_PRIVATE)
    }

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

    private val pickContact =
        registerForActivityResult(
            ActivityResultContracts.PickContact()
        ) { uri ->
            uri?.let {
                readSelectedContact(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        messageBox = findViewById(R.id.etMessage)
        searchBox = findViewById(R.id.etSearch)
        recyclerView = findViewById(R.id.recyclerView)
        countsText = findViewById(R.id.tvCounts)

        messageBox.setText(
            prefs.getString("message", "")
        )

        loadContacts()

        adapter = ContactAdapter()

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestContactsPermission.launch(
                    Manifest.permission.READ_CONTACTS
                )
            } else {
                pickContact.launch(null)
            }
        }

        messageBox.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    prefs.edit()
                        .putString("message", s.toString())
                        .apply()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )

        searchBox.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    adapter.notifyDataSetChanged()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )

        updateCounts()
    }

    private fun readSelectedContact(uri: Uri) {

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->

            if (!cursor.moveToFirst()) {
                return
            }

            val id =
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.Contacts._ID
                    )
                )

            val name =
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.Contacts.DISPLAY_NAME
                    )
                )

            val hasPhone =
                cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    )
                ) > 0

            if (!hasPhone) {

                Toast.makeText(
                    this,
                    "इस Contact में मोबाइल नंबर नहीं है",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            val numbers = mutableListOf<String>()

            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(id),
                null
            )?.use { phoneCursor ->

                while (phoneCursor.moveToNext()) {

                    val number =
                        phoneCursor.getString(
                            phoneCursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            )
                        )

                    if (!numbers.contains(number)) {
                        numbers.add(number)
                    }
                }
            }

            when {

                numbers.isEmpty() -> {

                    Toast.makeText(
                        this,
                        "मोबाइल नंबर नहीं मिला",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                numbers.size == 1 -> {

                    addPhoneContact(
                        name,
                        numbers[0]
                    )
                }

                else -> {

                    AlertDialog.Builder(this)
                        .setTitle("कौन-सा नंबर जोड़ना है?")
                        .setItems(
                            numbers.toTypedArray()
                        ) { _, which ->

                            addPhoneContact(
                                name,
                                numbers[which]
                            )
                        }
                        .setNegativeButton(
                            "Cancel",
                            null
                        )
                        .show()
                }
            }
        }
    }

    private fun normalize(raw: String): String {

        return raw
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
    }

    private fun sameNumber(
        first: String,
        second: String
    ): Boolean {

        fun lastTen(number: String): String {

            return number
                .replace("+91", "")
                .removePrefix("0")
                .takeLast(10)
        }

        return lastTen(first) == lastTen(second)
    }

    private fun addPhoneContact(
        name: String,
        rawNumber: String
    ) {

        val mobile = normalize(rawNumber)

        if (
            contacts.any {
                sameNumber(it.mobile, mobile)
            }
        ) {

            Toast.makeText(
                this,
                "यह Contact पहले से मौजूद है",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        contacts.add(
            Contact(
                name = name,
                mobile = mobile,
                sent = false
            )
        )

        saveContacts()

        adapter.notifyDataSetChanged()

        updateCounts()

        Toast.makeText(
            this,
            "$name Contact में जोड़ दिया गया",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showEdit(index: Int) {

        val box = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                40,
                10,
                40,
                10
            )
        }

        val nameInput =
            EditText(this).apply {

                hint = "नाम"

                setText(
                    contacts[index].name
                )
            }

        val phoneInput =
            EditText(this).apply {

                hint = "मोबाइल नंबर"

                inputType =
                    android.text.InputType.TYPE_CLASS_PHONE

                setText(
                    contacts[index].mobile
                )
            }
