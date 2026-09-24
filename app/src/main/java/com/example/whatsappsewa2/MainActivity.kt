package com.example.whatsappsewa2

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class AttendancePerson(
    val badge: String,
    val name: String,
    val father: String,
    val day: Int,
    val night: Int,
    val saturday: Int,
    val sunday: Int,
    val lipai: Int,
    val beas: Int
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CSV_URL =
            "https://docs.google.com/spreadsheets/d/e/2PACX-1vT7VlT602hfD5eX7t_o5IKhkQdyVTfhLziXZV05t3zKBGi-GQElsArnLhT3Ljq_3MInCTUkkXxdDTDd/pub?gid=1120077762&single=true&output=csv"

        private const val PREFS = "sewa_sender"
        private const val PHONES = "phones"
        private const val SENT = "sent"
    }

    private lateinit var searchBox: EditText
    private lateinit var countsText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PersonAdapter
    private lateinit var prefs: SharedPreferences

    private var people = mutableListOf<AttendancePerson>()
    private var query = ""

    private val contactPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data?.data != null) {
                readContact(result.data!!.data!!)
            }
        }

    private val contactPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openContactPicker()
            else Toast.makeText(this, "Contacts permission जरूरी है।", Toast.LENGTH_SHORT).show()
        }

    private var phoneBadgeForPicker: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        searchBox = findViewById(R.id.etSearch)
        countsText = findViewById(R.id.tvCounts)
        recyclerView = findViewById(R.id.recyclerView)

        adapter = PersonAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            updateFromSheet()
        }

        searchBox.addTextChangedListener(SimpleTextWatcher {
            query = it
            adapter.notifyDataSetChanged()
            updateCounts()
        })

        updateCounts()
        updateFromSheet()
    }

    private fun updateFromSheet() {
        Toast.makeText(this, "हाज़िरी Update हो रही है...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val connection = URL(CSV_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 20000

                val csv = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()

                val rows = parseCsv(csv)
                val newPeople = parsePeople(rows)

                runOnUiThread {
                    people = newPeople.toMutableList()
                    adapter.notifyDataSetChanged()
                    updateCounts()
                    Toast.makeText(
                        this,
                        "हाज़िरी Update हो गई: ${people.size} सदस्य",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Update नहीं हो पाया। Internet/Sheet check करें।",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun parsePeople(rows: List<List<String>>): List<AttendancePerson> {
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map {
            it.trim().lowercase(Locale.getDefault())
        }

        fun indexOf(vararg names: String): Int {
            for (name in names) {
                val i = header.indexOf(name.lowercase(Locale.getDefault()))
                if (i >= 0) return i
            }
            return -1
        }

        val badgeI = indexOf("badge", "badge no", "badge no.")
        val nameI = indexOf("name", "first name", "sewadar name")
        val fatherI = indexOf(
            "father/husband",
            "father husband",
            "father name",
            "father/husband name",
            "father"
        )
        val dayI = indexOf("day", "दिन")
        val nightI = indexOf("night", "रात")
        val satI = indexOf("saturday", "शनिवार")
        val sunI = indexOf("sunday", "रविवार")
        val lipaiI = indexOf("lipai", "लिपाई")
        val beasI = indexOf("beas", "ब्यास")

        if (badgeI < 0 || nameI < 0) return emptyList()

        fun cell(row: List<String>, i: Int): String =
            if (i >= 0 && i < row.size) row[i].trim() else ""

        fun number(row: List<String>, i: Int): Int {
            val value = cell(row, i).replace(",", "").trim()
            return value.toDoubleOrNull()?.toInt() ?: 0
        }

        return rows.drop(1).mapNotNull { row ->
            val badge = cell(row, badgeI)
            val name = cell(row, nameI)
            if (badge.isBlank() || name.isBlank()) return@mapNotNull null

            AttendancePerson(
                badge = badge,
                name = name,
                father = cell(row, fatherI),
                day = number(row, dayI),
                night = number(row, nightI),
                saturday = number(row, satI),
                sunday = number(row, sunI),
                lipai = number(row, lipaiI),
                beas = number(row, beasI)
            )
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0

        while (i < text.length) {
            val c = text[i]

            when {
                c == '"' -> {
                    if (quoted && i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = !quoted
                    }
                }

                c == ',' && !quoted -> {
                    row.add(cell.toString())
                    cell.clear()
                }

                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString())
                    cell.clear()
                    if (row.any { it.isNotBlank() }) result.add(row.toList())
                    row.clear()
                }

                else -> cell.append(c)
            }
            i++
        }

        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            if (row.any { it.isNotBlank() }) result.add(row.toList())
        }

        return result
    }

    private fun updateCounts() {
        val filtered = filteredPeople()
        val sent = getSentBadges().count { badge ->
            filtered.any { it.badge == badge }
        }
        countsText.text = "कुल: ${filtered.size}    भेजे: $sent    बाकी: ${filtered.size - sent}"
    }

    private fun filteredPeople(): List<AttendancePerson> {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) return people

        return people.filter {
            it.badge.lowercase(Locale.getDefault()).contains(q) ||
            it.name.lowercase(Locale.getDefault()).contains(q) ||
            it.father.lowercase(Locale.getDefault()).contains(q)
        }
    }

    private fun getPhone(badge: String): String =
        prefs.getString("$PHONES:$badge", "") ?: ""

    private fun savePhone(badge: String, phone: String) {
        prefs.edit().putString("$PHONES:$badge", phone).apply()
    }

    private fun getSentBadges(): Set<String> =
        prefs.getStringSet(SENT, emptySet()) ?: emptySet()

    private fun markSent(badge: String) {
        val set = getSentBadges().toMutableSet()
        set.add(badge)
        prefs.edit().putStringSet(SENT, set).apply()
    }

    private fun showPhoneDialog(person: AttendancePerson) {
        val input = EditText(this)
        input.hint = "मोबाइल नंबर"
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        input.setText(getPhone(person.badge))

        AlertDialog.Builder(this)
            .setTitle("${person.name} जी का नंबर")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.isNotBlank()) {
                    savePhone(person.badge, phone)
                    adapter.notifyDataSetChanged()
                }
            }
            .setNeutralButton("Contacts से चुनें") { _, _ ->
                phoneBadgeForPicker = person.badge
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openContactPicker()
                } else {
                    contactPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openContactPicker() {
        val intent = Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
        contactPicker.launch(intent)
    }

    private fun readContact(uri: Uri) {
        val badge = phoneBadgeForPicker ?: return

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number = cursor.getString(0) ?: ""
                savePhone(badge, number)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "मोबाइल नंबर Save हो गया।", Toast.LENGTH_SHORT).show()
            }
        }
        phoneBadgeForPicker = null
    }

    private fun sendWhatsApp(person: AttendancePerson) {
        val rawPhone = getPhone(person.badge)
        val phone = rawPhone.filter { it.isDigit() }

        if (phone.isBlank()) {
            showPhoneDialog(person)
            return
        }

        val number = when {
            phone.startsWith("91") && phone.length >= 12 -> phone
            phone.length == 10 -> "91$phone"
            else -> phone
        }

        val eligibilityMessage =
            if (person.day >= 23 && person.night >= 23 && person.beas >= 12) {
                "धन्यवाद जी, नियमानुसार हाज़िरी को पूरा करने पर आप बैज, दर्शन व प्रशादी टोकन प्राप्त करने के पात्र पाए गए हैं।"
            } else {
                "धन्यवाद जी, नियमानुसार हाज़िरी को पूरा करने के बाद ही आप बैज, दर्शन व प्रशादी टोकन प्राप्त करने के पात्र होंगे।"
            }

        val message = buildString {
            append(person.name)
            append(" जी\n")
            if (person.father.isNotBlank()) {
                append("राधा स्वामी जी 🙏🏼\n\n")
            } else {
                append("राधा स्वामी जी 🙏🏼\n\n")
            }

            append("अब तक आपकी हाज़िरी:\n\n")
            append("दिन - ${person.day}\n")
            append("रात - ${person.night}\n")
            append("शनिवार - ${person.saturday}\n")
            append("रविवार - ${person.sunday}\n")
            append("लिपाई - ${person.lipai}\n")
            append("ब्यास - ${person.beas}\n\n\n")
            append(eligibilityMessage)
        }

        try {
            val url = "https://wa.me/$number?text=${URLEncoder.encode(message, "UTF-8")}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            markSent(person.badge)
            updateCounts()
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp नहीं खुल पाया।", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class PersonAdapter :
        RecyclerView.Adapter<PersonAdapter.PersonHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonHolder {
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 14, 14, 14)
            }
            return PersonHolder(root)
        }

        override fun onBindViewHolder(holder: PersonHolder, position: Int) {
            holder.bind(filteredPeople()[position])
        }

        override fun getItemCount(): Int = filteredPeople().size

        inner class PersonHolder(private val root: LinearLayout) :
            RecyclerView.ViewHolder(root) {

            fun bind(person: AttendancePerson) {
                root.removeAllViews()

                val title = TextView(root.context).apply {
                    text = "${person.name} जी"
                    textSize = 18f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }

                val meta = TextView(root.context).apply {
                    text = "Badge: ${person.badge}\nFather/Husband: ${person.father.ifBlank { "-" }}"
                    textSize = 14f
                    setPadding(0, 4, 0, 8)
                }

                val attendance = TextView(root.context).apply {
                    text = "दिन ${person.day} | रात ${person.night} | ब्यास ${person.beas}"
                    textSize = 14f
                }

                val phone = TextView(root.context).apply {
                    val p = getPhone(person.badge)
                    text = if (p.isBlank()) "📱 मोबाइल नंबर: नहीं जोड़ा गया" else "📱 $p"
                    textSize = 14f
                    setPadding(0, 6, 0, 8)
                }

                val buttons = LinearLayout(root.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val phoneButton = Button(root.context).apply {
                    text = "📱 नंबर"
                    setOnClickListener { showPhoneDialog(person) }
                }

                val sendButton = Button(root.context).apply {
                    text = "SEND"
                    setOnClickListener { sendWhatsApp(person) }
                }

                buttons.addView(
                    phoneButton,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                buttons.addView(
                    sendButton,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )

                root.addView(title)
                root.addView(meta)
                root.addView(attendance)
                root.addView(phone)
                root.addView(buttons)

                val lp = root.layoutParams
                    ?: RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                root.layoutParams = lp
            }
        }
    }

    private class SimpleTextWatcher(
        private val callback: (String) -> Unit
    ) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            callback(s?.toString() ?: "")
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}







