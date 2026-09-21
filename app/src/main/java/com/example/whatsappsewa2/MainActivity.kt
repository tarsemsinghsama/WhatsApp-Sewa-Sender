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
        getSharedPreferences(
            "sewa_sender_new",
            Context.MODE_PRIVATE
        )
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
                        .putString(
                            "message",
                            s.toString()
                        )
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

    // ---------------------------------------------------------
    // PHONE CONTACT PICKER
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // PHONE NUMBER
    // ---------------------------------------------------------

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
                sameNumber(
                    it.mobile,
                    mobile
                )
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

    // ---------------------------------------------------------
    // EDIT CONTACT
    // ---------------------------------------------------------

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

        box.addView(
            nameInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        box.addView(
            phoneInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(this)
            .setTitle("Contact Edit करें")
            .setView(box)
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Save"
            ) { _, _ ->

                val newName =
                    nameInput.text
                        .toString()
                        .trim()

                val newNumber =
                    normalize(
                        phoneInput.text
                            .toString()
                            .trim()
                    )

                if (
                    newName.isEmpty() ||
                    newNumber.isEmpty()
                ) {

                    Toast.makeText(
                        this,
                        "नाम और मोबाइल नंबर दोनों भरें",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                val duplicate =
                    contacts.indices.any { i ->

                        i != index &&
                            sameNumber(
                                contacts[i].mobile,
                                newNumber
                            )
                    }

                if (duplicate) {

                    Toast.makeText(
                        this,
                        "यह मोबाइल नंबर पहले से मौजूद है",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                contacts[index].name =
                    newName

                contacts[index].mobile =
                    newNumber

                contacts[index].sent =
                    false

                saveContacts()

                adapter.notifyDataSetChanged()

                updateCounts()
            }
            .show()
    }

    // ---------------------------------------------------------
    // DELETE CONTACT
    // ---------------------------------------------------------

    private fun deleteContact(index: Int) {

        AlertDialog.Builder(this)
            .setTitle("Contact हटाएँ?")
            .setMessage(
                "${contacts[index].name} को हटाना है?"
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Delete"
            ) { _, _ ->

                contacts.removeAt(index)

                saveContacts()

                adapter.notifyDataSetChanged()

                updateCounts()
            }
            .show()
    }

    // ---------------------------------------------------------
    // WHATSAPP
    // ---------------------------------------------------------

    private fun sendWhatsApp(index: Int) {

        val contact = contacts[index]

        var number =
            contact.mobile
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")

        if (number.startsWith("0")) {
            number = number.removePrefix("0")
        }

        if (!number.startsWith("+")) {
            number = "+91$number"
        }

        val commonMessage =
            messageBox.text
                .toString()
                .trim()

        if (commonMessage.isEmpty()) {

            Toast.makeText(
                this,
                "पहले Daily Message लिखें",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val finalMessage =
            "${contact.name} जी\n" +
            "राधा स्वामी जी 🙏🏼\n" +
            commonMessage

        val encodedMessage =
            URLEncoder.encode(
                finalMessage,
                "UTF-8"
            )

        val uri =
            Uri.parse(
                "https://wa.me/$number?text=$encodedMessage"
            )

        try {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )

            contact.sent = true

            saveContacts()

            adapter.notifyDataSetChanged()

            updateCounts()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "WhatsApp खोलने में समस्या हुई",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------------------------------------------------------
    // SAVE CONTACTS
    // ---------------------------------------------------------

    private fun saveContacts() {

        val array = JSONArray()

        contacts.forEach { contact ->

            val obj = JSONObject()

            obj.put(
                "name",
                contact.name
            )

            obj.put(
                "mobile",
                contact.mobile
            )

            obj.put(
                "sent",
                contact.sent
            )

            array.put(obj)
        }

        prefs.edit()
            .putString(
                "contacts",
                array.toString()
            )
            .apply()
    }

    // ---------------------------------------------------------
    // LOAD CONTACTS
    // ---------------------------------------------------------

    private fun loadContacts() {

        contacts.clear()

        val saved =
            prefs.getString(
                "contacts",
                null
            ) ?: return

        try {

            val array =
                JSONArray(saved)

            for (i in 0 until array.length()) {

                val obj =
                    array.getJSONObject(i)

                contacts.add(
                    Contact(
                        name =
                            obj.optString(
                                "name"
                            ),

                        mobile =
                            obj.optString(
                                "mobile"
                            ),

                        sent =
                            obj.optBoolean(
                                "sent",
                                false
                            )
                    )
                )
            }

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Contacts data पढ़ने में समस्या",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------------------------------------------------------
    // COUNTS
    // ---------------------------------------------------------

    private fun updateCounts() {

        val total =
            contacts.size

        val sent =
            contacts.count {
                it.sent
            }

        val pending =
            total - sent

        countsText.text =
            "कुल: $total    भेजे: $sent    बाकी: $pending"
    }

    // ---------------------------------------------------------
    // CONTACT ADAPTER
    // ---------------------------------------------------------

    inner class ContactAdapter :
        RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        inner class ContactViewHolder(
            val layout: LinearLayout
        ) : RecyclerView.ViewHolder(layout)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ContactViewHolder {

            val layout =
                LinearLayout(
                    this@MainActivity
                ).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    setPadding(
                        20,
                        20,
                        20,
                        20
                    )

                    background =
                        getDrawable(
                            android.R.drawable.dialog_holo_light_frame
                        )
                }

            return ContactViewHolder(layout)
        }

        override fun getItemCount(): Int {

            val query =
                searchBox.text
                    .toString()
                    .trim()
                    .lowercase()

            return if (query.isEmpty()) {

                contacts.size

            } else {

                contacts.count {

                    it.name
                        .lowercase()
                        .contains(query) ||

                        it.mobile
                            .contains(query)
                }
            }
        }

        private fun getContactAt(
            position: Int
        ): Int {

            val query =
                searchBox.text
                    .toString()
                    .trim()
                    .lowercase()

            if (query.isEmpty()) {
                return position
            }

            val filtered =
                contacts.filter {

                    it.name
                        .lowercase()
                        .contains(query) ||

                        it.mobile
                            .contains(query)
                }

            return contacts.indexOf(
                filtered[position]
            )
        }

        override fun onBindViewHolder(
            holder: ContactViewHolder,
            position: Int
        ) {

            val actualIndex =
                getContactAt(position)

            val contact =
                contacts[actualIndex]

            val layout =
                holder.layout

            layout.removeAllViews()

            val nameText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        "${contact.name} जी"

                    textSize = 18f

                    setPadding(
                        0,
                        0,
                        0,
                        8
                    )
                }

            val numberText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        contact.mobile

                    textSize = 15f
                }

            val statusText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        if (contact.sent) {
                            "स्थिति: भेजा गया"
                        } else {
                            "स्थिति: बाकी"
                        }

                    textSize = 14f

                    setPadding(
                        0,
                        8,
                        0,
                        8
                    )
                }

            val buttonRow =
                LinearLayout(
                    this@MainActivity
                ).apply {

                    orientation =
                        LinearLayout.HORIZONTAL
                }

            val sendButton =
                Button(
                    this@MainActivity
                ).apply {

                    text = "SEND"

                    setOnClickListener {

                        sendWhatsApp(
                            actualIndex
                        )
                    }
                }

            val editButton =
                Button(
                    this@MainActivity
                ).apply {

                    text = "Edit"

                    setOnClickListener {

                        showEdit(
                            actualIndex
                        )
                    }
                }

            val deleteButton =
                Button(
                    this@MainActivity
                ).apply {

                    text = "Delete"

                    setOnClickListener {

                        deleteContact(
                            actualIndex
                        )
                    }
                }

            buttonRow.addView(
                sendButton
            )

            buttonRow.addView(
                editButton
            )

            buttonRow.addView(
                deleteButton
            )

            layout.addView(
                nameText
            )

            layout.addView(
                numberText
            )

            layout.addView(
                statusText
            )

            layout.addView(
                buttonRow
            )
        }
    }
}
