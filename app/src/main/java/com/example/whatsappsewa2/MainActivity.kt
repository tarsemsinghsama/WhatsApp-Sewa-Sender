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


        Thread {

            try {

                val connection =
                    URL(CSV_URL)
                        .openConnection()
                            as HttpURLConnection


                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    20000

                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0"
                )


                val responseCode =
                    connection.responseCode


                if (responseCode != 200) {

                    throw Exception(
                        "Server response: $responseCode"
                    )
                }


                val reader =
                    BufferedReader(
                        InputStreamReader(
                            connection.inputStream,
                            Charsets.UTF_8
                        )
                    )


                val csvText =
                    reader.use {
                        it.readText()
                    }


                connection.disconnect()


                val rows =
                    parseCsv(csvText)


                if (rows.size <= 1) {

                    throw Exception(
                        "WEB_DATA में कोई data नहीं मिला"
                    )
                }


                val newPeople =
                    parseAttendanceRows(rows)


                if (newPeople.isEmpty()) {

                    throw Exception(
                        "Attendance data पढ़ा नहीं जा सका"
                    )
                }


                runOnUiThread {

                    mergeAttendanceData(
                        newPeople
                    )

                    saveAttendance()

                    adapter.notifyDataSetChanged()

                    updateCounts()


                    updateButton.isEnabled =
                        true

                    updateButton.text =
                        "🔄 हाज़िरी Update करें"


                    Toast.makeText(
                        this,
                        "${newPeople.size} सेवादारों की हाज़िरी Update हो गई",
                        Toast.LENGTH_LONG
                    ).show()
                }


            } catch (e: Exception) {

                runOnUiThread {

                    updateButton.isEnabled =
                        true

                    updateButton.text =
                        "🔄 हाज़िरी Update करें"


                    Toast.makeText(
                        this,
                        "Update नहीं हो पाया: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }


    // =========================================================
    // CSV PARSER
    // =========================================================

    private fun parseCsv(
        text: String
    ): List<List<String>> {

        val result =
            mutableListOf<List<String>>()

        var row =
            mutableListOf<String>()

        val cell =
            StringBuilder()

        var insideQuotes = false

        var i = 0


        while (i < text.length) {

            val c =
                text[i]


            if (c == '"') {

                if (
                    insideQuotes &&
                    i + 1 < text.length &&
                    text[i + 1] == '"'
                ) {

                    cell.append('"')

                    i += 2

                    continue

                } else {

                    insideQuotes =
                        !insideQuotes
                }

            } else if (
                c == ',' &&
                !insideQuotes
            ) {

                row.add(
                    cell.toString()
                )

                cell.clear()

            } else if (
                (c == '\n' || c == '\r') &&
                !insideQuotes
            ) {

                if (
                    c == '\r' &&
                    i + 1 < text.length &&
                    text[i + 1] == '\n'
                ) {

                    i++
                }


                row.add(
                    cell.toString()
                )

                cell.clear()


                if (
                    row.any {
                        it.isNotBlank()
                    }
                ) {

                    result.add(
                        row
                            .map {
                                it.trim()
                            }
                    )
                }


                row =
                    mutableListOf()

            } else {

                cell.append(c)
            }


            i++
        }


        if (
            cell.isNotEmpty() ||
            row.isNotEmpty()
        ) {

            row.add(
                cell.toString()
            )

            if (
                row.any {
                    it.isNotBlank()
                }
            ) {

                result.add(
                    row.map {
                        it.trim()
                    }
                )
            }
        }


        return result
    }


    // =========================================================
    // PARSE ATTENDANCE
    // =========================================================

    private fun parseAttendanceRows(
        rows: List<List<String>>
    ): List<AttendancePerson> {

        if (rows.isEmpty()) {
            return emptyList()
        }


        val header =
            rows[0].map {
                it
                    .trim()
                    .removePrefix("\uFEFF")
                    .lowercase()
            }


        fun column(
            vararg names: String
        ): Int {

            for (name in names) {

                val index =
                    header.indexOf(
                        name.lowercase()
                    )

                if (index >= 0) {
                    return index
                }
            }

            return -1
        }


        // -----------------------------------------------------
        // COLUMN POSITIONS
        // -----------------------------------------------------

        val badgeIndex =
            column(
                "badge",
                "badge no",
                "badge_no"
            ).let {
                if (it >= 0) it else 0
            }


        val nameIndex =
            column(
                "name",
                "first name"
            ).let {
                if (it >= 0) it else 1
            }


        val fatherIndex =
            column(
                "father/husband",
                "father husband",
                "father",
                "husband"
            ).let {
                if (it >= 0) it else 2
            }


        val dayIndex =
            column(
                "day",
                "दिन"
            ).let {
                if (it >= 0) it else 3
            }


        val nightIndex =
            column(
                "night",
                "रात"
            ).let {
                if (it >= 0) it else 4
            }


        val saturdayIndex =
            column(
                "saturday",
                "शनिवार"
            ).let {
                if (it >= 0) it else 5
            }


        val sundayIndex =
            column(
                "sunday",
                "रविवार"
            ).let {
                if (it >= 0) it else 6
            }


        val lipaiIndex =
            column(
                "lipai",
                "लिपाई"
            ).let {
                if (it >= 0) it else 7
            }


        val beasIndex =
            column(
                "beas",
                "ब्यास"
            ).let {
                if (it >= 0) it else 8
            }


        val result =
            mutableListOf<AttendancePerson>()


        // -----------------------------------------------------
        // DATA ROWS
        // -----------------------------------------------------

        for (
            rowIndex in 1 until rows.size
        ) {

            val row =
                rows[rowIndex]


            if (row.isEmpty()) {
                continue
            }


            fun value(
                index: Int
            ): String {

                return if (
                    index >= 0 &&
                    index < row.size
                ) {

                    row[index].trim()

                } else {

                    ""
                }
            }


            val badge =
                value(badgeIndex)


            val name =
                value(nameIndex)


            val father =
                value(fatherIndex)


            if (
                badge.isBlank() &&
                name.isBlank()
            ) {
                continue
            }


            result.add(

                AttendancePerson(

                    badge =
                        badge,

                    name =
                        name,

                    fatherHusband =
                        father,

                    day =
                        value(dayIndex)
                            .toIntOrNull()
                            ?: 0,

                    night =
                        value(nightIndex)
                            .toIntOrNull()
                            ?: 0,

                    saturday =
                        value(saturdayIndex)
                            .toIntOrNull()
                            ?: 0,

                    sunday =
                        value(sundayIndex)
                            .toIntOrNull()
                            ?: 0,

                    lipai =
                        value(lipaiIndex)
                            .toIntOrNull()
                            ?: 0,

                    beas =
                        value(beasIndex)
                            .toIntOrNull()
                            ?: 0
                )
            )
        }


        return result
    }


    // =========================================================
    // MERGE DATA
    // =========================================================

    private fun mergeAttendanceData(
        newPeople: List<AttendancePerson>
    ) {

        val oldPhones =
            people.associateBy(
                {
                    it.badge
                },
                {
                    it.mobile
                }
            )


        val oldSent =
            people.associateBy(
                {
                    it.badge
                },
                {
                    it.sent
                }
            )


        people.clear()


        newPeople.forEach { person ->

            person.mobile =
                oldPhones[
                    person.badge
                ] ?: getSavedPhone(
                    person.badge
                )


            person.sent =
                oldSent[
                    person.badge
                ] ?: false


            people.add(
                person
            )
        }
    }


    // =========================================================
    // SAVE ATTENDANCE
    // =========================================================

    private fun saveAttendance() {

        val editor =
            prefs.edit()


        people.forEach { person ->

            editor.putString(
                "phone_${person.badge}",
                person.mobile
            )

            editor.putBoolean(
                "sent_${person.badge}",
                person.sent
            )
        }


        editor.apply()
    }


    // =========================================================
    // LOAD SAVED ATTENDANCE
    // =========================================================

    private fun loadSavedAttendance() {

        // Saved attendance is optional.
        // Phone numbers are loaded when Sheet is refreshed.

        people.clear()
    }


    // =========================================================
    // GET SAVED PHONE
    // =========================================================

    private fun getSavedPhone(
        badge: String
    ): String {

        return prefs.getString(
            "phone_$badge",
            ""
        ) ?: ""
    }


    // =========================================================
    // PHONE CONTACT
    // =========================================================

    private fun readSelectedContact(
        uri: Uri
    ) {

        if (pendingPersonIndex < 0) {
            return
        }


        if (
            pendingPersonIndex >=
            people.size
        ) {
            return
        }


        val projection =
            arrayOf(
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


            val numbers =
                mutableListOf<String>()


            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(id),
                null
            )?.use { phoneCursor ->

                while (
                    phoneCursor.moveToNext()
                ) {

                    val number =
                        phoneCursor.getString(
                            phoneCursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            )
                        )


                    if (
                        !numbers.contains(
                            number
                        )
                    ) {

                        numbers.add(
                            number
                        )
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

                    savePhone(
                        pendingPersonIndex,
                        numbers[0]
                    )
                }


                else -> {

                    AlertDialog.Builder(this)

                        .setTitle(
                            "कौन-सा नंबर जोड़ना है?"
                        )

                        .setItems(
                            numbers.toTypedArray()
                        ) { _, which ->

                            savePhone(
                                pendingPersonIndex,
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


    // =========================================================
    // SAVE PHONE
    // =========================================================

    private fun savePhone(
        index: Int,
        rawNumber: String
    ) {

        if (
            index < 0 ||
            index >= people.size
        ) {
            return
        }


        val number =
            normalizePhone(
                rawNumber
            )


        people[index].mobile =
            number


        prefs.edit()
            .putString(
                "phone_${people[index].badge}",
                number
            )
            .apply()


        adapter.notifyDataSetChanged()


        Toast.makeText(
            this,
            "${people[index].name} का मोबाइल नंबर सेव हो गया",
            Toast.LENGTH_SHORT
        ).show()
    }


    // =========================================================
    // NORMALIZE PHONE
    // =========================================================

    private fun normalizePhone(
        raw: String
    ): String {

        return raw
            .replace(
                Regex("[^0-9+]"),
                ""
            )
    }


    // =========================================================
    // ADD PHONE MANUALLY
    // =========================================================

    private fun showManualPhone(
        index: Int
    ) {

        val input =
            EditText(this).apply {

                hint =
                    "मोबाइल नंबर"

                inputType =
                    android.text.InputType.TYPE_CLASS_PHONE

                setText(
                    people[index].mobile
                )
            }


        AlertDialog.Builder(this)

            .setTitle(
                "मोबाइल नंबर जोड़ें"
            )

            .setView(input)

            .setNegativeButton(
                "Cancel",
                null
            )

            .setPositiveButton(
                "Save"
            ) { _, _ ->

                val number =
                    input.text
                        .toString()
                        .trim()


                if (number.isEmpty()) {

                    Toast.makeText(
                        this,
                        "मोबाइल नंबर डालें",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }


                savePhone(
                    index,
                    number
                )
            }

            .show()
    }


    // =========================================================
    // PHONE CONTACT OR MANUAL
    // =========================================================

    private fun choosePhoneMethod(
        index: Int
    ) {

        AlertDialog.Builder(this)

            .setTitle(
                "मोबाइल नंबर जोड़ें"
            )

            .setItems(
                arrayOf(
                    "📱 Phone Contacts से चुनें",
                    "⌨️ मोबाइल नंबर खुद लिखें"
                )
            ) { _, which ->

                pendingPersonIndex =
                    index


                if (which == 0) {

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

                } else {

                    showManualPhone(
                        index
                    )
                }
            }

            .setNegativeButton(
                "Cancel",
                null
            )

            .show()
    }


    // =========================================================
    // WHATSAPP
    // =========================================================

    private fun sendWhatsApp(
        index: Int
    ) {

        if (
            index < 0 ||
            index >= people.size
        ) {
            return
        }


        val person =
            people[index]


        if (
            person.mobile.isBlank()
        ) {

            Toast.makeText(
                this,
                "पहले मोबाइल नंबर जोड़ें",
                Toast.LENGTH_SHORT
            ).show()

            choosePhoneMethod(
                index
            )

            return
        }


        var number =
            person.mobile
                .replace(
                    Regex("[^0-9]"),
                    ""
                )


        // -----------------------------------------------------
        // INDIAN NUMBER
        // -----------------------------------------------------

        if (
            number.length == 10
        ) {

            number =
                "91$number"

        } else if (
            number.startsWith("0") &&
            number.length == 11
        ) {

            number =
                "91" +
                    number.substring(1)
        }


        // -----------------------------------------------------
        // MESSAGE
        // -----------------------------------------------------

        val finalMessage =
            "${person.name} जी\n" +
            "राधा स्वामी जी 🙏🏼\n\n" +
            "अब तक कि आपकी हाज़िरी:\n\n" +
            "दिन - ${person.day}\n" +
            "रात - ${person.night}\n" +
            "शनिवार - ${person.saturday}\n" +
            "रविवार - ${person.sunday}\n" +
            "लिपाई - ${person.lipai}\n" +
            "ब्यास - ${person.beas}"


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


            person.sent =
                true


            prefs.edit()
                .putBoolean(
                    "sent_${person.badge}",
                    true
                )
                .apply()


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


    // =========================================================
    // SEARCH
    // =========================================================

    private fun filteredIndexes():
            List<Int> {

        val query =
            searchBox.text
                .toString()
                .trim()
                .lowercase()


        if (query.isEmpty()) {

            return people.indices.toList()
        }


        return people.indices.filter { index ->

            val person =
                people[index]


            person.badge
                .lowercase()
                .contains(query) ||

            person.name
                .lowercase()
                .contains(query) ||

            person.fatherHusband
                .lowercase()
                .contains(query)
        }
    }


    // =========================================================
    // COUNTS
    // =========================================================

    private fun updateCounts() {

        val total =
            people.size


        val sent =
            people.count {
                it.sent
            }


        val pending =
            total - sent


        countsText.text =
            "कुल: $total    भेजे: $sent    बाकी: $pending"
    }


    // =========================================================
    // RECYCLER ADAPTER
    // =========================================================

    inner class AttendanceAdapter :
        RecyclerView.Adapter<
            AttendanceAdapter.AttendanceViewHolder
        >() {


        inner class AttendanceViewHolder(
            val layout: LinearLayout
        ) : RecyclerView.ViewHolder(
            layout
        )


        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): AttendanceViewHolder {

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


            return AttendanceViewHolder(
                layout
            )
        }


        override fun getItemCount():
                Int {

            return filteredIndexes().size
        }


        override fun onBindViewHolder(
            holder: AttendanceViewHolder,
            position: Int
        ) {

            val indexes =
                filteredIndexes()


            val actualIndex =
                indexes[position]


            val person =
                people[actualIndex]


            val layout =
                holder.layout


            layout.removeAllViews()


            // -------------------------------------------------
            // NAME
            // -------------------------------------------------

            val nameText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        "${person.name} जी"

                    textSize =
                        19f

                    setPadding(
                        0,
                        0,
                        0,
                        6
                    )
                }


            // -------------------------------------------------
            // BADGE
            // -------------------------------------------------

            val badgeText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        "Badge: ${person.badge}"

                    textSize =
                        14f
                }


            // -------------------------------------------------
            // FATHER / HUSBAND
            // -------------------------------------------------

            val fatherText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        "Father/Husband: ${person.fatherHusband}"

                    textSize =
                        14f

                    setPadding(
                        0,
                        4,
                        0,
                        8
                    )
                }


            // -------------------------------------------------
            // ATTENDANCE
            // -------------------------------------------------

            val attendanceText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        "दिन: ${person.day}    रात: ${person.night}\n" +
                        "शनिवार: ${person.saturday}    रविवार: ${person.sunday}\n" +
                        "लिपाई: ${person.lipai}    ब्यास: ${person.beas}"

                    textSize =
                        15f

                    setPadding(
                        0,
                        4,
                        0,
                        8
                    )
                }


            // -------------------------------------------------
            // PHONE
            // -------------------------------------------------

            val phoneText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        if (
                            person.mobile.isBlank()
                        ) {

                            "📱 मोबाइल नंबर: नहीं जोड़ा गया"

                        } else {

                            "📱 ${person.mobile}"
                        }

                    textSize =
                        15f

                    setPadding(
                        0,
                        4,
                        0,
                        8
                    )
                }


            // -------------------------------------------------
            // STATUS
            // -------------------------------------------------

            val statusText =
                TextView(
                    this@MainActivity
                ).apply {

                    text =
                        if (person.sent) {

                            "स्थिति: WhatsApp खोला गया"

                        } else {

                            "स्थिति: बाकी"
                        }

                    textSize =
                        14f

                    setPadding(
                        0,
                        4,
                        0,
                        8
                    )
                }


            // -------------------------------------------------
            // BUTTON ROW
            // -------------------------------------------------

            val buttonRow =
                LinearLayout(
                    this@MainActivity
                ).apply {

                    orientation =
                        LinearLayout.HORIZONTAL
                }


            // -------------------------------------------------
            // SEND BUTTON
            // -------------------------------------------------

            val sendButton =
                Button(
                    this@MainActivity
                ).apply {

                    text =
                        "SEND"

                    setOnClickListener {

                        sendWhatsApp(
                            actualIndex
                        )
                    }
                }


            // -------------------------------------------------
            // PHONE BUTTON
            // -------------------------------------------------

            val phoneButton =
                Button(
                    this@MainActivity
                ).apply {

                    text =
                        if (
                            person.mobile.isBlank()
                        ) {

                            "📱 फोन जोड़ें"

                        } else {

                            "✏️ फोन बदलें"
                        }


                    setOnClickListener {

                        choosePhoneMethod(
                            actualIndex
                        )
                    }
                }


            buttonRow.addView(
                sendButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )


            buttonRow.addView(
                phoneButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )


            // -------------------------------------------------
            // ADD TO LAYOUT
            // -------------------------------------------------

            layout.addView(
                nameText
            )

            layout.addView(
                badgeText
            )

            layout.addView(
                fatherText
            )

            layout.addView(
                attendanceText
            )

            layout.addView(
                phoneText
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


// =============================================================
// EDIT TEXT EXTENSION
// =============================================================

private fun EditText.setOnTextChanged(
    action: () -> Unit
) {

    addTextChangedListener(

        object :
            android.text.TextWatcher {

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

                action()
            }


            override fun afterTextChanged(
                s: android.text.Editable?
            ) {
            }
        }
    )
}
