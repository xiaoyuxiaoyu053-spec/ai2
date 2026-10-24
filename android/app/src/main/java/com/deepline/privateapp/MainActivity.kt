package com.deepline.privateapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Green = Color(0xFF06C755)
private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF7A8580)
private val Page = Color(0xFFF4F7F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var accent by remember {
                mutableStateOf(try {
                    Color(android.graphics.Color.parseColor(LocalStore.load(context).optString("theme", "#06C755")))
                } catch (_: Exception) { Green })
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = accent, background = Page)) {
                DeepLineApp(onThemeChange = { accent = it })
            }
        }
    }
}

@Composable
fun DeepLineApp(onThemeChange: (Color) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(LocalStore.init(context)) }
    var revision by remember { mutableIntStateOf(0) }
    val revisionSnapshot = revision
    fun persist() { LocalStore.save(context, data); revision++ }
    var tab by remember { mutableStateOf("聊天") }
    var page by remember { mutableStateOf("home") }
    var currentId by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var personaApiKey by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf(data.optString("myName","我")) }
    var myBio by remember { mutableStateOf(data.optString("myBio")) }
    var themeHex by remember { mutableStateOf(data.optString("theme","#06C755")) }
    var personaName by remember { mutableStateOf("") }
    var personaBio by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("其他") }
    var relation by remember { mutableStateOf("朋友") }
    var likes by remember { mutableStateOf("") }
    var dislikes by remember { mutableStateOf("") }
    var hobbies by remember { mutableStateOf("") }
    var feedText by remember { mutableStateOf("") }
    var postImage by remember { mutableStateOf("") }
    var commentPostId by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var autoAiEnabled by remember { mutableStateOf(data.optBoolean("autoAiEnabled", false)) }
    var editAvatar by remember { mutableStateOf("") }
    var editBackground by remember { mutableStateOf("") }
    var selectedImageTarget by remember { mutableStateOf("avatar") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val target = selectedImageTarget
            val storedImage = try { copyPickedImageToAppStorage(context, uri, target) } catch (e: Exception) {
                error = "图片保存失败：${e.message ?: "请重新选择图片"}"
                ""
            }
            if (storedImage.isNotBlank()) {
                when (target) {
                    "myAvatar" -> { data.put("myAvatar", storedImage); persist() }
                    "personaAvatar" -> editAvatar = storedImage
                    "background" -> { data.put("background", storedImage); persist() }
                    "postImage" -> postImage = storedImage
                }
            }
        }
    }
    fun saveData() {
        data.put("myName",myName).put("myBio",myBio).put("theme",themeHex)
        try { onThemeChange(Color(android.graphics.Color.parseColor(themeHex))) } catch (_: Exception) { }
        persist()
    }
    fun personas(): JSONArray = data.optJSONArray("personas") ?: JSONArray().also { data.put("personas",it) }
    fun findPersona(id:String): JSONObject? { val a=personas(); for(i in 0 until a.length()) if(a.getJSONObject(i).optString("id")==id)return a.getJSONObject(i); return null }
    fun createPersona() {
        if(personaName.isBlank()||persona.isBlank()) { error="名字和人设为必填项"; return }
        if(personaApiKey.isBlank()) { error="请填写这个 AI 使用的 API Key"; return }
        if(personas().length()>=10) { error="最多创建 10 个聊天对象"; return }
        val p=JSONObject().put("id",System.currentTimeMillis().toString()).put("name",personaName).put("bio",personaBio).put("persona",persona)
            .put("gender",gender).put("relationship",relation).put("likes",likes).put("dislikes",dislikes).put("hobbies",hobbies)
            .put("avatar",editAvatar).put("apiKey",personaApiKey.trim()).put("messages",JSONArray())
        personas().put(p);persist();personaName="";personaBio="";persona="";personaApiKey="";likes="";dislikes="";hobbies="";editAvatar="";error="";page="home";tab="聊天"
    }

    LaunchedEffect(autoAiEnabled) {
        if (autoAiEnabled) {
            while (true) {
                kotlinx.coroutines.delay(60L * 60L * 1000L)
                val all = personas()
                for (i in 0 until all.length()) {
                    val ai = all.optJSONObject(i) ?: continue
                    try {
                        val history = JSONArray()
                        history.put(JSONObject().put("role", "user").put("content", "请根据你的人设，写一条自然、简短、像你自己主动分享的动态。只返回动态正文。"))
                        val sys = "你正在扮演 ${ai.optString("name")}。人设：${ai.optString("persona")}。喜欢：${ai.optString("likes")}。爱好：${ai.optString("hobbies")}。"
                        val postText = DeepSeek.reply(ai.optString("apiKey"), sys, history)
                        val posts = data.optJSONArray("posts") ?: JSONArray().also { data.put("posts", it) }
                        posts.put(0, JSONObject().put("id", System.currentTimeMillis().toString()).put("author", ai.optString("name")).put("avatar", ai.optString("avatar")).put("content", postText).put("likes", 0).put("comments", JSONArray()).put("image", "").put("ai", true))
                        val msgs = ai.optJSONArray("messages") ?: JSONArray().also { ai.put("messages", it) }
                        msgs.put(JSONObject().put("role", "assistant").put("content", "我刚刚发了一条动态：$postText"))
                        LocalStore.save(context, data)
                    } catch (_: Exception) { }
                }
                data = LocalStore.load(context)
            }
        }
    }

    Surface(Modifier.fillMaxSize().then(if (revisionSnapshot >= 0) Modifier else Modifier),color=Page) {
        AnimatedContent(page,transitionSpec={fadeIn() togetherWith fadeOut()},label="pages") { screen ->
            when(screen) {
                "create" -> PersonaEditor(
                    name=personaName,onName={personaName=it},bio=personaBio,onBio={personaBio=it},persona=persona,onPersona={persona=it},apiKey=personaApiKey,onApiKey={personaApiKey=it},
                    gender=gender,onGender={gender=it},relation=relation,onRelation={relation=it},likes=likes,onLikes={likes=it},
                    dislikes=dislikes,onDislikes={dislikes=it},hobbies=hobbies,onHobbies={hobbies=it},avatar=editAvatar,
                    onPickAvatar={selectedImageTarget="personaAvatar";imagePicker.launch("image/*")},error=error,onBack={page="home"},onCreate={createPersona()})
                "chat" -> {
                    val p=findPersona(currentId)
                    if(p==null) { page="home" } else ChatScreen(p=p,myName=myName,myAvatar=data.optString("myAvatar"),composer=composer,onComposer={composer=it},busy=busy,error=error,backgroundUri=data.optString("background"),onBack={page="home"},onSend={
                        val msg=composer.trim()
                        if(msg.isNotEmpty()) {
                            val arr=p.optJSONArray("messages")?:JSONArray().also{p.put("messages",it)}
                            arr.put(JSONObject().put("role","user").put("content",msg))
                            arr.put(JSONObject().put("role","assistant").put("content","---"))
                            composer="";persist()
                            scope.launch {
                                busy=true;error=""
                                try {
                                    val history=JSONArray()
                                    val start=maxOf(0,arr.length()-31)
                                    for(i in start until arr.length()-1) { val m=arr.getJSONObject(i); if(m.optString("content")!="---") history.put(JSONObject().put("role",m.optString("role")).put("content",m.optString("content"))) }
                                    val system="你正在扮演 AI 角色。名字：${p.optString("name")}。简介：${p.optString("bio")}。人设：${p.optString("persona")}。性别：${p.optString("gender")}。与用户关系：${p.optString("relationship")}。喜欢：${p.optString("likes")}。讨厌：${p.optString("dislikes")}。爱好：${p.optString("hobbies")}。请遵循人设并用用户使用的语言自然回复。你是 AI，不要声称现实中不存在的经历。"
                                    val reply=DeepSeek.reply(p.optString("apiKey"),system,history)
                                    arr.put(arr.length()-1,JSONObject().put("role","assistant").put("content",reply));persist()
                                } catch(e:Exception) { arr.put(arr.length()-1,JSONObject().put("role","assistant").put("content","请求失败：${e.message}"));persist();error=e.message ?: "请求失败" }
                                finally { busy=false }
                            }
                        }
                    })
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(data.optString("myAvatar"),myName,44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(when(tab){"聊天"->"消息";"动态"->"动态广场";else->"设置"},fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=Ink);Text(if(tab=="聊天") "和你的 AI 好友聊聊吧" else "DeepLine · 私人空间",fontSize=12.sp,color=Muted) }
                        if(tab=="聊天") IconButton(onClick={page="create"},modifier=Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha=.12f),CircleShape)){Icon(Icons.Default.Add,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(27.dp))}
                    }
                    when(tab) {
                        "聊天" -> {
                            val a=personas()
                            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=14.dp)) {
                                if(a.length()==0)item{EmptyState("还没有聊天对象","点击右上角 + 创建第一个 AI")}
                                items((0 until a.length()).map{a.getJSONObject(it)},key={it.optString("id")}) { p ->
                                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp).background(Color.White,RoundedCornerShape(22.dp)).clickable{currentId=p.optString("id");page="chat"}.padding(horizontal=15.dp, vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Avatar(p.optString("avatar"),p.optString("name"),48)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(p.optString("name"),fontWeight=FontWeight.SemiBold)
                                            val lastMessage = p.optJSONArray("messages")?.let { messages -> if (messages.length() > 0) messages.optJSONObject(messages.length() - 1)?.optString("content") else null }
                                            val preview = if (lastMessage == "---") "正在输入…" else lastMessage?.takeIf { it.isNotBlank() } ?: p.optString("bio").ifBlank { p.optString("persona") }
                                            Text(preview,maxLines=1,color=Color.Gray,fontSize=13.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight,null,tint=Color.LightGray)
                                    }
                                }
                            }
                        }
                        "动态" -> {
                            Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).padding(14.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) { Avatar(data.optString("myAvatar"),myName,42); Spacer(Modifier.width(10.dp)); Text("分享你的日常",fontWeight=FontWeight.SemiBold,color=Ink) }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(feedText,{feedText=it},placeholder={Text("今天有什么新鲜事？")},modifier=Modifier.fillMaxWidth(),minLines=2,shape=RoundedCornerShape(18.dp))
                                    if(postImage.isNotBlank()) { Spacer(Modifier.height(8.dp)); PickedPhoto(postImage, Modifier.fillMaxWidth().height(150.dp)); TextButton(onClick={postImage=""}) { Text("移除图片") } }
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={selectedImageTarget="postImage";imagePicker.launch("image/*")}) { Icon(Icons.Default.Image,null); Spacer(Modifier.width(5.dp)); Text("添加图片") }
                                        Button(onClick={if(feedText.isNotBlank()||postImage.isNotBlank()){val posts=data.optJSONArray("posts")?:JSONArray().also{data.put("posts",it)};posts.put(0,JSONObject().put("id",System.currentTimeMillis().toString()).put("author",myName).put("avatar",data.optString("myAvatar")).put("content",feedText).put("image",postImage).put("likes",0).put("comments",JSONArray()).put("ai",false));feedText="";postImage="";persist()}},shape=RoundedCornerShape(22.dp)){Text("发布动态")}
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                LazyColumn(contentPadding=PaddingValues(bottom=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    items((0 until (data.optJSONArray("posts")?.length()?:0)).map{data.getJSONArray("posts").getJSONObject(it)},key={it.optString("id")}){post->
                                        Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(22.dp)).padding(16.dp)) {
                                            Row(verticalAlignment=Alignment.CenterVertically) { Avatar(post.optString("avatar"),post.optString("author"),42); Spacer(Modifier.width(10.dp)); Column { Text(post.optString("author"),fontWeight=FontWeight.Bold,color=Ink); Text(if(post.optBoolean("ai")) "AI 好友" else "刚刚",fontSize=11.sp,color=Muted) } }
                                            if(post.optString("content").isNotBlank()) Text(post.optString("content"),modifier=Modifier.padding(top=12.dp),fontSize=15.sp,color=Ink)
                                            if(post.optString("image").isNotBlank()) { Spacer(Modifier.height(10.dp)); PickedPhoto(post.optString("image"), Modifier.fillMaxWidth().height(190.dp)) }
                                            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=8.dp)) {
                                                TextButton(onClick={post.put("likes",post.optInt("likes")+1);persist()}) { Icon(Icons.Default.FavoriteBorder,null,tint=Color(0xFFFF6680)); Spacer(Modifier.width(4.dp)); Text("${post.optInt("likes")}") }
                                                TextButton(onClick={commentPostId=post.optString("id")}) { Icon(Icons.Default.ChatBubbleOutline,null,tint=Muted); Spacer(Modifier.width(4.dp)); Text("评论 ${post.optJSONArray("comments")?.length()?:0}") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp)) {
                            item {
                                Text("个人资料",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.padding(vertical=10.dp))
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Avatar(data.optString("myAvatar"),myName,68)
                                    Spacer(Modifier.width(12.dp))
                                    OutlinedButton(onClick={selectedImageTarget="myAvatar";imagePicker.launch("image/*")}){Text("从相册选择头像")}
                                }
                                OutlinedTextField(myName,{myName=it},label={Text("名字")},modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(myBio,{myBio=it},label={Text("简介")},modifier=Modifier.fillMaxWidth(),minLines=2)
                                Button(onClick={saveData()},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("保存资料")}
                                Spacer(Modifier.height(18.dp));Text("AI 接口说明",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                Text("创建 AI 时单独填写 API Key。密钥仅保存在本机应用数据中，不会上传到 GitHub 或任何自建后端。请勿把含密钥的备份分享给他人。",fontSize=13.sp,color=Color.Gray)
                                Spacer(Modifier.height(18.dp));Text("主题颜色",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=8.dp)){listOf("#06C755","#4F7DFF","#A66CFF","#FF8A65","#222222").forEach{hex->Box(Modifier.size(36.dp).background(Color(android.graphics.Color.parseColor(hex)),CircleShape).clickable{themeHex=hex;saveData()})}}
                                OutlinedButton(onClick={selectedImageTarget="background";imagePicker.launch("image/*")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Spacer(Modifier.width(8.dp));Text("选择动态/聊天背景图")}
                                Spacer(Modifier.height(18.dp))
                                Text("AI 主动互动",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                Text("开启后，应用运行期间每隔约 1 小时尝试让每个 AI 发布一条动态，并向自己的聊天记录发送动态通知。会消耗对应 API 额度。",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Text("允许 AI 自动发布动态",modifier=Modifier.weight(1f)); Switch(checked=autoAiEnabled,onCheckedChange={autoAiEnabled=it;data.put("autoAiEnabled",it);persist()}) }
                                Text("聊天记录和动态保存在此设备。卸载应用或清除应用数据可能会删除它们。",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=10.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=10.dp).background(Color.White,RoundedCornerShape(30.dp)).padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceAround) {
                        Nav("聊天",tab=="聊天",Icons.Default.ChatBubbleOutline){tab="聊天"}
                        Nav("动态",tab=="动态",Icons.Default.DynamicFeed){tab="动态"}
                        Nav("设置",tab=="设置",Icons.Default.Settings){tab="设置"}
                    }
                }
            }
        }
        if (commentPostId.isNotBlank()) {
            val post = (0 until (data.optJSONArray("posts")?.length() ?: 0)).mapNotNull { data.optJSONArray("posts")?.optJSONObject(it) }.firstOrNull { it.optString("id") == commentPostId }
            if (post != null) {
                val comments = post.optJSONArray("comments") ?: JSONArray().also { post.put("comments", it) }
                AlertDialog(
                    onDismissRequest={commentPostId=""},
                    title={Text("评论与回复")},
                    text={Column {
                        LazyColumn(Modifier.heightIn(max=240.dp)) {
                            items((0 until comments.length()).mapNotNull { comments.optJSONObject(it) }) { c ->
                                Column(Modifier.fillMaxWidth().padding(vertical=5.dp).background(Page,RoundedCornerShape(12.dp)).padding(10.dp)) {
                                    Text(c.optString("author"),fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                                    Text(c.optString("content"),fontSize=14.sp,color=Ink)
                                    if(c.optString("reply").isNotBlank()) Text("回复：${c.optString("reply")}",fontSize=12.sp,color=Muted)
                                    TextButton(onClick={commentText="回复 ${c.optString("author")}: "}) { Text("回复") }
                                }
                            }
                        }
                        OutlinedTextField(commentText,{commentText=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("写下评论或回复…")},maxLines=3)
                    }},
                    confirmButton={TextButton(onClick={if(commentText.isNotBlank()){comments.put(JSONObject().put("author",myName).put("content",commentText).put("reply",""));persist();commentText="";commentPostId=""}}){Text("发送")}},
                    dismissButton={TextButton(onClick={commentPostId=""}){Text("关闭")}}
                )
            }
        }
    }
}

