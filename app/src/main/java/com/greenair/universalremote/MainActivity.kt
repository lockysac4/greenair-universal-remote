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
    val outer=col().apply {setPadding(dp(12),dp(6),dp(12),0);setBackgroundColor(0xFF0C0D10.toInt())}
    outer.addView(label("GREENAIR UNIVERSAL REMOTE",16f,Color.WHITE).apply { setPadding(0,dp(7),0,dp(2)) })
    outer.addView(label("Philips 22PFT5403/98",14f,Color.LTGRAY).apply { setPadding(0,0,0,dp(8)) })
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
    philipsActive=philips
    page.removeAllViews()
    val body=col().apply {
      setPadding(dp(14),dp(8),dp(14),dp(16))
      background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xFF24282A.toInt(),0xFF111619.toInt())).apply {
        cornerRadius=dp(35).toFloat();setStroke(dp(1),0xFF407650.toInt())
      }
      elevation=dp(5).toFloat()
    }
    page.addView(body,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(5),dp(10),dp(5),dp(8))})
    if (philips) {
      profile="RC6"
      body.addView(label("━━  Philips Profile Active  ━━",14f,Color.WHITE).apply {
        setPadding(0,dp(9),0,dp(13))
      })
    } else {
      body.addView(label("UNIVERSAL PROFILE",12f,Color.LTGRAY))
      val profiles=RadioGroup(this).apply{orientation=RadioGroup.HORIZONTAL;gravity=Gravity.CENTER}
      listOf("RC6","RC5").forEach{p->
        profiles.addView(RadioButton(this).apply{
          text=p;textSize=14f;setTextColor(Color.WHITE)
          buttonTintList=ColorStateList.valueOf(0xFF61E46F.toInt())
          isChecked=profile==p
          setOnClickListener {
            profile=p
            getSharedPreferences("remote",MODE_PRIVATE).edit().putString("profile",p).apply()
          }
        },LinearLayout.LayoutParams(0,dp(43),1f))
      }
      body.addView(profiles)
    }
    val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    top.addView(btn("⏻",0xFFBE1F26.toInt(),31f){send("POWER")},LinearLayout.LayoutParams(dp(92),dp(63)))
    top.addView(View(this),LinearLayout.LayoutParams(0,dp(1),1f))
    top.addView(btn("↪  SOURCE",0xFF353A3E.toInt(),14f){send("SOURCE")},LinearLayout.LayoutParams(dp(132),dp(58)))
    body.addView(top)

    val ctr=LinearLayout(this).apply{gravity=Gravity.CENTER}
    fun rocker(caption:String,plus:String,minus:String):LinearLayout {
      return col().apply {
        background=rounded(0xFF31363A.toInt(),27)
        setPadding(dp(2),dp(2),dp(2),dp(2))
        addView(rockerPart("+"){send(plus)},LinearLayout.LayoutParams(-1,dp(53)))
        addView(label(caption,15f,Color.WHITE).apply{setPadding(0,dp(6),0,dp(6))})
        addView(rockerPart("−"){send(minus)},LinearLayout.LayoutParams(-1,dp(53)))
      }
    }
    val vol=rocker("VOL","VOL+","VOL-")
    val mid=col().apply{gravity=Gravity.CENTER}
    mid.addView(btn("◀̸  MUTE",0xFF32373B.toInt(),13f){send("MUTE")},LinearLayout.LayoutParams(-1,dp(69)))
    mid.addView(View(this),LinearLayout.LayoutParams(-1,dp(10)))
    mid.addView(btn("⌂  HOME",0xFF32373B.toInt(),13f){send("HOME")},LinearLayout.LayoutParams(-1,dp(69)))
    val ch=rocker("CH","CH+","CH-")
    ctr.addView(vol,LinearLayout.LayoutParams(0,dp(165),1f).apply{setMargins(dp(2),dp(17),dp(5),0)})
    ctr.addView(mid,LinearLayout.LayoutParams(0,dp(165),1.23f).apply{setMargins(dp(3),dp(17),dp(3),0)})
    ctr.addView(ch,LinearLayout.LayoutParams(0,dp(165),1f).apply{setMargins(dp(5),dp(17),dp(2),0)})
    body.addView(ctr)

    val menu=LinearLayout(this)
    menu.addView(btn("☰  MENU",0xFF33383C.toInt(),14f){send("MENU")},LinearLayout.LayoutParams(0,dp(61),1f).apply{setMargins(0,dp(15),dp(6),0)})
    menu.addView(btn("↶  BACK",0xFF33383C.toInt(),14f){send("BACK")},LinearLayout.LayoutParams(0,dp(61),1f).apply{setMargins(dp(6),dp(15),0,0)})
    body.addView(menu)

    val dpad=DirectionPad()
    body.addView(dpad,LinearLayout.LayoutParams(-1,dp(274)).apply{setMargins(0,dp(12),0,dp(3))})
    if(!philips) {
      page.addView(label("TCL / Universal TV code search",16f,Color.WHITE))
      page.addView(btn("START / STOP AUTO SEARCH",0xFF45474D.toInt(),14f){if(searching)stop()else start()},LinearLayout.LayoutParams(-1,dp(55)))
      page.addView(btn("TV RESPONDED - STOP & SAVE",0xFF45474D.toInt(),14f){saveCode()},LinearLayout.LayoutParams(-1,dp(55)))
    }
    page.addView(label("v0.8.0  •  Saved remote codes",12f,Color.LTGRAY))
  }

  private fun rockerPart(s:String,action:()->Unit)=TextView(this).apply{
    text=s;textSize=29f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
    setOnClickListener{action()}
  }

  private inner class DirectionPad:View(this@MainActivity) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c:Canvas) {
      super.onDraw(c)
      val cx=width/2f
      val cy=height/2f
      val radius=minOf(width.toFloat(),height.toFloat())*0.46f
      val outer=RectF(cx-radius,cy-radius,cx+radius,cy+radius)
      p.style=Paint.Style.STROKE;p.strokeWidth=radius*0.46f;p.strokeCap=Paint.Cap.BUTT
      p.color=0xFF0C1113.toInt()
      c.drawCircle(cx,cy,radius*0.78f,p)
      p.color=0xFF393E43.toInt()
      for(i in 0..3) c.drawArc(outer, i*90f+229f,82f,false,p)
      p.style=Paint.Style.STROKE;p.strokeWidth=dp(1).toFloat();p.color=0xFF72797D.toInt()
      c.drawCircle(cx,cy,radius*1.015f,p)
      p.style=Paint.Style.FILL
      p.color=0xFF13181B.toInt();c.drawCircle(cx,cy,radius*0.47f,p)
      p.color=0xFF47CD68.toInt();c.drawCircle(cx,cy,radius*0.425f,p)
      p.color=0xFF272D30.toInt();c.drawCircle(cx,cy,radius*0.398f,p)
      p.color=Color.WHITE;p.textAlign=Paint.Align.CENTER
      p.textSize=radius*0.34f;p.typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD)
      c.drawText("OK",cx,cy-(p.ascent()+p.descent())/2f,p)
      p.textSize=radius*0.30f
      c.drawText("▲",cx,cy-radius*0.66f-(p.ascent()+p.descent())/2f,p)
      c.drawText("▼",cx,cy+radius*0.66f-(p.ascent()+p.descent())/2f,p)
      c.drawText("◀",cx-radius*0.68f,cy-(p.ascent()+p.descent())/2f,p)
      c.drawText("▶",cx+radius*0.68f,cy-(p.ascent()+p.descent())/2f,p)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
      if(event.action==MotionEvent.ACTION_DOWN) return true
      if(event.action==MotionEvent.ACTION_UP) {
        val dx=event.x-width/2f;val dy=event.y-height/2f
        val r=hypot(dx.toDouble(),dy.toDouble()).toFloat()
        val radius=minOf(width.toFloat(),height.toFloat())*0.46f
        if(r<radius*0.45f)send("OK")
        else if(r<=radius*1.04f) {
          if(kotlin.math.abs(dx)>kotlin.math.abs(dy))send(if(dx>0)"RIGHT" else "LEFT")
          else send(if(dy>0)"DOWN" else "UP")
        }
        performClick()
        return true
      }
      return true
    }
    override fun performClick():Boolean {super.performClick();return true}
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