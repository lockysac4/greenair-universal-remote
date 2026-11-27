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
    page.addView(label("v1.3.0  •  Saved remote codes",12f,Color.LTGRAY))
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
      text(canvas,if(selectedRemote().type=="DGTEC")"DGTEC DG-FV42LCD" else if(philips)"Philips Profile Active" else "Universal IR Profile",200f,33f,17f)
      paint.strokeWidth=1.3f;paint.color=0xFF42BE62.toInt()
      canvas.drawLine(42f,34f,100f,34f,paint);canvas.drawLine(300f,34f,358f,34f,paint)
      if(!philips && selectedRemote().type!="DGTEC"){
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
          if(id.isNotBlank() && name.isNotBlank() && type in listOf("PHILIPS","RC6","RC5","DGTEC") && remotes.none{it.id==id}) {
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
    if(r.type=="DGTEC") showDgtec() else remote(r.type=="PHILIPS")
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
      val typeLabel=when(r.type){"PHILIPS"->"Philips • RC6";"DGTEC"->"DGTEC • DG-FV42LCD";"RC5"->"Universal • RC5";else->"Universal • RC6"}
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
    page.addView(label("v1.3.0  •  Remotes saved on this phone",12f,Color.LTGRAY))
  }
  private fun nameInput(default:String=""):EditText =
    EditText(this).apply {
      setSingleLine(true);setText(default);selectAll()
      setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)
      hint="Remote name";setPadding(dp(17),dp(10),dp(17),dp(10))
    }

  private fun addRemoteDialog(){
    val input=nameInput()
    val labels=arrayOf("Philips (RC6)","Universal (RC6)","Universal (RC5)","DGTEC DG-FV42LCD (test)")
    val types=arrayOf("PHILIPS","RC6","RC5","DGTEC")
    val chooser=Spinner(this).apply{
      adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,labels)
      setSelection(0)
    }
    val container=col().apply {
      setPadding(dp(16),dp(8),dp(16),0)
      addView(label("NAME",12f,Color.LTGRAY))
      addView(input)
      addView(label("REMOTE TYPE",12f,Color.LTGRAY))
      addView(chooser)
    }
    AlertDialog.Builder(this).setTitle("Add new remote").setView(container)
      .setNegativeButton("CANCEL",null)
      .setPositiveButton("ADD"){_,_->
        val name=input.text.toString().trim()
        if(name.isEmpty()){
          Toast.makeText(this,"Enter a remote name",Toast.LENGTH_SHORT).show()
        } else {
          val id="remote_"+System.currentTimeMillis().toString()+"_"+remotes.size
          remotes.add(RemoteItem(id,name,types[chooser.selectedItemPosition]))
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
    if(selectedRemote().type=="DGTEC"){dgSend(name);return}
    val c=mapOf("POWER" to 12,"MUTE" to 13,"VOL+" to 16,"VOL-" to 17,"CH+" to 32,"CH-" to 33,
      "UP" to 88,"DOWN" to 89,"LEFT" to 90,"RIGHT" to 91,"OK" to 92,"MENU" to 82,
      "BACK" to 10,"HOME" to 54,"SOURCE" to 56)[name]?:return
    toggle=1-toggle
    try{ir.transmit(36000,if(!philipsActive && profile=="RC5")rc5(c)else rc6(c))}
    catch(e:Exception){Toast.makeText(this,"IR error",Toast.LENGTH_SHORT).show()}
  }
  private val dgKeys=listOf("POWER","MUTE","VOL+","VOL-","CH+","CH-","SOURCE","MENU","BACK","HOME","UP","DOWN","LEFT","RIGHT","OK","0","1","2","3","4","5","6","7","8","9","DTV/ATV","EPG","DISPLAY")
  private fun dgPrefs()=getSharedPreferences("dgtec_"+selectedRemoteId,MODE_PRIVATE)
  private fun dgSend(key:String){
    val p=dgPrefs()
    val a=p.getInt(key+"_a",-1);val c=p.getInt(key+"_c",-1)
    if(a>=0 && c>=0)dgTransmit(p.getString(key+"_protocol","NEC")?:"NEC",a,c)
    else {Toast.makeText(this,"Set up "+key+" first",Toast.LENGTH_SHORT).show();dgSetup(key)}
  }
  private fun showDgtec(){
    page.removeAllViews()
    page.addView(btn("QUICK JVC POWER TEST",0xFF255E38.toInt(),16f){dgJvcTest()},LinearLayout.LayoutParams(-1,dp(56)))
    page.addView(btn("AUTO FIND TV POWER CODE",0xFF255E38.toInt(),16f){dgScan()},LinearLayout.LayoutParams(-1,dp(56)))
    page.addView(RemoteFace(false),LinearLayout.LayoutParams(-1,-2))
    for(group in (listOf("1","2","3","4","5","6","7","8","9","DTV/ATV","0","EPG","DISPLAY")).chunked(3)){
      val row=LinearLayout(this)
      group.forEach{key->row.addView(btn(key,0xFF394146.toInt(),15f){dgSend(key)},LinearLayout.LayoutParams(0,dp(48),1f))}
      page.addView(row)
    }
    page.addView(btn("SET UP / TEST IR BUTTONS",0xFF255E38.toInt(),15f){
      AlertDialog.Builder(this).setTitle("Choose a button to configure").setItems(dgKeys.toTypedArray()){_,i->dgSetup(dgKeys[i])}.show()
    },LinearLayout.LayoutParams(-1,dp(56)))
    page.addView(label("Experimental multi-protocol search • test against your TV",12f,Color.LTGRAY))
  }
  private data class DgCode(val protocol:String,val address:Int,val command:Int)
  private val dgProtocols=arrayOf("NEC","NEC extended","RC5","RC6","JVC")
  private fun dgTransmit(protocol:String,a:Int,c:Int):Boolean {
    if(!ir.hasIrEmitter())return false
    try {
      toggle=1-toggle
      when(protocol){
        "RC5"->ir.transmit(36000,rc5(c,a))
        "RC6"->ir.transmit(36000,rc6(c,a))
        "JVC"->{
          val p=mutableListOf(8400,4200)
          listOf(a,c).forEach{v->for(i in 0..7){p.add(525);p.add(if(((v shr i) and 1)==1)1575 else 525)}}
          p.add(525);p.add(10000);ir.transmit(38000,p.toIntArray())
        }
        else->{
          val p=mutableListOf(9000,4500)
          val bytes=if(protocol=="NEC extended")listOf(a and 255,(a shr 8) and 255,c,c xor 255) else listOf(a,a xor 255,c,c xor 255)
          bytes.forEach{v->for(i in 0..7){p.add(560);p.add(if(((v shr i) and 1)==1)1690 else 560)}}
          p.add(560);p.add(10000);ir.transmit(38000,p.toIntArray())
        }
      }
      return true
    } catch(e:Exception){return false}
  }
  private fun dgSave(p:android.content.SharedPreferences,key:String,v:DgCode){
    p.edit().putInt(key+"_a",v.address).putInt(key+"_c",v.command).putString(key+"_protocol",v.protocol).apply()
  }
  private fun dgJvcTest(){
    val p=dgPrefs()
    val candidates=listOf(DgCode("JVC",3,23),DgCode("JVC",3,15),DgCode("JVC",3,12),DgCode("JVC",3,18),DgCode("JVC",0,23),DgCode("JVC",0,15))
    val area=col().apply{setPadding(dp(16),dp(8),dp(16),dp(8))}
    val info=label("Experimental JVC candidates. Point at your TV and test each.",14f,Color.LTGRAY)
    area.addView(info)
    var index=0
    var tested:DgCode?=null
    fun test(){
      val v=candidates[index]
      if(dgTransmit(v.protocol,v.address,v.command)){tested=v;info.text="Test "+(index+1)+" / "+candidates.size+" • JVC address "+v.address+" • command "+v.command}
      else {tested=null;info.text="IR transmission failed"}
    }
    area.addView(btn("TEST / RETEST",0xFF255E38.toInt(),15f){test()})
    area.addView(btn("NEXT CANDIDATE",0xFF41484D.toInt(),15f){index=(index+1)%candidates.size;test()})
    val dialog=AlertDialog.Builder(this).setTitle("JVC compatibility test").setView(area).setNegativeButton("CLOSE",null).setPositiveButton("SAVE WORKING POWER",null).create()
    dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
      val v=tested
      if(v!=null){dgSave(p,"POWER",v);dialog.dismiss()}else Toast.makeText(this,"Test a code first",Toast.LENGTH_SHORT).show()
    }}
    dialog.show()
  }
  private fun dgScan(){
    if(!ir.hasIrEmitter()){Toast.makeText(this,"No IR transmitter",Toast.LENGTH_SHORT).show();return}
    val prefs=dgPrefs()
    val powerCommands=listOf(0x12,0x08,0x0C,0x45,0x40,0x46,0x47,0x44,0x43,0x07,0x15,0x09,0x16,0x19,0x0D,0x18,0x5E,0x1C,0x5A,0x42,0x52,0x14,0x0F,0x57,0x17)
    val preferred=listOf(0,4,1,2,8,16,32,64,128,255,7,10,20)
    val addresses=(preferred+(0..255).toList()).distinct()
    val extended=listOf(0x00FF,0xFF00,0xBF00,0xDF00,0xEF00,0xF700,0xFB00,0xFD00,0xFE00,0x807F,0x7F80,0x04FB,0x20DF,0x40BF,0x10EF,0x00BF,0x00DF,0x00EF,0x0000,0xFFFF,0x0100,0x0200,0x0400,0x0800,0x1000,0x2000,0x4000,0x8000)
    val candidates=buildList {
      for(a in listOf(3,0,1,2,4,5,7,8))for(c in listOf(23,15,12,18))add(DgCode("JVC",a,c))
      for(a in 0..31)add(DgCode("RC5",a,12))
      for(a in addresses)add(DgCode("RC6",a,12))
      for(a in preferred)for(c in powerCommands)add(DgCode("NEC",a,c))
      for(a in extended)for(c in powerCommands)add(DgCode("NEC extended",a,c))
      for(a in addresses.filter{it !in preferred})for(c in powerCommands)add(DgCode("NEC",a,c))
    }
    val area=col().apply{setPadding(dp(12),dp(6),dp(12),0)}
    val info=label("Point your phone at the TV. Start scanning and pause when it responds.",14f,Color.LTGRAY)
    area.addView(info)
    var index=prefs.getInt("scan_position_v13",0).coerceIn(0,candidates.size)
    var last:DgCode?=null
    var running=false
    val scanHandler=Handler(Looper.getMainLooper())
    fun display(){
      val v=last
      info.text=if(v==null)"Ready: "+index+" / "+candidates.size else index.toString()+" / "+candidates.size+" • "+v.protocol+" • Address "+v.address+" • Command "+v.command
    }
    val tick=object:Runnable{
      override fun run(){
        if(!running)return
        if(index>=candidates.size){running=false;info.text="Candidate search finished. No more preset codes.";return}
        val v=candidates[index]
        if(!dgTransmit(v.protocol,v.address,v.command)){running=false;info.text="IR transmission failed";return}
        last=v;index++;prefs.edit().putInt("scan_position_v13",index).apply();display()
        scanHandler.postDelayed(this,1800)
      }
    }
    fun pause(){running=false;scanHandler.removeCallbacks(tick)}
    area.addView(btn("START / RESUME",0xFF255E38.toInt(),15f){if(!running){running=true;scanHandler.post(tick)}})
    area.addView(btn("PAUSE / TV RESPONDED",0xFF41484D.toInt(),15f){pause()})
    area.addView(btn("RETEST LAST CODE",0xFF41484D.toInt(),15f){pause();last?.let{dgTransmit(it.protocol,it.address,it.command)}})
    area.addView(btn("PREVIOUS CODE + TEST",0xFF41484D.toInt(),14f){
      pause();if(index>1){index--;last=candidates[index-1];last?.let{dgTransmit(it.protocol,it.address,it.command)};prefs.edit().putInt("scan_position_v13",index).apply();display()}
    })
    area.addView(btn("RESTART SEARCH",0xFF41484D.toInt(),14f){pause();index=0;last=null;prefs.edit().putInt("scan_position_v13",0).apply();display()})
    display()
    val dialog=AlertDialog.Builder(this).setTitle("DGTEC automatic power search").setView(area).setNegativeButton("STOP / CLOSE",null).setPositiveButton("SAVE WORKING CODE",null).create()
    dialog.setOnDismissListener{pause()}
    dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
      pause();val v=last
      if(v!=null){dgSave(prefs,"POWER",v);dialog.dismiss();Toast.makeText(this,"Power protocol saved",Toast.LENGTH_SHORT).show()}
      else Toast.makeText(this,"Test a code first",Toast.LENGTH_SHORT).show()
    }}
    dialog.show()
  }
  private fun dgSetup(key:String){
    val p=dgPrefs()
    val area=col().apply{setPadding(dp(16),dp(8),dp(16),0)}
    val protocol=Spinner(this).apply{
      adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,dgProtocols)
      setSelection(dgProtocols.indexOf(p.getString(key+"_protocol",p.getString("POWER_protocol","NEC"))).coerceAtLeast(0))
    }
    val address=EditText(this).apply{inputType=2;hint="Address (decimal)";setText(p.getInt(key+"_a",p.getInt("POWER_a",0)).toString())}
    val command=EditText(this).apply{inputType=2;hint="Command (decimal)";setText(p.getInt(key+"_c",0).toString())}
    area.addView(label("Protocol",14f,Color.LTGRAY));area.addView(protocol)
    area.addView(label("Address (decimal)",14f,Color.LTGRAY));area.addView(address)
    area.addView(label("Command (decimal)",14f,Color.LTGRAY));area.addView(command)
    fun value():DgCode?{
      val pr=dgProtocols[protocol.selectedItemPosition];val a=address.text.toString().toIntOrNull();val c=command.text.toString().toIntOrNull()
      val maxA=when(pr){"RC5"->31;"NEC extended"->65535;else->255}
      val maxC=if(pr=="RC5")127 else 255
      if(a==null||c==null||a !in 0..maxA||c !in 0..maxC){Toast.makeText(this,"Invalid address or command for "+pr,Toast.LENGTH_SHORT).show();return null}
      return DgCode(pr,a,c)
    }
    fun test(){val v=value()?:return;if(!dgTransmit(v.protocol,v.address,v.command))Toast.makeText(this,"IR transmission failed",Toast.LENGTH_SHORT).show()}
    area.addView(btn("TEST",0xFF255E38.toInt(),15f){test()})
    area.addView(btn("NEXT COMMAND + TEST",0xFF41484D.toInt(),15f){
      val v=value();if(v!=null){command.setText(((v.command+1)%(if(v.protocol=="RC5")128 else 256)).toString());test()}
    })
    val dialog=AlertDialog.Builder(this).setTitle("Configure "+key).setView(area).setNegativeButton("CANCEL",null).setPositiveButton("SAVE WORKING CODE",null).create()
    dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
      val v=value();if(v!=null){dgSave(p,key,v);dialog.dismiss()}
    }}
    dialog.show()
  }
  private fun duration(halves:List<Int>,u:Int):IntArray{
    val p=mutableListOf<Int>();var state=1;var n=0
    for(h in halves){if(h==state)n+=u else {p.add(n);n=u;state=h}}
    p.add(n);if(p.size%2==1)p.add(u)
    return p.toIntArray()
  }
  private fun rc5(cmd:Int,address:Int=0):IntArray{
    val word=(1 shl 13) or ((if(cmd<64)1 else 0) shl 12) or (toggle shl 11) or ((address and 31) shl 6) or (cmd and 63)
    val halves=mutableListOf<Int>()
    for(i in 13 downTo 0){val bit=(word shr i) and 1;halves.add(bit);halves.add(1-bit)}
    return duration(halves,889)
  }
  private fun rc6(cmd:Int,address:Int=0):IntArray{
    val h=mutableListOf<Int>()
    fun bit(v:Int,width:Int=1){repeat(width){h.add(v)};repeat(width){h.add(1-v)}}
    bit(1);repeat(3){bit(0)};bit(toggle,2)
    for(i in 7 downTo 0)bit((address shr i) and 1)
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