@Composable fun Nav(label:String,selected:Boolean,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit){val accent=MaterialTheme.colorScheme.primary;Column(Modifier.clickable{click()}.padding(horizontal=16.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=if(selected)accent else Color.Gray);Text(label,fontSize=11.sp,color=if(selected)accent else Color.Gray)}}
private fun copyPickedImageToAppStorage(context: android.content.Context, source: Uri, prefix: String): String {
    val directory = File(context.filesDir, "deepline-media").apply { mkdirs() }
    val destination = File(directory, "${prefix}_${UUID.randomUUID()}.img")
    context.contentResolver.openInputStream(source).use { input ->
        requireNotNull(input) { "无法读取所选图片" }
        destination.outputStream().use { output -> input.copyTo(output) }
    }
    return Uri.fromFile(destination).toString()
}

private fun loadLocalBitmap(context: android.content.Context, value: String) = try {
    if (value.isBlank()) null else {
        val uri = Uri.parse(value)
        val bitmap = if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path)
        else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        bitmap?.asImageBitmap()
    }
} catch (_: Exception) { null }

@Composable fun Avatar(uri:String,name:String,size:Int){
    val context=LocalContext.current
    val bitmap=remember(uri){loadLocalBitmap(context,uri)}
    if(bitmap!=null) Image(bitmap,null,Modifier.size(size.dp).clip(CircleShape).background(Color.White),contentScale=ContentScale.Crop)
    else Box(Modifier.size(size.dp).background(Color(0xFFE4F6EA),CircleShape),contentAlignment=Alignment.Center){Text(name.take(1).ifBlank{"?"},color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=(size/2).sp)}
}
@Composable fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(top=60.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Forum,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(48.dp));Spacer(Modifier.height(12.dp));Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(subtitle,color=Color.Gray,fontSize=13.sp)}}

