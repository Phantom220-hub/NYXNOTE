package com.nyxnote.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private val BG=Color(0xFF070B16); private val PANEL=Color(0xFF111A2B); private val PANEL2=Color(0xFF1B2941)
private val BLUE=Color(0xFF42D9FF); private val PURPLE=Color(0xFF9B7BFF); private val GREEN=Color(0xFF42D69A)
private val TXT=Color(0xFFF2F6FF); private val MUTED=Color(0xFF91A2BC)

data class Entry(val id:Long,val title:String,val body:String="",val done:Boolean=false,val progress:Int=0)

class NyxDb(c:Context):SQLiteOpenHelper(c,"nyxnote.db",null,2){
 override fun onCreate(d:SQLiteDatabase){
  d.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,done INTEGER DEFAULT 0,priority TEXT DEFAULT 'Обычный',created INTEGER)")
  d.execSQL("CREATE TABLE goals(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,progress INTEGER DEFAULT 0)")
  d.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,body TEXT DEFAULT '',pinned INTEGER DEFAULT 0,created INTEGER)")
  d.execSQL("CREATE TABLE habits(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,streak INTEGER DEFAULT 0)")
  d.execSQL("CREATE TABLE journal(id INTEGER PRIMARY KEY AUTOINCREMENT,body TEXT NOT NULL,created INTEGER)")
  d.execSQL("CREATE TABLE ideas(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,body TEXT DEFAULT '',created INTEGER)")
  d.execSQL("CREATE TABLE learning(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,progress INTEGER DEFAULT 0)")
  d.execSQL("CREATE TABLE focus(id INTEGER PRIMARY KEY AUTOINCREMENT,seconds INTEGER,created INTEGER)")
  d.execSQL("CREATE TABLE xp(id INTEGER PRIMARY KEY CHECK(id=1), total INTEGER NOT NULL DEFAULT 0)")
  d.execSQL("INSERT INTO xp(id,total) VALUES(1,0)")
 }
 override fun onUpgrade(d:SQLiteDatabase,o:Int,n:Int){
  if(o<2){
   d.execSQL("CREATE TABLE IF NOT EXISTS xp(id INTEGER PRIMARY KEY CHECK(id=1), total INTEGER NOT NULL DEFAULT 0)")
   d.execSQL("INSERT OR IGNORE INTO xp(id,total) VALUES(1,0)")
  }
 }
 fun add(t:String,title:String,body:String=""){
  val v=ContentValues()
  when(t){
   "tasks"->{v.put("title",title);v.put("created",System.currentTimeMillis())}
   "goals","habits","learning"->v.put("title",title)
   "notes","ideas"->{v.put("title",title);v.put("body",body);v.put("created",System.currentTimeMillis())}
   "journal"->{v.put("body",title);v.put("created",System.currentTimeMillis())}
  }
  writableDatabase.insert(t,null,v)
 }
 fun list(t:String):List<Entry>{
  val a=mutableListOf<Entry>();val c=readableDatabase.rawQuery("SELECT * FROM $t ORDER BY id DESC",null)
  c.use{while(it.moveToNext()){
   val id=it.getLong(0)
   val title=if(t=="journal")"Запись" else it.getString(1)
   val body=when(t){"notes","ideas","journal"->it.getString(it.getColumnIndexOrThrow("body"));"habits"->"Серия: ${it.getInt(it.getColumnIndexOrThrow("streak"))} дней";else->""}
   val done=if(t=="tasks")it.getInt(it.getColumnIndexOrThrow("done"))==1 else false
   val progress=if(t=="goals"||t=="learning")it.getInt(it.getColumnIndexOrThrow("progress")) else 0
   a.add(Entry(id,title,body,done,progress))
  }};return a
 }
 fun totalXp():Int{val c=readableDatabase.rawQuery("SELECT total FROM xp WHERE id=1",null);c.use{return if(it.moveToFirst())it.getInt(0) else 0}}
 fun addXp(points:Int){if(points>0)writableDatabase.execSQL("UPDATE xp SET total=total+? WHERE id=1",arrayOf(points))}
 fun level():Int=totalXp()/100+1
 fun levelProgress():Int=totalXp()%100
 fun taskDone(id:Long,v:Boolean){
  val c=readableDatabase.rawQuery("SELECT done FROM tasks WHERE id=?",arrayOf(id.toString()))
  val wasDone=c.use{it.moveToFirst() && it.getInt(0)==1}
  writableDatabase.execSQL("UPDATE tasks SET done=? WHERE id=?",arrayOf(if(v)1 else 0,id))
  if(v && !wasDone)addXp(10)
 }
 fun progress(t:String,id:Long,v:Int){
  val c=readableDatabase.rawQuery("SELECT progress FROM $t WHERE id=?",arrayOf(id.toString()))
  val old=c.use{if(it.moveToFirst())it.getInt(0) else 0}
  val next=v.coerceIn(0,100)
  writableDatabase.execSQL("UPDATE $t SET progress=? WHERE id=?",arrayOf(next,id))
  if(t=="goals" && old<100 && next==100)addXp(50)
 }
 fun delete(t:String,id:Long){writableDatabase.delete(t,"id=?",arrayOf(id.toString()))}
 fun focus(seconds:Int){val v=ContentValues().apply{put("seconds",seconds);put("created",System.currentTimeMillis())};writableDatabase.insert("focus",null,v)}
 fun count(sql:String):Int{val c=readableDatabase.rawQuery(sql,null);c.use{return if(it.moveToFirst())it.getInt(0) else 0}}
}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val db=NyxDb(this);setContent{NyxApp(db)}}
}

