"use strict";
// Run through capture_liveplayer.py; records only this player's inputs and callbacks.
Java.perform(function () {
  const options=globalThis.LP_OPTIONS||{};
  const app=Java.use("android.app.ActivityThread").currentApplication();
  if(app===null||app.getPackageName().toString()!=="com.ss.android.ugc.aweme")throw new Error("Unexpected target application");
  const ObjectClass=Java.use("java.lang.Object"), System=Java.use("java.lang.System");
  const hooked=new Set();
  const providerOwners=Java.use("java.util.IdentityHashMap").$new();
  const IntegerClass=Java.use("java.lang.Integer");
  function emit(event,value){send({event:event,time_ms:Date.now(),pid:Process.id,value:value});}
  function object(v){return Java.cast(v,ObjectClass);}
  function scalar(v){
    if(v===null||v===undefined)return null;
    if(typeof v!=="object"&&typeof v!=="function")return v;
    try{const x=object(v);return {java_type:x.getClass().getName().toString(),value:x.toString()};}
    catch(e){return {unavailable:String(e)};}
  }
  function fields(v){
    if(v===null)return null;const x=object(v),out={},fs=x.getClass().getFields();
    for(let i=0;i<fs.length;i++){const f=fs[i];if((f.getModifiers()&8)!==0)continue;
      const t=f.getType().getName().toString(),k=f.getName().toString();
      if(t==="boolean")out[k]=f.getBoolean(x);
      else if(t==="int")out[k]=f.getInt(x);
      else if(t==="long")out[k]=f.getLong(x).toString();
      else if(t==="float")out[k]=f.getFloat(x);
      else if(t==="double")out[k]=f.getDouble(x);
      else if(t==="java.lang.String"){const y=f.get(x);out[k]=y===null?null:y.toString();}
      else if(f.getType().isEnum()){const y=f.get(x);out[k]=y===null?null:y.toString();}
    }return out;
  }
  function observeProvider(bundle,id){
    if(bundle===null)return;const obj=object(bundle),cls=obj.getClass(),name=cls.getName().toString();
    if(providerOwners.containsKey(obj)&&Java.cast(providerOwners.get(obj),IntegerClass).intValue()!==id){
      providerOwners.put(obj,IntegerClass.valueOf(-1));
      emit("capture_gap",{method:"settings_provider_owner",error:"One provider is shared across players; its lookup attribution is ambiguous"});
    }else providerOwners.put(obj,IntegerClass.valueOf(id));
    const T=Java.ClassFactory.get(cls.getClassLoader()).use(name);
    if(!T.getSettingsValueForKey)return;
    T.getSettingsValueForKey.overloads.forEach(function(o){
      const key=name+"#settings#"+o.argumentTypes.map(x=>x.className).join(",");if(hooked.has(key))return;hooked.add(key);
      o.implementation=function(){const a=Array.prototype.slice.call(arguments),r=o.apply(this,a);
        try{const owner=providerOwners.get(object(this));emit("settings_result",{player_id:owner===null?-1:Java.cast(owner,IntegerClass).intValue(),provider:name,key:String(a[0]),default_value:scalar(a[1]),result:scalar(r)});}catch(e){}
        return r;
      };
    });emit("settings_provider_observed",{player_id:id,class:name});
  }
  function observeCallbacks(observer,id){
    if(observer===null)return;const obj=object(observer),cls=obj.getClass(),name=cls.getName().toString();
    const T=Java.ClassFactory.get(cls.getClassLoader()).use(name);
    ["onStatistics","onMonitorLog","onFirstAudioFrameRender","onFirstVideoFrameRender","onStallStart","onStallEnd","onError"].forEach(function(m){
      if(!T[m])return;T[m].overloads.forEach(function(o){const key=name+"#"+m+"#"+o.argumentTypes.map(x=>x.className).join(",");if(hooked.has(key))return;hooked.add(key);
        o.implementation=function(){const a=Array.prototype.slice.call(arguments);
          try{const actualId=a[0]===null?-1:Java.cast(a[0],P).getPlayerId();emit("official_callback",{player_id:actualId,method:m,args:a.slice(1).map(function(x){return m==="onStatistics"||m==="onError"?fields(x):scalar(x);})});}catch(e){}
          return o.apply(this,a);
        };
      });
    });emit("observer_observed",{player_id:id,class:name});
  }
  const P=Java.use("com.ss.videoarch.liveplayer2.VeLivePlayer");
  const nativeVersion=P.getVersion().toString();
  // Keep the validated JNI subset: Native Bridge can lose lazy registrations when other native methods are replaced.
  const names=["cpp_setConfig","cpp_setPlayUrl","cpp_setStreamDataInfo","cpp_setIntProperty","cpp_setIntPropertyId","cpp_setStringProperty","cpp_setStringPropertyId","cpp_setSettingsBundle","cpp_setObserver","cpp_play","cpp_stop"];
  names.forEach(function(m){if(!P[m])return;P[m].overloads.forEach(function(o){
    o.implementation=function(){const a=Array.prototype.slice.call(arguments);let values;
      try{values=a.map(scalar);if(m==="cpp_setConfig")values=[a[0],fields(a[1])];emit("native_entry",{method:m,args:values});}catch(e){emit("capture_error",{method:m,error:String(e)});}
      try{
        if(m==="cpp_setSettingsBundle")observeProvider(a[1],a[0]);
        if(m==="cpp_setObserver")observeCallbacks(a[1],a[0]);
        if(m==="cpp_setConfig"&&options.statistics){const cfg=object(a[1]);cfg.getClass().getField("enableStatisticsCallback").setBoolean(cfg,true);cfg.getClass().getField("statisticsCallbackInterval").setInt(cfg,1);emit("diagnostic_override",{statistics:true,interval_seconds:1});}
      }catch(e){emit("capture_gap",{method:m,error:String(e)});}
      const result=o.apply(this,a);
      if(m==="cpp_play"){
        const p=Java.retain(this);setTimeout(function(){Java.perform(function(){
          try{const values={};["VeLivePlayerKeyGetStreamLog","VeLivePlayerKeyGetStreamBaseInfo"].forEach(function(k){const x=p.getProperty(k,Java.use("java.lang.String").$new(""));values[k]=x===null?null:x.toString();});emit("native_state",{player_id:a[0],properties:values});}catch(e){emit("state_read_error",String(e));}finally{p.$dispose();}
        });},2000);
      }return result;
    };
  });});
  emit("capture_ready",{native_version:nativeVersion,statistics_override:!!options.statistics,scope:"Observed JNI inputs, resolved settings and callbacks after attach; uncaptured historical globals remain a gap"});
});