@Composable fun PersonaEditor(name:String,onName:(String)->Unit,bio:String,onBio:(String)->Unit,persona:String,onPersona:(String)->Unit,apiKey:String,onApiKey:(String)->Unit,gender:String,onGender:(String)->Unit,relation:String,onRelation:(String)->Unit,likes:String,onLikes:(String)->Unit,dislikes:String,onDislikes:(String)->Unit,hobbies:String,onHobbies:(String)->Unit,avatar:String,onPickAvatar:()->Unit,error:String,onBack:()->Unit,onCreate:()->Unit){
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Text("创建 AI 人设",fontWeight=FontWeight.Bold,fontSize=20.sp)}
        LazyColumn(Modifier.weight(1f).padding(horizontal=18.dp),contentPadding=PaddingValues(bottom=16.dp)){
            item{
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(vertical=12.dp)){Avatar(avatar,name,72);Spacer(Modifier.width(12.dp));OutlinedButton(onClick=onPickAvatar){Text("从相册选择头像")}}
                OutlinedTextField(name,onName,label={Text("名字 *")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(bio,onBio,label={Text("简介")},modifier=Modifier.fillMaxWidth(),minLines=2)
                OutlinedTextField(persona,onPersona,label={Text("人设 *")},modifier=Modifier.fillMaxWidth(),minLines=3)
                OutlinedTextField(apiKey,onApiKey,label={Text("DeepSeek API Key *")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
                Text("必须填写可用的 API Key 才能创建此 AI。费用和额度由 API 服务商决定。",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=4.dp))
                Text("性别",fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=12.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("男","女","其他").forEach{FilterChip(selected=gender==it,onClick={onGender(it)},label={Text(it)})}}
                Text("与你的关系",fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("家人","情侣","哥哥","弟弟","朋友","亲戚").forEach{FilterChip(selected=relation==it,onClick={onRelation(it)},label={Text(it)})}}
                Text("选填：角色偏好",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp,bottom=8.dp))
                OutlinedTextField(likes,onLikes,label={Text("喜欢什么")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(dislikes,onDislikes,label={Text("讨厌什么")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(hobbies,onHobbies,label={Text("爱好")},modifier=Modifier.fillMaxWidth())
                if(error.isNotBlank())Text(error,color=Color.Red,modifier=Modifier.padding(top=8.dp))
            }
        }
        Button(onClick=onCreate,modifier=Modifier.fillMaxWidth().padding(16.dp).height(52.dp),shape=RoundedCornerShape(28.dp)){Text("创建")}
    }
}

@Composable fun PickedPhoto(uri:String, modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val bitmap=remember(uri){loadLocalBitmap(context,uri)}
    if(bitmap!=null) Image(bitmap,contentDescription="已选择的图片",modifier=modifier.clip(RoundedCornerShape(16.dp)),contentScale=ContentScale.Crop)
}

@Composable fun FloatingTypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val y by transition.animateFloat(
                initialValue = 2f, targetValue = -5f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 420, delayMillis = index * 120, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "typing-dot-$index"
            )
            Box(Modifier.offset(y = y.dp).size(7.dp).background(Color(0xFF87948D), CircleShape))
        }
    }
}