@Composable fun NyxApp(db:NyxDb){
 var page by remember{mutableStateOf("Сегодня")};var menu by remember{mutableStateOf(false)}
 var accent by remember{mutableIntStateOf(0)}
 val color=when(accent){1->PURPLE;2->GREEN;else->BLUE}
 MaterialTheme(colorScheme=darkColorScheme(primary=color,background=BG,surface=PANEL)){
  Column(Modifier.fillMaxSize().background(BG)){
   Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text("NYXNOTE",color=TXT,fontSize=22.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("PERSONAL SPACE  •  OFFLINE",color=MUTED,fontSize=9.sp,letterSpacing=1.sp)}
    Icon(Icons.Default.Bolt,null,tint=color,modifier=Modifier.size(30.dp))
   }
   Box(Modifier.weight(1f)){AnimatedContent(page,label="page"){Screen(it,db,color){accent=it}}}
   NavigationBar(containerColor=PANEL){listOf("Сегодня" to Icons.Default.Today,"Цели" to Icons.Default.Flag,"Заметки" to Icons.Default.StickyNote2,"Фокус" to Icons.Default.Timer,"Статистика" to Icons.Default.Insights).forEach{(n,ic)->NavigationBarItem(selected=page==n,onClick={page=n},icon={Icon(ic,null)},label={Text(n,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=color,selectedTextColor=color,indicatorColor=PANEL2,unselectedIconColor=MUTED,unselectedTextColor=MUTED))}}
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
    TextButton(onClick={menu=!menu}){Text(if(menu)"Скрыть меню" else "Ещё  ▾",color=color)}
    if(menu)listOf("Обучение","Привычки","Дневник","Идеи","Часы","ИИ-помощник","Настройки").forEach{TextButton(onClick={page=it}){Text(it,color=TXT,fontSize=11.sp)}}
   }
  }
 }
}

