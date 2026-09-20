package com.example.whatsappsewa

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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
        getSharedPreferences("sewa_sender", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messageBox = findViewById(R.id.etMessage)
        searchBox = findViewById(R.id.etSearch)
        recyclerView = findViewById(R.id.recyclerView)
        countsText = findViewById(R.id.tvCounts)

        messageBox.setText(prefs.getString("message", ""))

        loadContacts()

        adapter = ContactAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            showContactDialog(null)
        }

        messageBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                prefs.edit().putString("message", s.toString()).apply()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.notifyDataSetChanged()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        updateCounts()
    }

    private fun showContactDialog(editIndex: Int?) {

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 10, 40, 10)

        val nameInput = EditText(this)
        nameInput.hint = "नाम"

        val mobileInput = EditText(this)
        mobileInput.hint = "मोबाइल नंबर"
        mobileInput.inputType = android.text.InputType.TYPE_CLASS_PHONE

        layout.addView(nameInput)
        layout.addView(mobileInput)

        if (editIndex != null) {
            nameInput.setText(contacts[editIndex].name)
            mobileInput.setText(contacts[editIndex].mobile)
        }

        val title =
            if (editIndex == null) "नया Contact जोड़ें"
            else "Contact Edit करें"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(
                if (editIndex == null) "Add" else "Save"
            ) { _, _ ->

                val name = nameInput.text.toString().trim()
                val mobile = mobileInput.text.toString()
                    .replace(" ", "")
                    .replace("-", "")
                    .trim()

                if (name.isEmpty() || mobile.isEmpty()) {
                    Toast.makeText(
                        this,
                        "नाम और मोबाइल नंबर भरें",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (editIndex == null) {
                    contacts.add(Contact(name, mobile, false))
                } else {
                    contacts[editIndex].name = name
                    contacts[editIndex].mobile = mobile
                    contacts[editIndex].sent = false
                }

                saveContacts()
                adapter.notifyDataSetChanged()
                updateCounts()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteContact(index: Int) {

        AlertDialog.Builder(this)
            .setTitle("Contact Delete करें?")
            .setMessage(contacts[index].name)
            .setPositiveButton("Delete") { _, _ ->

                contacts.removeAt(index)
                saveContacts()
                adapter.notifyDataSetChanged()
                updateCounts()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openWhatsApp(index: Int) {

        val contact = contacts[index]

        var number = contact.mobile
            .replace(" ", "")
            .replace("-", "")
            .replace("+", "")

        if (number.startsWith("0")) {
            number = number.substring(1)
        }

        if (number.length == 10) {
            number = "91$number"
        }

        val dailyMessage = messageBox.text.toString().trim()

        if (dailyMessage.isEmpty()) {
            Toast.makeText(
                this,
                "पहले आज का संदेश लिखें",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val finalMessage =
            "${contact.name} जी\n" +
            "राधा स्वामी जी 🙏🏼\n" +
            dailyMessage

        val encoded =
            URLEncoder.encode(finalMessage, "UTF-8")

        val uri =
            Uri.parse("https://wa.me/$number?text=$encoded")

        val intent = Intent(Intent.ACTION_VIEW, uri)

        // सामान्य WhatsApp को target करने की कोशिश
        intent.setPackage("com.whatsapp")

        try {
            startActivity(intent)

            Toast.makeText(
                this,
                "WhatsApp खुल गया। Message देखकर Send करें।",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {

            // अगर सामान्य WhatsApp उपलब्ध नहीं है
            intent.setPackage(null)

            try {
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(
                    this,
                    "WhatsApp नहीं मिला।",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun markSent(index: Int) {

        contacts[index].sent = true
        saveContacts()
        adapter.notifyDataSetChanged()
        updateCounts()
    }

    private fun saveContacts() {

        val array = JSONArray()

        for (contact in contacts) {

            val obj = JSONObject()

            obj.put("name", contact.name)
            obj.put("mobile", contact.mobile)
            obj.put("sent", contact.sent)

            array.put(obj)
        }

        prefs.edit()
            .putString("contacts", array.toString())
            .apply()
    }

    private fun loadContacts() {

        contacts.clear()

        val data = prefs.getString("contacts", null)
            ?: return

        try {

            val array = JSONArray(data)

            for (i in 0 until array.length()) {

                val obj = array.getJSONObject(i)

                contacts.add(
                    Contact(
                        obj.getString("name"),
                        obj.getString("mobile"),
                        obj.optBoolean("sent", false)
                    )
                )
            }

        } catch (_: Exception) {
        }
    }

    private fun updateCounts() {

        val total = contacts.size
        val sent = contacts.count { it.sent }
        val pending = total - sent

        countsText.text =
            "Total: $total    Sent: $sent    Pending: $pending"
    }

    inner class ContactAdapter :
        RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        inner class ContactViewHolder(
            val layout: LinearLayout
        ) : RecyclerView.ViewHolder(layout)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ContactViewHolder {

            val layout = LinearLayout(this@MainActivity)

            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(12, 16, 12, 16)

            return ContactViewHolder(layout)
        }

        override fun getItemCount(): Int {

            val search = searchBox.text.toString().trim()

            if (search.isEmpty()) {
                return contacts.size
            }

            return contacts.count {
                it.name.contains(search, ignoreCase = true)
            }
        }

        private fun getContact(position: Int): Pair<Contact, Int> {

            val search = searchBox.text.toString().trim()

            if (search.isEmpty()) {
                return Pair(contacts[position], position)
            }

            val filtered =
                contacts.withIndex()
                    .filter {
                        it.value.name.contains(
                            search,
                            ignoreCase = true
                        )
                    }

            return Pair(
                filtered[position].value,
                filtered[position].index
            )
        }

        override fun onBindViewHolder(
            holder: ContactViewHolder,
            position: Int
        ) {

            val (contact, realIndex) =
                getContact(position)

            val layout = holder.layout
            layout.removeAllViews()

            val nameText = TextView(this@MainActivity)

            nameText.text =
                "${position + 1}. ${contact.name} जी"

            nameText.textSize = 18f
            nameText.setTypeface(null, android.graphics.Typeface.BOLD)

            val mobileText = TextView(this@MainActivity)

            mobileText.text =
                "📱 ${contact.mobile}"

            mobileText.textSize = 14f

            val statusText = TextView(this@MainActivity)

            statusText.text =
                if (contact.sent)
                    "🟢 Sent"
                else
                    "🟡 Pending"

            statusText.textSize = 14f

            val buttons = LinearLayout(this@MainActivity)
            buttons.orientation = LinearLayout.HORIZONTAL

            val sendButton = Button(this@MainActivity)
            sendButton.text = "SEND"

            sendButton.setOnClickListener {
                openWhatsApp(realIndex)
            }

            val editButton = Button(this@MainActivity)
            editButton.text = "Edit"

            editButton.setOnClickListener {
                showContactDialog(realIndex)
            }

            val deleteButton = Button(this@MainActivity)
            deleteButton.text = "Delete"

            deleteButton.setOnClickListener {
                deleteContact(realIndex)
            }

            val markButton = Button(this@MainActivity)
            markButton.text = "Mark Sent"

            markButton.setOnClickListener {
                markSent(realIndex)
            }

            buttons.addView(sendButton)
            buttons.addView(editButton)
            buttons.addView(deleteButton)
            buttons.addView(markButton)

            layout.addView(nameText)
            layout.addView(mobileText)
            layout.addView(statusText)
            layout.addView(buttons)

            val divider = View(this@MainActivity)

            divider.setBackgroundColor(
                android.graphics.Color.LTGRAY
            )

            layout.addView(divider)
        }
    }
}
