package com.bhavyadigital.everydaynotes

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

data class Note(var id:Long,var title:String,var body:String,var category:String,var pinned:Boolean,var updated:Long)

class MainActivity:AppCompatActivity(){
 private val prefs by lazy{getSharedPreferences("notes",0)}
 private val notes=mutableListOf<Note>(); private val shown=mutableListOf<Note>()
 private var category="All"; private lateinit var adapter:NoteAdapter
 override fun onCreate(b:Bundle?){
  if(prefs.getBoolean("dark",false)) androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
  super.onCreate(b); setContentView(R.layout.activity_main); load()
  adapter=NoteAdapter(shown,{edit(it)},{remove(it)},{share(it)},{pin(it)})
  findViewById<RecyclerView>(R.id.notesList).apply{layoutManager=LinearLayoutManager(this@MainActivity);adapter=this@MainActivity.adapter}
  findViewById<Button>(R.id.addButton).setOnClickListener{edit(null)}
  findViewById<Button>(R.id.themeButton).setOnClickListener{prefs.edit().putBoolean("dark",!prefs.getBoolean("dark",false)).apply();recreate()}
  val search=findViewById<EditText>(R.id.searchInput)
  search.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int){}
   override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){refresh()}
   override fun afterTextChanged(e:android.text.Editable?){}
  })
  categories(); refresh()
 }
 private fun refresh(){
  val q=findViewById<EditText>(R.id.searchInput).text.toString().lowercase()
  shown.clear();shown.addAll(notes.filter{(category=="All"||it.category.equals(category,true))&&(q.isBlank()||it.title.lowercase().contains(q)||it.body.lowercase().contains(q)||it.category.lowercase().contains(q))}.sortedWith(compareByDescending<Note>{it.pinned}.thenByDescending{it.updated}))
  adapter.notifyDataSetChanged();findViewById<TextView>(R.id.countText).text="${shown.size} notes"
 }
 private fun categories(){
  val bar=findViewById<LinearLayout>(R.id.categoryBar);bar.removeAllViews()
  (listOf("All")+notes.map{it.category}.filter{it.isNotBlank()}.distinct().sorted()).forEach{c->
   Button(this).apply{text=c;setOnClickListener{category=c;refresh()}}.also{bar.addView(it)}
  }
 }
 private fun edit(n:Note?){
  val v=LayoutInflater.from(this).inflate(R.layout.dialog_note,null)
  val t=v.findViewById<EditText>(R.id.titleInput);val body=v.findViewById<EditText>(R.id.bodyInput);val cat=v.findViewById<EditText>(R.id.categoryInput)
  t.setText(n?.title?:"");body.setText(n?.body?:"");cat.setText(n?.category?:"General")
  AlertDialog.Builder(this).setTitle(if(n==null)"New note" else "Edit note").setView(v).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->
   if(t.text.toString().isBlank()&&body.text.toString().isBlank()){Toast.makeText(this,"Write something first.",Toast.LENGTH_SHORT).show();return@setPositiveButton}
   val now=System.currentTimeMillis()
   if(n==null)notes.add(Note(now,t.text.toString().trim(),body.text.toString().trim(),cat.text.toString().trim().ifBlank{"General"},false,now))
   else{n.title=t.text.toString().trim();n.body=body.text.toString().trim();n.category=cat.text.toString().trim().ifBlank{"General"};n.updated=now}
   save();categories();refresh()
  }.show()
 }
 private fun remove(n:Note){AlertDialog.Builder(this).setTitle("Delete note?").setMessage("This note will be removed from this device.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->notes.remove(n);save();categories();refresh()}.show()}
 private fun pin(n:Note){n.pinned=!n.pinned;n.updated=System.currentTimeMillis();save();refresh()}
 private fun share(n:Note){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"${n.title}\n\n${n.body}")},"Share note"))}
 private fun save(){val a=JSONArray();notes.forEach{n->a.put(JSONObject().apply{put("id",n.id);put("title",n.title);put("body",n.body);put("category",n.category);put("pinned",n.pinned);put("updated",n.updated)})};prefs.edit().putString("data",a.toString()).apply()}
 private fun load(){prefs.getString("data",null)?.let{runCatching{val a=JSONArray(it);for(i in 0 until a.length()){val o=a.getJSONObject(i);notes.add(Note(o.getLong("id"),o.getString("title"),o.getString("body"),o.optString("category","General"),o.optBoolean("pinned"),o.optLong("updated")))}}}}
}

class NoteAdapter(private val items:List<Note>,val edit:(Note)->Unit,val del:(Note)->Unit,val share:(Note)->Unit,val pin:(Note)->Unit):RecyclerView.Adapter<NoteAdapter.H>(){
 class H(v:View):RecyclerView.ViewHolder(v){val t:TextView=v.findViewById(R.id.noteTitle);val b:TextView=v.findViewById(R.id.noteBody);val m:TextView=v.findViewById(R.id.noteMeta);val p:TextView=v.findViewById(R.id.pinMark);val e:Button=v.findViewById(R.id.editButton);val d:Button=v.findViewById(R.id.deleteButton);val s:Button=v.findViewById(R.id.shareButton)}
 override fun onCreateViewHolder(p:android.view.ViewGroup,t:Int)=H(LayoutInflater.from(p.context).inflate(R.layout.item_note,p,false))
 override fun onBindViewHolder(h:H,i:Int){val n=items[i];h.t.text=n.title.ifBlank{"Untitled note"};h.b.text=n.body.ifBlank{"No note text"};h.m.text="${n.category} • "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(n.updated));h.p.visibility=if(n.pinned)View.VISIBLE else View.GONE;h.p.setOnClickListener{pin(n)};h.e.setOnClickListener{edit(n)};h.d.setOnClickListener{del(n)};h.s.setOnClickListener{share(n)}}
 override fun getItemCount()=items.size
}
