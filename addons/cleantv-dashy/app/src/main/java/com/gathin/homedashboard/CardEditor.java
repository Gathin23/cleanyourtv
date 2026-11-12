package com.gathin.homedashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** On-TV editor for three read-only state bindings. Credentials stay in the private setup file. */
final class CardEditor {
    private final Activity activity;
    private final Config config;
    private final ExecutorService worker;
    private final Runnable changed;
    CardEditor(Activity activity,Config config,ExecutorService worker,Runnable changed){
        this.activity=activity;this.config=config;this.worker=worker;this.changed=changed;
    }
    void show(){
        String[] labels=new String[3];
        for(int i=0;i<labels.length;i++){
            SensorCard card=config.cards.get(i);
            labels[i]="Card "+(i+1)+": "+card.label+(card.enabled?"\n"+card.entity:" · Hidden");
        }
        new AlertDialog.Builder(activity).setTitle("Sensor cards").setItems(labels,(d,i)->edit(i))
            .setNeutralButton("Use setup defaults",(d,w)->new AlertDialog.Builder(activity).setTitle("Restore sensor cards?")
                .setMessage("Replace your card edits with the cards from your private setup file?")
                .setNegativeButton("Cancel",null).setPositiveButton("Restore",(confirm,which)->{
                    activity.getSharedPreferences("choices",Context.MODE_PRIVATE).edit().remove("sensorCards").apply();changed.run();
                }).show()).setNegativeButton("Close",null).show();
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(20),dp(8),dp(20),dp(8));return view;}
    private void label(LinearLayout form,String title){TextView text=new TextView(activity);text.setText(title);text.setTextSize(14);form.addView(text);}
    private EditText field(LinearLayout form,String title,String value,int max){
        label(form,title);EditText input=new EditText(activity);input.setSingleLine(true);input.setText(value);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);form.addView(input);return input;
    }
    private Spinner choices(LinearLayout form,String title,String[] choices,int selected){
        label(form,title);Spinner spinner=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(selected);
        form.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));return spinner;
    }
    private void edit(int slot){
        SensorCard previous=config.cards.get(slot);LinearLayout form=column();
        CheckBox enabled=new CheckBox(activity);enabled.setText("Show this card");enabled.setChecked(previous.enabled);form.addView(enabled);
        EditText title=field(form,"Card label",previous.label,40);
        EditText entity=field(form,"Home Assistant entity",previous.entity,255);
        entity.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        entity.setHint("sensor.room_temperature");
        Button browse=new Button(activity);browse.setText("Browse Home Assistant entities…");browse.setAllCaps(false);form.addView(browse);
        int selected=java.util.Arrays.asList(SensorCard.MODES).indexOf(previous.mode);
        Spinner mode=choices(form,"Display",new String[]{"Auto · entity state and unit","Percentage · 0–100% with bar","Power · W or kW shown as kW"},selected);
        Spinner decimals=choices(form,"Decimal places",new String[]{"0","1","2","3"},previous.decimals);
        EditText colour=field(form,"Value colour (#RRGGBB)",previous.colour,7);
        TextView hint=new TextView(activity);hint.setText("Auto keeps the entity’s unit and supports text or on/off states. Cards only read values; they never control devices.");hint.setTextSize(14);form.addView(hint);
        ScrollView scroll=new ScrollView(activity);scroll.setFocusable(false);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Edit card "+(slot+1)).setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        browse.setOnClickListener(v->browse(entity,title,dialog));
        dialog.setOnShowListener(v->{
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            enabled.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                try{
                    SensorCard next=new SensorCard(new JSONObject().put("label",title.getText().toString())
                        .put("entity",entity.getText().toString()).put("mode",SensorCard.MODES[mode.getSelectedItemPosition()])
                        .put("decimals",decimals.getSelectedItemPosition()).put("colour",colour.getText().toString().trim())
                        .put("enabled",enabled.isChecked()),slot);
                    List<SensorCard> cards=new ArrayList<>(config.cards);cards.set(slot,next);
                    activity.getSharedPreferences("choices",Context.MODE_PRIVATE).edit().putString("sensorCards",SensorCard.json(cards).toString()).apply();
                    dialog.dismiss();changed.run();
                }catch(IllegalArgumentException e){toast(e.getMessage());}
                catch(org.json.JSONException e){toast("Could not save this card");}
            });
        });dialog.show();
    }
    private boolean alive(){return !activity.isFinishing()&&!activity.isDestroyed();}
    private void toast(String text){if(alive())Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private void browse(EditText entity,EditText title,AlertDialog editor){
        toast("Reading Home Assistant entities…");
        worker.execute(()->{try{
            List<EntityOption> all=new Api(config).entities();
            activity.runOnUiThread(()->{if(!alive()||!editor.isShowing())return;
                if(all.isEmpty()){toast("No entities found; you can enter an entity ID instead");return;}
                LinearLayout form=column();EditText query=new EditText(activity);query.setSingleLine(true);query.setHint("Search name or entity ID");form.addView(query);
                List<EntityOption> visible=new ArrayList<>(all);
                ArrayAdapter<EntityOption> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,visible);
                ListView list=new ListView(activity);list.setAdapter(adapter);form.addView(list,new LinearLayout.LayoutParams(-1,dp(240)));
                AlertDialog chooser=new AlertDialog.Builder(activity).setTitle("Choose entity").setView(form).setNegativeButton("Cancel",null).create();
                query.addTextChangedListener(new TextWatcher(){
                    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                    public void onTextChanged(CharSequence s,int start,int before,int count){
                        String filter=s.toString().toLowerCase(Locale.ROOT);List<EntityOption> matches=new ArrayList<>();
                        for(EntityOption option:all)if((option.id+" "+option.name).toLowerCase(Locale.ROOT).contains(filter))matches.add(option);
                        adapter.clear();adapter.addAll(matches);adapter.notifyDataSetChanged();
                    }
                    public void afterTextChanged(Editable e){}
                });
                list.setOnItemClickListener((parent,view,position,id)->{
                    EntityOption picked=adapter.getItem(position);if(picked==null)return;
                    entity.setText(picked.id);
                    if(title.getText().toString().isEmpty()||title.getText().toString().matches("Card [1-3]")){
                        String name=picked.name.isEmpty()?picked.id:picked.name;title.setText(name.substring(0,Math.min(40,name.length())));
                    }
                    android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(ime!=null)ime.hideSoftInputFromWindow(query.getWindowToken(),0);chooser.dismiss();
                });
                chooser.setOnShowListener(v->{chooser.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);list.requestFocus();});chooser.show();
            });
        }catch(Exception e){activity.runOnUiThread(()->{if(editor.isShowing())toast("Could not read entities; check the HA connection or enter an ID manually");});}});
    }
}
