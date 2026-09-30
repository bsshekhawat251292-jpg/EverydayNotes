package com.bhavyadigital.everydaynotes

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

data class Note(
    var id: Long,
    var title: String,
    var body: String,
    var category: String,
    var pinned: Boolean,
    var updated: Long
)

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("notes", MODE_PRIVATE) }
    private val notes = mutableListOf<Note>()
    private val shown = mutableListOf<Note>()
    private var category = "All"
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        if (prefs.getBoolean("dark", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        load()

        adapter = NoteAdapter(
            shown,
            { edit(it) },
            { remove(it) },
            { share(it) },
            { pin(it) }
        )

        findViewById<RecyclerView>(R.id.notesList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<Button>(R.id.addButton).setOnClickListener {
            edit(null)
        }

        findViewById<Button>(R.id.themeButton).setOnClickListener {
            val enabled = !prefs.getBoolean("dark", false)
            prefs.edit().putBoolean("dark", enabled).commit()
            recreate()
        }

        val search = findViewById<EditText>(R.id.searchInput)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                refresh()
            }

            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        categories()
        refresh()
    }

    private fun refresh() {
        val q = findViewById<EditText>(R.id.searchInput).text.toString().trim().lowercase()

        shown.clear()
        shown.addAll(
            notes
                .filter {
                    (category == "All" || it.category.equals(category, true)) &&
                        (
                            q.isBlank() ||
                            it.title.lowercase().contains(q) ||
                            it.body.lowercase().contains(q) ||
                            it.category.lowercase().contains(q)
                        )
                }
                .sortedWith(
                    compareByDescending<Note> { it.pinned }
                        .thenByDescending { it.updated }
                )
        )

        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.countText).text = "${shown.size} notes"
    }

    private fun categories() {
        val bar = findViewById<LinearLayout>(R.id.categoryBar)
        bar.removeAllViews()

        (listOf("All") +
            notes.map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        ).forEach { c ->
            Button(this).apply {
                text = c
                setOnClickListener {
                    category = c
                    refresh()
                }
            }.also { bar.addView(it) }
        }
    }

    private fun edit(note: Note?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_note, null)

        val titleInput = view.findViewById<EditText>(R.id.titleInput)
        val bodyInput = view.findViewById<EditText>(R.id.bodyInput)
        val categoryInput = view.findViewById<EditText>(R.id.categoryInput)

        titleInput.setText(note?.title.orEmpty())
        bodyInput.setText(note?.body.orEmpty())
        categoryInput.setText(note?.category ?: "General")

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (note == null) "New note" else "Edit note")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                val body = bodyInput.text.toString().trim()
                val savedCategory = categoryInput.text.toString().trim().ifBlank { "General" }

                if (title.isBlank() && body.isBlank()) {
                    Toast.makeText(
                        this,
                        "Please enter a title or note text.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val now = System.currentTimeMillis()

                if (note == null) {
                    notes.add(
                        Note(
                            id = now,
                            title = title,
                            body = body,
                            category = savedCategory,
                            pinned = false,
                            updated = now
                        )
                    )
                } else {
                    note.title = title
                    note.body = body
                    note.category = savedCategory
                    note.updated = now
                }

                if (save()) {
                    categories()
                    refresh()
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        this,
                        "Could not save the note. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    private fun remove(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Delete note?")
            .setMessage("This note will be removed from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                notes.remove(note)
                save()
                categories()
                refresh()
            }
            .show()
    }

    private fun pin(note: Note) {
        note.pinned = !note.pinned
        note.updated = System.currentTimeMillis()
        save()
        refresh()
    }

    private fun share(note: Note) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.body}")
                },
                "Share note"
            )
        )
    }

    private fun save(): Boolean {
        return runCatching {
            val array = JSONArray()

            notes.forEach { note ->
                array.put(
                    JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("body", note.body)
                        put("category", note.category)
                        put("pinned", note.pinned)
                        put("updated", note.updated)
                    }
                )
            }

            // commit() is intentional here: confirm the local write before
            // telling the user that the note has been saved.
            prefs.edit()
                .putString("data", array.toString())
                .commit()
        }.getOrDefault(false)
    }

    private fun load() {
        val raw = prefs.getString("data", null) ?: return

        runCatching {
            val array = JSONArray(raw)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                notes.add(
                    Note(
                        id = obj.getLong("id"),
                        title = obj.optString("title"),
                        body = obj.optString("body"),
                        category = obj.optString("category", "General").ifBlank { "General" },
                        pinned = obj.optBoolean("pinned", false),
                        updated = obj.optLong("updated", System.currentTimeMillis())
                    )
                )
            }
        }
    }
}

class NoteAdapter(
    private val items: List<Note>,
    private val edit: (Note) -> Unit,
    private val delete: (Note) -> Unit,
    private val share: (Note) -> Unit,
    private val pin: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.noteTitle)
        val body: TextView = view.findViewById(R.id.noteBody)
        val meta: TextView = view.findViewById(R.id.noteMeta)
        val pinMark: TextView = view.findViewById(R.id.pinMark)
        val editButton: Button = view.findViewById(R.id.editButton)
        val deleteButton: Button = view.findViewById(R.id.deleteButton)
        val shareButton: Button = view.findViewById(R.id.shareButton)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
        Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_note, parent, false)
        )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val note = items[position]

        holder.title.text = note.title.ifBlank { "Untitled note" }
        holder.body.text = note.body.ifBlank { "No note text" }
        holder.meta.text =
            "${note.category} • " +
                DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT
                ).format(Date(note.updated))

        holder.pinMark.visibility = if (note.pinned) View.VISIBLE else View.GONE
        holder.pinMark.setOnClickListener { pin(note) }
        holder.editButton.setOnClickListener { edit(note) }
        holder.deleteButton.setOnClickListener { delete(note) }
        holder.shareButton.setOnClickListener { share(note) }
    }

    override fun getItemCount() = items.size
}