@Composable fun Screen(page:String,db:NyxDb,c:Color,onAccent:(Int)->Unit){
 var refresh by remember{mutableIntStateOf(0)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Spacer(Modifier.height(4.dp));Text(page,color=TXT,fontSize=26.sp,fontWeight=FontWeight.Bold)
  when(page){
   "Сегодня"->{
    val tasks=db.list("tasks");val done=tasks.count{it.done}
    Text(SimpleDateFormat("EEEE, d MMMM",Locale("ru")).format(Date()).replaceFirstChar{it.uppercase()},color=MUTED)
    CardBox{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("УРОВЕНЬ ${db.level()}",color=c,fontSize=11.sp,fontWeight=FontWeight.Bold);Text("${db.totalXp()} XP",color=TXT,fontSize=27.sp,fontWeight=FontWeight.Bold)};Text("+10 XP за задачу",color=MUTED,fontSize=10.sp)};LinearProgressIndicator(progress={db.levelProgress()/100f},modifier=Modifier.fillMaxWidth(),color=c,trackColor=PANEL2);Text("${db.levelProgress()} / 100 XP до следующего уровня",color=MUTED,fontSize=11.sp)}
    CardBox{Text("ПРОГРЕСС ДНЯ",color=c,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("$done / ${tasks.size}",color=TXT,fontSize=30.sp,fontWeight=FontWeight.Bold);LinearProgressIndicator(progress={if(tasks.isEmpty())0f else done.toFloat()/tasks.size},modifier=Modifier.fillMaxWidth(),color=c,trackColor=PANEL2)}
    AddLine("Новая задача…",c){db.add("tasks",it);refresh++}
    tasks.forEach{e->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(PANEL).padding(8.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(e.done,{db.taskDone(e.id,it);refresh++},colors=CheckboxDefaults.colors(checkedColor=c));Text(e.title,color=if(e.done)MUTED else TXT,modifier=Modifier.weight(1f));IconButton(onClick={db.delete("tasks",e.id);refresh++}){Icon(Icons.Default.Delete,null,tint=MUTED)}}}
   }
   "Цели"->{AddLine("Например: выучить C++",c){db.add("goals",it);refresh++};db.list("goals").forEach{e->CardBox{Row{Text(e.title,color=TXT,modifier=Modifier.weight(1f));Text("${e.progress}%",color=c)};LinearProgressIndicator(progress={e.progress/100f},modifier=Modifier.fillMaxWidth(),color=c);Row{TextButton(onClick={db.progress("goals",e.id,e.progress+10);refresh++}){Text("+10%",color=c)};TextButton(onClick={db.delete("goals",e.id);refresh++}){Text("Удалить",color=MUTED)}}}}}
   "Заметки"->{var q by remember{mutableStateOf("")};AddLine("Заголовок заметки…",c){db.add("notes",it);refresh++};Field(q,{q=it},"Поиск по заметкам…");db.list("notes").filter{it.title.contains(q,true)||it.body.contains(q,true)}.forEach{e->CardBox{Text(e.title,color=TXT,fontWeight=FontWeight.Bold);Text(e.body.ifBlank{"Открой заметку, чтобы дополнить её (редактор будет расширен)."},color=MUTED);TextButton(onClick={db.delete("notes",e.id);refresh++}){Text("Удалить",color=MUTED)}}}}
   "Фокус"->FocusPanel(c,db)
   "Статистика"->{val tasks=db.count("SELECT COUNT(*) FROM tasks WHERE done=1");val goals=db.count("SELECT COUNT(*) FROM goals WHERE progress=100");val sec=db.count("SELECT COALESCE(SUM(seconds),0) FROM focus");Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("Задачи",tasks.toString(),c,Modifier.weight(1f));Stat("Цели",goals.toString(),PURPLE,Modifier.weight(1f))};Stat("Фокус","${sec/3600} ч ${(sec%3600)/60} мин",GREEN,Modifier.fillMaxWidth());CardBox{Text("УРОВЕНЬ ${db.level()}",color=c,fontWeight=FontWeight.Bold);Text("${db.totalXp()} XP всего",color=TXT,fontSize=25.sp,fontWeight=FontWeight.Bold);LinearProgressIndicator(progress={db.levelProgress()/100f},modifier=Modifier.fillMaxWidth(),color=c);Text("${db.levelProgress()} / 100 XP до следующего уровня",color=MUTED,fontSize=11.sp)};CardBox{Text("Твой принцип",color=c);Text("Регулярность важнее идеального дня. Каждый маленький шаг считается.",color=TXT)}}
   "Обучение"->{AddLine("Навык для изучения…",c){db.add("learning",it);refresh++};db.list("learning").forEach{ProgressItem(it,"learning",db,c){refresh++}}}
   "Привычки"->{AddLine("Новая привычка…",c){db.add("habits",it);refresh++};db.list("habits").forEach{CardBox{Text(it.title,color=TXT,fontWeight=FontWeight.Bold);Text(it.body,color=MUTED);Text("Отмечай выполнение каждый день. Серии будут расширены в следующем обновлении.",color=MUTED,fontSize=11.sp);TextButton(onClick={db.delete("habits",it.id);refresh++}){Text("Удалить",color=MUTED)}}}}
   "Дневник"->{var v by remember{mutableStateOf("")};CardBox{Field(v,{v=it},"Что произошло сегодня?");Button(onClick={if(v.isNotBlank()){db.add("journal",v);v="";refresh++}},colors=ButtonDefaults.buttonColors(containerColor=c)){Text("Сохранить запись",color=BG)}};db.list("journal").forEach{CardBox{Text(it.body,color=TXT);TextButton(onClick={db.delete("journal",it.id);refresh++}){Text("Удалить",color=MUTED)}}}}
   "Идеи"->{AddLine("Название идеи…",c){db.add("ideas",it);refresh++};db.list("ideas").forEach{CardBox{Text(it.title,color=TXT,fontWeight=FontWeight.Bold);Text(it.body,color=MUTED);TextButton(onClick={db.delete("ideas",it.id);refresh++}){Text("Удалить",color=MUTED)}}}}
   "Часы"->{var now by remember{mutableStateOf(Date())};LaunchedEffect(Unit){while(true){now=Date();delay(1000)}};CardBox{Text(SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(now),color=TXT,fontSize=48.sp);Text(SimpleDateFormat("EEEE, d MMMM yyyy",Locale("ru")).format(now),color=MUTED)};Text("Будильники пока открываются через системное приложение «Часы».",color=MUTED,fontSize=12.sp)}
   "ИИ-помощник"->{var answer by remember{mutableStateOf("Я офлайн-помощник. Спроси о задачах, целях, заметках или плане дня.")};CardBox{Text(answer,color=TXT);AddLine("Спроси NYX…",c){q->answer=when{q.contains("задач",true)->"Выполнено задач: ${db.count("SELECT COUNT(*) FROM tasks WHERE done=1")} из ${db.count("SELECT COUNT(*) FROM tasks")}.";q.contains("целей",true)->"Целей: ${db.count("SELECT COUNT(*) FROM goals")}.";q.contains("замет",true)->"Заметок: ${db.count("SELECT COUNT(*) FROM notes")}.";else->"План: выбери одну важную задачу, разбей её на три шага и начни с первого."}}};Text("Это простой локальный помощник, а не полноценная нейросеть.",color=MUTED,fontSize=11.sp)}
   "Настройки"->{CardBox{Text("Цвет акцента",color=TXT,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf(BLUE,PURPLE,GREEN).forEachIndexed{i,col->Box(Modifier.size(42.dp).clip(CircleShape).background(col).clickable{onAccent(i)}.border(2.dp,TXT.copy(alpha=.4f),CircleShape))}};Text("Изменение применяется сразу.",color=MUTED,fontSize=11.sp)};CardBox{Text("Данные хранятся локально в SQLite. Интернет не требуется.",color=TXT)}}
  }
  Spacer(Modifier.height(16.dp))
 }
}

