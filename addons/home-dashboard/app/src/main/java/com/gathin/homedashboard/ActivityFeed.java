package com.gathin.homedashboard;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.net.SocketFactory;

/** Subscribe only, while the dashboard is visible. Never publishes Frigate controls. */
final class ActivityFeed {
    private final Config config;
    private final Api api;
    private final PersonFocus focus;
    private final Runnable changed;
    private volatile boolean running=true;
    private volatile MqttClient client;
    private final Thread thread;
    ActivityFeed(Config config,Api api,PersonFocus focus,Runnable changed){
        this.config=config;this.api=api;this.focus=focus;this.changed=changed;
        thread=new Thread(this::run,"dashboard-activity");thread.start();
    }
    private void run(){
        int attempts=0;
        while(running){
            MqttClient session=new MqttClient(config.mqttUrl,"dashboard-"+UUID.randomUUID().toString().substring(0,12),
                List.of(config.topicPrefix+"/reviews",config.topicPrefix+"/available"),new MqttClient.Listener(){
                    public void onConnected(){
                        android.util.Log.i("HomeDashboard","Person focus MQTT connected");
                        try{focus.seed(api.activeReviews());}catch(Exception ignored){}
                        changed.run();
                    }
                    public void onMessage(String topic,byte[] payload){
                        String value=new String(payload,StandardCharsets.UTF_8);
                        if(topic.equals(config.topicPrefix+"/reviews"))focus.message(value);
                        else if(value.equals("offline"))focus.clear();
                        changed.run();
                    }
                    public void onDisconnected(Exception cause){focus.clear();changed.run();}
                });
            client=session;if(!running){session.stop();return;}session.run(SocketFactory.getDefault());
            if(!running)return;
            try{Thread.sleep(MqttClient.backoffMs(attempts++));}catch(InterruptedException e){return;}
        }
    }
    void stop(){running=false;MqttClient c=client;if(c!=null)c.stop();thread.interrupt();focus.clear();}
}
