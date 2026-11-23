package com.greenair.universalremote

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import org.json.JSONArray
import org.json.JSONObject
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.hardware.ConsumerIrManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.*
import android.view.View
import android.view.MotionEvent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.hypot

class MainActivity:Activity() {
  private lateinit var ir:ConsumerIrManager
  private lateinit var page:LinearLayout
  private lateinit var status:TextView
  private val handler=Handler(Looper.getMainLooper())
  private var profile="RC6"
  private data class RemoteItem(val id:String,val name:String,val type:String)
  private val remotes=mutableListOf<RemoteItem>()
  private var selectedRemoteId="philips"
  private lateinit var header:TextView
  private var philipsActive=true
  private var toggle=0
  private var searching=false
  private var idx=0
  private val codes=buildList { for(a in 0..255)for(c in listOf(0x45,0x46,0x47,0x44,0x40,0x43,0x07,0x15,0x09,0x16,0x19,0x0D,0x0C,0x18,0x5E,0x08,0x1C,0x5A,0x42,0x52,0x12,0x14,0x0F,0x57,0x17)) add(a to c) }
  override fun onCreate(savedInstanceState:Bundle?) {
    super.onCreate(savedInstanceState)
    window.statusBarColor=Color.rgb(12,13,16)
    window.navigationBarColor=Color.rgb(12,13,16)
    ir=getSystemService(CONSUMER_IR_SERVICE) as ConsumerIrManager
    profile=getSharedPreferences("remote",MODE_PRIVATE).getString("profile","RC6")?:"RC6"
    loadRemotes()
    val outer=col().apply {setPadding(dp(12),dp(6),dp(12),0);setBackgroundColor(0xFF090C0D.toInt())}
    outer.addView(label("GREENAIR UNIVERSAL REMOTE",18f,Color.WHITE).apply { setPadding(0,dp(14),0,dp(5)) })
    header=label(selectedRemote().name,15f,Color.LTGRAY).apply { setPadding(0,0,0,dp(15)) }
    outer.addView(header)
    val tabs=LinearLayout(this)
    listOf("REMOTE","REMOTES","CODES").forEach { title ->
      val active=title=="REMOTE"
      val tab=TextView(this).apply {
        text=title;textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
        background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
          intArrayOf(if(active)0xFF17482A.toInt() else 0xFF343A3D.toInt(),
                     if(active)0xFF071F10.toInt() else 0xFF24292D.toInt())).apply {
          cornerRadius=dp(26).toFloat()
          setStroke(dp(if(active)2 else 1),if(active)0xFF5BE875.toInt() else 0xFF616A70.toInt())
        }
        setOnClickListener {
          stop()
          when(title) {
            "CODES" -> savedCodes()
            "REMOTES" -> manageRemotes()
            else -> showSelectedRemote()
          }
        }
      }
      tabs.addView(tab,LinearLayout.LayoutParams(0,dp(52),1f).apply {setMargins(dp(3),dp(2),dp(3),dp(2))})
    }
    outer.addView(tabs)
    status=label(if(ir.hasIrEmitter()) "●  IR transmitter detected ✓" else "IR transmitter not detected",14f,0xFF65DD75.toInt())
    outer.addView(status)
    page=col()
    val sc=ScrollView(this).apply {isVerticalScrollBarEnabled=false;addView(page)}
    outer.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
    setContentView(outer)
    showSelectedRemote()
  }
  private fun remote(philips:Boolean) {
    philipsActive=philips
    page.removeAllViews()
    if(philips) profile="RC6"
    page.addView(RemoteFace(philips),LinearLayout.LayoutParams(-1,-2).apply {
      setMargins(dp(2),dp(8),dp(2),dp(6))
    })
    if(!philips) {
      page.addView(label("TCL / Universal TV code search",16f,Color.WHITE))
      page.addView(btn("START / STOP AUTO SEARCH",0xFF45474D.toInt(),14f){if(searching)stop()else start()},LinearLayout.LayoutParams(-1,dp(55)))
      page.addView(btn("TV RESPONDED - STOP & SAVE",0xFF45474D.toInt(),14f){saveCode()},LinearLayout.LayoutParams(-1,dp(55)))
    }
    page.addView(label("v1.0.0  •  Saved remote codes",12f,Color.LTGRAY))
  }

  private inner class RemoteFace(private val philips:Boolean):View(this@MainActivity) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val hit=mutableListOf<Pair<RectF,String>>()
    override fun onMeasure(w:Int,h:Int){
      val width=MeasureSpec.getSize(w)
      setMeasuredDimension(width,(width*1.48f).toInt())
    }
    private fun rect(c:Canvas,l:Float,t:Float,r:Float,b:Float,rad:Float,top:Int,bottom:Int,stroke:Int=0xFF5D676C.toInt()){
      val area=RectF(l,t,r,b)
      paint.style=Paint.Style.FILL
      paint.shader=LinearGradient(l,t,l,b,top,bottom,Shader.TileMode.CLAMP)
      c.drawRoundRect(area,rad,rad,paint)
      paint.shader=null
      paint.style=Paint.Style.STROKE;paint.strokeWidth=0.85f;paint.color=stroke
      c.drawRoundRect(area,rad,rad,paint);paint.style=Paint.Style.FILL
    }
    private fun text(c:Canvas,t:String,x:Float,y:Float,size:Float,color:Int=Color.WHITE,bold:Boolean=false){
      paint.style=Paint.Style.FILL;paint.color=color;paint.textAlign=Paint.Align.CENTER
      paint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
      paint.textSize=size
      c.drawText(t,x,y-(paint.ascent()+paint.descent())/2f,paint)
    }
    private fun button(c:Canvas,l:Float,t:Float,r:Float,b:Float,label:String,action:String,
                       color:Int=0xFF3A4045.toInt(),size:Float=17f,rad:Float=19f){
      rect(c,l,t,r,b,rad,color,0xFF20262A.toInt())
      text(c,label,(l+r)/2,(t+b)/2,size)
      hit.add(RectF(l,t,r,b) to action)
    }
    override fun onDraw(canvas:Canvas){
      super.onDraw(canvas);hit.clear()
      canvas.save();canvas.scale(width/400f,height/592f)
      rect(canvas,3f,3f,397f,589f,36f,0xFF22292B.toInt(),0xFF0F1718.toInt(),0xFF3A8E55.toInt())
      text(canvas,if(philips)"Philips Profile Active" else "Universal IR Profile",200f,33f,17f)
      paint.strokeWidth=1.3f;paint.color=0xFF42BE62.toInt()
      canvas.drawLine(42f,34f,100f,34f,paint);canvas.drawLine(300f,34f,358f,34f,paint)
      if(!philips){
        button(canvas,90f,43f,190f,72f,"RC6","PROFILE_RC6",0xFF23432C.toInt(),14f)
        button(canvas,210f,43f,310f,72f,"RC5","PROFILE_RC5",0xFF383D40.toInt(),14f)
      }
      button(canvas,34f,75f,140f,138f,"⏻","POWER",0xFFCB222A.toInt(),34f,21f)
      button(canvas,247f,76f,367f,136f,"→  SOURCE","SOURCE",0xFF424950.toInt(),16f)
      rect(canvas,35f,156f,119f,312f,26f,0xFF3A4146.toInt(),0xFF252C30.toInt())
      hit.add(RectF(35f,156f,119f,224f) to "VOL+")
      hit.add(RectF(35f,245f,119f,312f) to "VOL-")
      text(canvas,"+",77f,185f,34f);text(canvas,"VOL",77f,232f,15f);text(canvas,"−",77f,282f,34f)
      rect(canvas,281f,156f,365f,312f,26f,0xFF3A4146.toInt(),0xFF252C30.toInt())
      hit.add(RectF(281f,156f,365f,224f) to "CH+")
      hit.add(RectF(281f,245f,365f,312f) to "CH-")
      text(canvas,"+",323f,185f,34f);text(canvas,"CH",323f,232f,15f);text(canvas,"−",323f,282f,34f)
      button(canvas,135f,165f,265f,223f,"◁  MUTE","MUTE",0xFF394146.toInt(),17f)
      button(canvas,135f,245f,265f,303f,"⌂  HOME","HOME",0xFF394146.toInt(),17f)
      button(canvas,31f,333f,190f,385f,"☰  MENU","MENU",0xFF3D4449.toInt(),18f)
      button(canvas,210f,333f,369f,385f,"↶  BACK","BACK",0xFF3D4449.toInt(),18f)
      val cx=200f;val cy=483f;val ro=88f;val ri=45f
      for(i in 0..3){
        val start=225f+90f*i
        val path=Path()
        path.arcTo(RectF(cx-ro,cy-ro,cx+ro,cy+ro),start,88f)
        path.arcTo(RectF(cx-ri,cy-ri,cx+ri,cy+ri),start+88f,-88f)
        path.close()
        paint.shader=LinearGradient(0f,cy-ro,0f,cy+ro,0xFF454D53.toInt(),0xFF252D32.toInt(),Shader.TileMode.CLAMP)
        paint.style=Paint.Style.FILL;canvas.drawPath(path,paint);paint.shader=null
        paint.color=0xFF151B1D.toInt();paint.strokeWidth=2f;paint.style=Paint.Style.STROKE;canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL
      }
      paint.color=0xFF5AE578.toInt();canvas.drawCircle(cx,cy,44f,paint)
      paint.color=0xFF242C31.toInt();canvas.drawCircle(cx,cy,41f,paint)
      text(canvas,"OK",cx,cy,25f,Color.WHITE,true)
      text(canvas,"▲",cx,417f,23f);text(canvas,"▼",cx,549f,23f)
      text(canvas,"◀",134f,cy,23f);text(canvas,"▶",266f,cy,23f)
      hit.add(RectF(160f,443f,240f,523f) to "OK")
      hit.add(RectF(160f,393f,240f,440f) to "UP")
      hit.add(RectF(160f,525f,240f,575f) to "DOWN")
      hit.add(RectF(106f,443f,158f,523f) to "LEFT")
      hit.add(RectF(242f,443f,294f,523f) to "RIGHT")
      canvas.restore()
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{
      if(event.action==MotionEvent.ACTION_DOWN) return true
      if(event.action==MotionEvent.ACTION_UP){
        val x=event.x*400f/width;val y=event.y*592f/height
        val cmd=hit.lastOrNull{it.first.contains(x,y)}?.second
        when(cmd){
          "PROFILE_RC5"->{profile="RC5";saveCurrentProfile();invalidate()}
          "PROFILE_RC6"->{profile="RC6";saveCurrentProfile();invalidate()}
          null -> {}
          else -> send(cmd)
        }
        performClick()
      }
      return true
    }
    override fun performClick():Boolean {super.performClick();return true}
  }


  private fun loadRemotes() {
    remotes.clear()
    val prefs=getSharedPreferences("remote_collection",MODE_PRIVATE)
    val stored=prefs.getString("items",null)
    if(stored!=null) {
      try {
        val items=JSONArray(stored)
        for(i in 0 until items.length()) {
          val obj=items.getJSONObject(i)
          val id=obj.optString("id")
          val name=obj.optString("name").trim()
          val type=obj.optString("type")
          if(id.isNotBlank() && name.isNotBlank() && type in listOf("PHILIPS","RC6","RC5") && remotes.none{it.id==id}) {
            remotes.add(RemoteItem(id,name,type))
          }
        }
      } catch(e:Exception) {}
    }
    if(remotes.isEmpty()) {
      remotes.add(RemoteItem("philips","Philips 22PFT5403/98","PHILIPS"))
      remotes.add(RemoteItem("universal","Universal Remote","RC6"))
    }
    selectedRemoteId=prefs.getString("selected","philips")?:"philips"
    if(remotes.none{it.id==selectedRemoteId})selectedRemoteId=remotes.first().id
    saveRemotes()
  }
  private fun saveRemotes() {
    val json=JSONArray()
    remotes.forEach {r->
      json.put(JSONObject().put("id",r.id).put("name",r.name).put("type",r.type))
    }
    getSharedPreferences("remote_collection",MODE_PRIVATE).edit()
      .putString("items",json.toString()).putString("selected",selectedRemoteId).apply()
  }
  private fun selectedRemote():RemoteItem =
    remotes.firstOrNull{it.id==selectedRemoteId}?:remotes.first()

  private fun saveCurrentProfile() {
    val r=selectedRemote()
    if(r.type!="PHILIPS" && r.type!=profile){
      val i=remotes.indexOfFirst{it.id==r.id}
      if(i>=0) {
        remotes[i]=r.copy(type=profile)
        saveRemotes()
      }
    }
  }
  private fun showSelectedRemote() {
    val r=selectedRemote()
    header.text=r.name
    profile=if(r.type=="RC5")"RC5" else "RC6"
    remote(r.type=="PHILIPS")
  }
  private fun manageRemotes(){
    page.removeAllViews()
    page.addView(label("MY REMOTES",21f,Color.WHITE))
    page.addView(label("Select a remote, rename it, or add another.",14f,Color.LTGRAY))
    page.addView(btn("+  ADD REMOTE",0xFF255E38.toInt(),17f){addRemoteDialog()},
      LinearLayout.LayoutParams(-1,dp(58)).apply{setMargins(dp(6),dp(8),dp(6),dp(14))})
    remotes.toList().forEach { r->
      val wrapper=col().apply {
        setPadding(dp(8),dp(9),dp(8),dp(9))
        background=rounded(if(r.id==selectedRemoteId)0xFF234330.toInt() else 0xFF252B2F.toInt(),20)
      }
      val typeLabel=when(r.type){"PHILIPS"->"Philips • RC6";"RC5"->"Universal • RC5";else->"Universal • RC6"}
      wrapper.addView(label(r.name,17f,Color.WHITE))
      wrapper.addView(label(typeLabel,12f,Color.LTGRAY))
      val row=LinearLayout(this)
      row.addView(btn(if(r.id==selectedRemoteId)"SELECTED ✓" else "USE",0xFF296B3D.toInt(),13f) {
        selectedRemoteId=r.id;saveRemotes();showSelectedRemote()
      },LinearLayout.LayoutParams(0,dp(48),1.2f))
      row.addView(btn("RENAME",0xFF41484D.toInt(),13f){renameRemoteDialog(r)},
        LinearLayout.LayoutParams(0,dp(48),1.2f).apply{setMargins(dp(6),0,0,0)})
      row.addView(btn("DELETE",0xFF573439.toInt(),13f){deleteRemoteDialog(r)},
        LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(dp(6),0,0,0)})
      wrapper.addView(row)
      page.addView(wrapper,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(5),dp(4),dp(5),dp(8))})
    }
    page.addView(label("v1.0.0  •  Remotes saved on this phone",12f,Color.LTGRAY))
  }
  private fun nameInput(default:String=""):EditText =
    EditText(this).apply {
      setSingleLine(true);setText(default);selectAll()
      setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)
      hint="Remote name";setPadding(dp(17),dp(10),dp(17),dp(10))
    }

  private fun addRemoteDialog(){
    val input=nameInput()
    val labels=arrayOf("Philips (RC6)","Universal (RC6)","Universal (RC5)")
    val types=arrayOf("PHILIPS","RC6","RC5")
    var selected=0
    val container=col().apply {setPadding(dp(14),dp(4),dp(14),0);addView(input)}
    AlertDialog.Builder(this)
      .setTitle("Add new remote")
      .setView(container)
      .setSingleChoiceItems(labels,0){_:DialogInterface,which:Int->selected=which}
      .setNegativeButton("CANCEL",null)
      .setPositiveButton("ADD"){_,_->
        val name=input.text.toString().trim()
        if(name.isEmpty()) {
          Toast.makeText(this,"Enter a remote name",Toast.LENGTH_SHORT).show()
        } else {
          val id="remote_"+System.currentTimeMillis().toString()+"_"+remotes.size
          remotes.add(RemoteItem(id,name,types[selected]))
          selectedRemoteId=id
          saveRemotes();showSelectedRemote()
        }
      }.show()
  }
  private fun renameRemoteDialog(r:RemoteItem){
    val input=nameInput(r.name)
    val container=col().apply{setPadding(dp(14),dp(8),dp(14),0);addView(input)}
    AlertDialog.Builder(this).setTitle("Rename remote").setView(container)
      .setNegativeButton("CANCEL",null)
      .setPositiveButton("SAVE"){_,_->
        val name=input.text.toString().trim()
        if(name.isEmpty()){
          Toast.makeText(this,"Enter a remote name",Toast.LENGTH_SHORT).show()
        } else {
          val i=remotes.indexOfFirst{it.id==r.id}
          if(i>=0)remotes[i]=r.copy(name=name)
          saveRemotes()
          if(r.id==selectedRemoteId)header.text=name
          manageRemotes()
        }
      }.show()
  }
  private fun deleteRemoteDialog(r:RemoteItem){
    if(remotes.size<=1){
      Toast.makeText(this,"Keep at least one remote",Toast.LENGTH_SHORT).show()
      return
    }
    AlertDialog.Builder(this).setTitle("Delete remote?")
      .setMessage("Remove "+r.name+" from this phone?")
      .setNegativeButton("CANCEL",null)
      .setPositiveButton("DELETE"){_,_->
        remotes.removeAll{it.id==r.id}
        if(selectedRemoteId==r.id)selectedRemoteId=remotes.first().id
        saveRemotes();header.text=selectedRemote().name;manageRemotes()
      }.show()
  }
  private fun savedCodes(){
    page.removeAllViews()
    page.addView(label("SAVED CODES",20f,Color.WHITE))
    val brand=EditText(this).apply{hint="Brand / remote name";setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)}
    val code=EditText(this).apply{hint="Remote code";setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)}
    page.addView(brand);page.addView(code)
    val saved=col()
    val prefs=getSharedPreferences("saved_remote_codes",MODE_PRIVATE)
    fun refresh(){
      saved.removeAllViews()
      prefs.all.toSortedMap().forEach{(k,v)->saved.addView(btn(k+": "+v,0xFF35373B.toInt(),14f){brand.setText(k);code.setText(v.toString())})}
    }
    page.addView(btn("SAVE CODE",0xFF236737.toInt(),16f){
      val b=brand.text.toString().trim();val c=code.text.toString().trim()
      if(b.isNotEmpty()&&c.isNotEmpty()){prefs.edit().putString(b,c).apply();refresh()}
    },LinearLayout.LayoutParams(-1,dp(54)))
    page.addView(saved);refresh()
  }
  private fun send(name:String){
    if(!ir.hasIrEmitter())return
    val c=mapOf("POWER" to 12,"MUTE" to 13,"VOL+" to 16,"VOL-" to 17,"CH+" to 32,"CH-" to 33,
      "UP" to 88,"DOWN" to 89,"LEFT" to 90,"RIGHT" to 91,"OK" to 92,"MENU" to 82,
      "BACK" to 10,"HOME" to 54,"SOURCE" to 56)[name]?:return
    toggle=1-toggle
    try{ir.transmit(36000,if(!philipsActive && profile=="RC5")rc5(c)else rc6(c))}
    catch(e:Exception){Toast.makeText(this,"IR error",Toast.LENGTH_SHORT).show()}
  }
  private fun duration(halves:List<Int>,u:Int):IntArray{
    val p=mutableListOf<Int>();var state=1;var n=0
    for(h in halves){if(h==state)n+=u else {p.add(n);n=u;state=h}}
    p.add(n);if(p.size%2==1)p.add(u)
    return p.toIntArray()
  }
  private fun rc5(cmd:Int):IntArray{
    val word=(1 shl 13) or ((if(cmd<64)1 else 0) shl 12) or (toggle shl 11) or (cmd and 63)
    val halves=mutableListOf<Int>()
    for(i in 13 downTo 0){val bit=(word shr i) and 1;halves.add(bit);halves.add(1-bit)}
    return duration(halves,889)
  }
  private fun rc6(cmd:Int):IntArray{
    val h=mutableListOf<Int>()
    fun bit(v:Int,width:Int=1){repeat(width){h.add(v)};repeat(width){h.add(1-v)}}
    bit(1);repeat(3){bit(0)};bit(toggle,2)
    repeat(8){bit(0)}
    for(i in 7 downTo 0)bit((cmd shr i) and 1)
    return intArrayOf(2664,888)+duration(h,444)
  }
  private fun nec(a:Int,c:Int){
    val p=mutableListOf(9000,4500)
    listOf(a,a xor 255,c,c xor 255).forEach{v->
      for(i in 0..7){p.add(560);p.add(if(((v shr i) and 1)==1)1690 else 560)}
    }
    p.add(560);p.add(10000)
    try{ir.transmit(38000,p.toIntArray())}catch(e:Exception){}
  }
  private fun start(){if(!ir.hasIrEmitter())return;searching=true;idx=0;next()}
  private fun next(){
    if(!searching)return
    if(idx>=codes.size){stop();status.text="Search complete";return}
    val pair=codes[idx++]
    status.text="Searching code "+idx+" / "+codes.size
    nec(pair.first,pair.second)
    handler.postDelayed({next()},900)
  }
  private fun stop(){searching=false;handler.removeCallbacksAndMessages(null)}
  private fun saveCode(){
    if(idx==0)return
    val code=codes[(idx-1).coerceIn(codes.indices)]
    getSharedPreferences("remote",MODE_PRIVATE).edit().putInt("address",code.first).putInt("power",code.second).apply()
    stop();status.text="Power code saved ✓"
  }
  private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
  private fun col()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  private fun rounded(c:Int,r:Int)=GradientDrawable().apply{
    setColor(c);cornerRadius=dp(r).toFloat();setStroke(dp(1),0xFF484B50.toInt())
  }
  private fun label(s:String,size:Float,color:Int)=TextView(this).apply{
    text=s;textSize=size;setTextColor(color);gravity=Gravity.CENTER;setPadding(dp(3),dp(8),dp(3),dp(8))
  }
  private fun btn(s:String,c:Int,size:Float,fn:()->Unit)=Button(this).apply{
    text=s;textSize=size;setTextColor(Color.WHITE);isAllCaps=false
    background=rounded(c,18);setOnClickListener{fn()}
  }
}