@Composable fun CardBox(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PANEL).border(1.dp,Color(0xFF263752),RoundedCornerShape(18.dp)).padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)}
@Composable fun Field(v:String,on:(String)->Unit,hint:String){BasicTextField(v,on,Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(PANEL2).padding(13.dp),textStyle=androidx.compose.ui.text.TextStyle(color=TXT,fontSize=14.sp),decorationBox={inner->Box{if(v.isEmpty())Text(hint,color=MUTED);inner()}})}
@Composable fun AddLine(hint:String,c:Color,add:(String)->Unit){var value by remember{mutableStateOf("")};Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Field(value,{value=it},hint)};Button(onClick={if(value.isNotBlank()){add(value.trim());value=""}},colors=ButtonDefaults.buttonColors(containerColor=c),shape=RoundedCornerShape(12.dp)){Text("＋",color=BG,fontSize=18.sp)}}}
@Composable fun Stat(title:String,value:String,c:Color,m:Modifier){Column(m.clip(RoundedCornerShape(16.dp)).background(PANEL).padding(15.dp)){Text(title,color=MUTED,fontSize=12.sp);Text(value,color=c,fontSize=25.sp,fontWeight=FontWeight.Bold)}}
@Composable fun ProgressItem(e:Entry,t:String,db:NyxDb,c:Color,refresh:()->Unit){CardBox{Row{Text(e.title,color=TXT,modifier=Modifier.weight(1f));Text("${e.progress}%",color=c)};LinearProgressIndicator(progress={e.progress/100f},modifier=Modifier.fillMaxWidth(),color=c);TextButton(onClick={db.progress(t,e.id,e.progress+10);refresh()}){Text("+10%",color=c)}}}
@Composable fun FocusPanel(c:Color,db:NyxDb){
 var left by remember{mutableIntStateOf(1500)};var elapsed by remember{mutableIntStateOf(0)};var running by remember{mutableStateOf(false)}
 LaunchedEffect(running){while(running){delay(1000);if(left>0){left--;elapsed++}else{running=false;db.focus(elapsed);db.addXp(25);elapsed=0}}}
 CardBox{Text("POMODORO  •  25 / 5",color=c,fontSize=11.sp,fontWeight=FontWeight.Bold);Text(String.format(Locale.US,"%02d:%02d",left/60,left%60),color=TXT,fontSize=52.sp,fontWeight=FontWeight.Light);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={running=!running},colors=ButtonDefaults.buttonColors(containerColor=c)){Text(if(running)"Пауза" else "Старт",color=BG)};OutlinedButton(onClick={running=false;left=1500;db.focus(elapsed);elapsed=0}){Text("Сброс",color=TXT)};OutlinedButton(onClick={running=false;left=300;elapsed=0}){Text("Перерыв",color=TXT)}};Text("Завершённые сессии учитываются в статистике.",color=MUTED,fontSize=11.sp)}
}
