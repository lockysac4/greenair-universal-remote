package com.greenair.universalremote

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.hardware.ConsumerIrManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.*
import android.view.View

class MainActivity:Activity() {
  private lateinit var ir:ConsumerIrManager
  private lateinit var page:LinearLayout
  private lateinit var status:TextView
  private val handler=Handler(Looper.getMainLooper())
  private var profile="RC6"
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
    val outer=col().apply {setPadding(dp(12),dp(6),dp(12),0);setBackgroundColor(0xFF0C0D10.toInt())}
    outer.addView(label("GREENAIR UNIVERSAL REMOTE",19f,Color.WHITE))
    outer.addView(label("Philips 22PFT5403/98",14f,Color.LTGRAY))
    val tabs=LinearLayout(this)
    listOf("REMOTE","PHILIPS","CODES").forEach{ title->
      tabs.addView(btn(title,0xFF303238.toInt(),12f) {
        stop()
        if(title=="CODES") savedCodes() else remote(title=="PHILIPS")
      },LinearLayout.LayoutParams(0,dp(49),1f))
    }
    outer.addView(tabs)
    status=label(if(ir.hasIrEmitter()) "●  IR transmitter detected ✓" else "IR transmitter not detected",14f,0xFF65DD75.toInt())
    outer.addView(status)
    page=col()
    val sc=ScrollView(this).apply {isVerticalScrollBarEnabled=false;addView(page)}
    outer.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
    setContentView(outer)
    remote(true)
  }
  private fun remote(philips:Boolean) {
    page.removeAllViews()
    val body=col().apply{setPadding(dp(13),dp(12),dp(13),dp(16));background=rounded(0xFF1C1D21.toInt(),35)}
    page.addView(body,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(5),dp(10),dp(5),dp(8))})
    body.addView(label("IR PROFILE",12f,Color.LTGRAY))
    val profiles=RadioGroup(this).apply{orientation=RadioGroup.HORIZONTAL;gravity=Gravity.CENTER}
    listOf("RC6","RC5").forEach{p->
      profiles.addView(RadioButton(this).apply{
        text=p;textSize=15f;setTextColor(Color.WHITE);buttonTintList=ColorStateList.valueOf(0xFF61E46F.toInt())
        isChecked=profile==p
        setOnClickListener{profile=p;getSharedPreferences("remote",MODE_PRIVATE).edit().putString("profile",p).apply()}
      },LinearLayout.LayoutParams(0,dp(44),1f))
    }
    body.addView(profiles)
    val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    top.addView(btn("⏻",0xFFBB2029.toInt(),27f){send("POWER")},LinearLayout.LayoutParams(dp(94),dp(70)))
    top.addView(View(this),LinearLayout.LayoutParams(0,dp(1),1f))
    top.addView(btn("⇥ SOURCE",0xFF35373B.toInt(),14f){send("SOURCE")},LinearLayout.LayoutParams(dp(140),dp(64)))
    body.addView(top)
    val ctr=LinearLayout(this)
    val vol=col()
    vol.addView(btn("+",0xFF35373B.toInt(),25f){send("VOL+")},LinearLayout.LayoutParams(-1,dp(65)))
    vol.addView(label("VOL",13f,Color.WHITE))
    vol.addView(btn("−",0xFF35373B.toInt(),25f){send("VOL-")},LinearLayout.LayoutParams(-1,dp(65)))
    val mid=col()
    mid.addView(btn("MUTE",0xFF35373B.toInt(),13f){send("MUTE")},LinearLayout.LayoutParams(-1,dp(76)))
    mid.addView(btn("⌂ HOME",0xFF35373B.toInt(),13f){send("HOME")},LinearLayout.LayoutParams(-1,dp(76)))
    val ch=col()
    ch.addView(btn("+",0xFF35373B.toInt(),25f){send("CH+")},LinearLayout.LayoutParams(-1,dp(65)))
    ch.addView(label("CH",13f,Color.WHITE))
    ch.addView(btn("−",0xFF35373B.toInt(),25f){send("CH-")},LinearLayout.LayoutParams(-1,dp(65)))
    listOf(vol,mid,ch).forEach {ctr.addView(it,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(3),dp(9),dp(3),0)})}
    body.addView(ctr)
    val menu=LinearLayout(this)
    menu.addView(btn("☰ MENU",0xFF35373B.toInt(),13f){send("MENU")},LinearLayout.LayoutParams(0,dp(57),1f))
    menu.addView(btn("↶ BACK",0xFF35373B.toInt(),13f){send("BACK")},LinearLayout.LayoutParams(0,dp(57),1f))
    body.addView(menu)
    body.addView(label("DIRECTION PAD",11f,Color.LTGRAY))
    val up=LinearLayout(this).apply{gravity=Gravity.CENTER}
    up.addView(btn("▲",0xFF36383D.toInt(),22f){send("UP")},LinearLayout.LayoutParams(dp(140),dp(53)))
    body.addView(up)
    val nav=LinearLayout(this)
    nav.addView(btn("◀",0xFF36383D.toInt(),22f){send("LEFT")},LinearLayout.LayoutParams(0,dp(83),1f))
    nav.addView(btn("OK",0xFF45474D.toInt(),24f){send("OK")},LinearLayout.LayoutParams(0,dp(83),1.2f))
    nav.addView(btn("▶",0xFF36383D.toInt(),22f){send("RIGHT")},LinearLayout.LayoutParams(0,dp(83),1f))
    body.addView(nav)
    val down=LinearLayout(this).apply{gravity=Gravity.CENTER}
    down.addView(btn("▼",0xFF36383D.toInt(),22f){send("DOWN")},LinearLayout.LayoutParams(dp(140),dp(53)))
    body.addView(down)
    if(!philips){
      page.addView(label("TCL / Universal TV code search",16f,Color.WHITE))
      page.addView(btn("START / STOP AUTO SEARCH",0xFF45474D.toInt(),14f){if(searching)stop()else start()},LinearLayout.LayoutParams(-1,dp(55)))
      page.addView(btn("TV RESPONDED - STOP & SAVE",0xFF45474D.toInt(),14f){saveCode()},LinearLayout.LayoutParams(-1,dp(55)))
    }
    page.addView(label("v0.7.0  •  Saved remote codes",12f,Color.LTGRAY))
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
    try{ir.transmit(36000,if(profile=="RC5")rc5(c)else rc6(c))}
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