@Composable fun ChatScreen(p:JSONObject,myName:String,myAvatar:String,composer:String,onComposer:(String)->Unit,busy:Boolean,error:String,backgroundUri:String,onBack:()->Unit,onSend:()->Unit){
    val arr=p.optJSONArray("messages")?:JSONArray()
    val listState = rememberLazyListState()
    LaunchedEffect(arr.length(), busy) { if (arr.length() > 0) listState.animateScrollToItem(arr.length() - 1) }
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Avatar(p.optString("avatar"),p.optString("name"),42);Spacer(Modifier.width(10.dp));Column{Text(p.optString("name"),fontWeight=FontWeight.Bold,fontSize=18.sp);Text(p.optString("relationship"),fontSize=12.sp,color=Color.Gray)}}
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if(backgroundUri.isNotBlank()) PickedPhoto(backgroundUri,Modifier.fillMaxSize())
        LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            items((0 until arr.length()).map{arr.getJSONObject(it)},key={it.optString("id",it.optString("content")+it.optString("role")+it.hashCode())}){m->
                val user=m.optString("role")=="user"
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))
                ) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
                        if (!user) { Avatar(p.optString("avatar"), p.optString("name"), 34); Spacer(Modifier.width(7.dp)) }
                        Box(modifier = Modifier.widthIn(max=300.dp).background(
                            if(user) Color(0xFFD9F8C6) else Color.White,
                            RoundedCornerShape(topStart=18.dp, topEnd=18.dp, bottomStart=if(user)18.dp else 5.dp, bottomEnd=if(user)5.dp else 18.dp)
                        ).padding(horizontal=13.dp, vertical=10.dp)) {
                            if (!user && m.optString("content") == "---") FloatingTypingIndicator()
                            else Text(m.optString("content"),fontSize=15.sp,color=Ink)
                        }
                        if (user) { Spacer(Modifier.width(7.dp)); Avatar(myAvatar, myName, 34) }
                    }
                }
            }
        }
        }
        if(error.isNotBlank())Text(error,color=Color.Red,modifier=Modifier.padding(horizontal=12.dp))
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(composer,onComposer,modifier=Modifier.weight(1f),placeholder={Text("发送消息…")},shape=RoundedCornerShape(22.dp),maxLines=4);IconButton(onClick=onSend,enabled=!busy){Icon(Icons.Default.Send,null,tint=MaterialTheme.colorScheme.primary)}}
    }
}
