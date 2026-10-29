"use strict";
// Original observation code. No third-party implementation or native replacement.
Java.perform(function () {
  const app=Java.use('android.app.ActivityThread').currentApplication();
  if(app===null||app.getPackageName().toString()!=='com.ss.android.ugc.aweme')throw new Error('Unexpected application');
  const info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);
  if(Number(info.versionCode.value)!==400701)throw new Error('Telemetry profile requires versionCode 400701');
  const System=Java.use('java.lang.System'), Clock=Java.use('android.os.SystemClock');
  const JObject=Java.use('java.lang.Object'), JMap=Java.use('java.util.Map'), JSONClass=Java.use('org.json.JSONObject');
  function emit(event,value){send({event:event,time_ms:Date.now(),monotonic_ns:Clock.elapsedRealtimeNanos().toString(),pid:Process.id,value:value});}
  function obj(v){return Java.cast(v,JObject);}
  function fields(v){
    if(v===null)return null;
    const o=obj(v),result={},fs=o.getClass().getFields();
    for(let i=0;i<fs.length;i++){
      const f=fs[i];if((f.getModifiers()&8)!==0)continue;
      const t=f.getType().getName().toString(),k=f.getName().toString();
      if(t==='boolean')result[k]=f.getBoolean(o);
      else if(t==='int')result[k]=f.getInt(o);
      else if(t==='long')result[k]=f.getLong(o).toString();
      else if(t==='float')result[k]=f.getFloat(o);
      else if(t==='double')result[k]=f.getDouble(o);
      else if(t==='java.lang.String'||f.getType().isEnum()){const value=f.get(o);result[k]=value===null?null:value.toString();}
    }
    return result;
  }
  function json(v){if(v===null)return null;try{return JSON.parse(v.toString());}catch(e){return {unparsed:true};}}
  function map(v){return v===null?null:json(JSONClass.$new(Java.cast(v,JMap)));}
  function playerId(v){try{return Java.cast(v,Java.use('com.ss.videoarch.liveplayer2.VeLivePlayer')).getPlayerId();}catch(e){return null;}}
  let hookCount=0;
  function hook(className,methodName,after,observer){
    try{
      const T=Java.use(className),method=T[methodName];if(!method)throw new Error('Method absent');
      method.overloads.forEach(function(o){
        o.implementation=function(){
          const a=Array.prototype.slice.call(arguments);
          function record(receiver,result){try{observer(receiver,a,result);}catch(e){emit('telemetry_capture_error',{class:className,method:methodName,error:String(e)});}}
          if(!after)record(this,null);
          const result=o.apply(this,a);
          if(after)record(this,result);
          return result;
        };
        hookCount++;
      });
      emit('telemetry_hook',{class:className,method:methodName,installed:true});
    }catch(e){emit('telemetry_hook',{class:className,method:methodName,installed:false,error:String(e)});}
  }
  const observer='com.bytedance.android.livesdk.player.LiveSdkObserverClientImplProxy2';
  hook(observer,'onStatistics',false,function(self,a){emit('telemetry_statistics',{layer:'native_observer',player_id:playerId(a[0]),statistics:fields(a[1])});});
  hook(observer,'onMonitorLog',false,function(self,a){emit('telemetry_monitor',{layer:'native_observer',player_id:playerId(a[0]),tag:a[2]===null?null:a[2].toString(),payload:json(a[1])});});
  ['onStallStart','onStallEnd','onAudioRenderStall','onVideoRenderStall','onFirstAudioFrameRender','onFirstVideoFrameRender'].forEach(function(m){
    hook(observer,m,false,function(self,a){emit('telemetry_callback',{layer:'native_observer',player_id:playerId(a[0]),method:m,arguments:a.slice(1).map(v=>v===null?null:typeof v==='object'?v.toString():v)});});
  });
  hook('com.bytedance.android.livesdk.player.LivePlayerClient','e',false,function(self,a){emit('telemetry_monitor',{layer:'business_client_entry',client_identity:System.identityHashCode(self),tag:a[1]===null?null:a[1].toString(),payload:json(a[0])});});
  hook('X.0BL2','LJJZZIII',false,function(self,a){emit('telemetry_monitor',{layer:'legacy_java_logger_entry',logger_identity:System.identityHashCode(self),tag:a[0]===null?null:a[0].toString(),payload:json(a[1])});});
  hook('com.bytedance.android.livesdk.player.LivePlayerAdapter$setAppInfoFetcher$appInfoFetcher$2','getAppInfo',true,function(self,a,result){emit('telemetry_app_info',{payload:json(result)});});
  hook('X.1djI','LIZ',true,function(self,a){emit('telemetry_performance',{layer:'frame_stall_composer',payload:map(a[0])});});
  ['X.1dlH','X.1dhX'].forEach(function(c){hook(c,'LJJLJ',false,function(self,a){
    const name=a[0]===null?'':a[0].toString();
    if(/^(livesdk_performance_|livesdk_live_videostall|livesdk_live_play)/.test(name))emit('telemetry_report',{layer:c,event_name:name,payload:map(a[1])});
  });});
  hook('X.1dAZ','LIZ',true,function(self,a){
    if(a[0]!==null)emit('telemetry_battery_observed',{source:'original_broadcast_receiver',temperature_raw:a[0].hasExtra('temperature')?a[0].getIntExtra('temperature',0):null});
  });
  emit('capture_ready',{source_version_code:400701,hook_count:hookCount,statistics_override:false,scope:'Observed Java callbacks, report entry payloads, and composer outputs only; missing hooks and quiet branches remain explicit coverage gaps'});
});
