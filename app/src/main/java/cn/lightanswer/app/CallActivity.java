package cn.lightanswer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallEndpoint;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CallActivity extends Activity {
    private int selected=-1;
    private TextView duration;
    private boolean keypad;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable listener=() -> runOnUiThread(this::render);
    private final Runnable tick=new Runnable() { public void run() {
        AnswerService service=AnswerService.current;Call c=service==null?null:service.find(selected);
        if (c!=null && duration!=null) {
            long start=c.getDetails().getConnectTimeMillis();
            long seconds=start==0?0:Math.max(0,(System.currentTimeMillis()-start)/1000);
            duration.setText(start==0?AnswerService.stateLabel(c):String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60));
        } handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state) {super.onCreate(state);selected=getIntent().getIntExtra("callId",-1);setShowWhenLocked(true);setTurnScreenOn(true);}
    @Override protected void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);selected=intent.getIntExtra("callId",-1);render();}
    @Override protected void onResume() {super.onResume();AnswerService.listeners.add(listener);render();handler.post(tick);}
    @Override protected void onPause() {AnswerService.listeners.remove(listener);handler.removeCallbacks(tick);super.onPause();}
    private int dp(int n) {return Ui.dp(this,n);}
    private void render() {
        if (isFinishing()) return;
        AnswerService service=AnswerService.current;Call call=service==null?null:service.find(selected);
        if (call==null) {finish();return;} selected=service.id(call);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(0xFF142344);setContentView(root);Ui.insets(this,root,true);
        ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.column(this);body.setGravity(Gravity.CENTER_HORIZONTAL);body.setPadding(dp(24),dp(28),dp(24),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        TextView brand=Ui.text(this,"轻 接  /  "+AnswerService.stateLabel(call),14,0xFFAEBFDF,true);brand.setGravity(Gravity.CENTER);body.addView(brand);Ui.space(body,38);
        TextView number=Ui.text(this,AnswerService.number(call),30,Color.WHITE,true);number.setGravity(Gravity.CENTER);body.addView(number,Ui.full());Ui.space(body,14);
        duration=Ui.text(this,AnswerService.stateLabel(call),20,0xFFA9E9CD,false);duration.setGravity(Gravity.CENTER);body.addView(duration);Ui.space(body,30);
        List<Call> calls=service.calls();
        if (calls.size()>1) {
            for (Call c:calls) if (c!=call) {
                Button other=Ui.button(this,AnswerService.number(c)+" · "+AnswerService.stateLabel(c),0xFF25385B,Color.WHITE);
                other.setOnClickListener(v -> {selected=service.id(c);render();});body.addView(other,Ui.full());Ui.space(body,10);
            }
        }
        if (AnswerService.state(call)==Call.STATE_SELECT_PHONE_ACCOUNT) {
            Bundle extras=call.getDetails().getExtras();
            ArrayList<PhoneAccountHandle> accounts=extras==null?null:extras.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS);
            if (accounts!=null) for (int i=0;i<accounts.size();i++) {
                PhoneAccountHandle account=accounts.get(i);String label="SIM "+(i+1);
                try {PhoneAccount p=getSystemService(TelecomManager.class).getPhoneAccount(account);if(p!=null)label=p.getLabel().toString();}catch(SecurityException ignored){}
                Button b=Ui.button(this,label,Ui.BLUE,Color.WHITE);b.setOnClickListener(v -> call.phoneAccountSelected(account,false));body.addView(b,Ui.full());Ui.space(body,10);
            }
        }
        LinearLayout controls=Ui.row(this);
        Button mute=Ui.button(this,service.isMuted()?"取消静音":"静音",0xFF25385B,Color.WHITE);
        mute.setOnClickListener(v -> service.setMuted(!service.isMuted()));addControl(controls,mute);
        Button speaker=Ui.button(this,service.isSpeaker()?"免提已开":"音频",0xFF25385B,Color.WHITE);
        speaker.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT>=34 && !service.endpoints().isEmpty()) {
                List<CallEndpoint> endpoints=service.endpoints();String[] labels=new String[endpoints.size()];for(int i=0;i<labels.length;i++)labels[i]=endpoints.get(i).getEndpointName().toString();
                new AlertDialog.Builder(this).setTitle("通话音频").setItems(labels,(d,which)->service.route(endpoints.get(which))).show();
            }else service.routeSpeaker(!service.isSpeaker());
        });addControl(controls,speaker);body.addView(controls);Ui.space(body,10);
        LinearLayout more=Ui.row(this);
        Button keys=Ui.button(this,keypad?"收起键盘":"拨号键盘",0xFF25385B,Color.WHITE);keys.setOnClickListener(v -> {keypad=!keypad;render();});addControl(more,keys);
        boolean held=AnswerService.state(call)==Call.STATE_HOLDING;
        Button hold=Ui.button(this,held?"恢复通话":"保持",0xFF25385B,Color.WHITE);hold.setEnabled(held||call.getDetails().can(Call.Details.CAPABILITY_HOLD));hold.setAlpha(hold.isEnabled()?1f:0.4f);
        hold.setOnClickListener(v -> {if(held)call.unhold();else call.hold();});addControl(more,hold);body.addView(more);
        if (!call.getConferenceableCalls().isEmpty()) {
            Ui.space(body,10);Button merge=Ui.button(this,"合并通话",0xFF25385B,Color.WHITE);merge.setOnClickListener(v -> call.conference(call.getConferenceableCalls().get(0)));body.addView(merge,Ui.full());
        }
        if(keypad) {Ui.space(body,20);String[] digits={"1","2","3","4","5","6","7","8","9","*","0","#"};
            for(int i=0;i<4;i++){LinearLayout row=Ui.row(this);for(int j=0;j<3;j++){String digit=digits[i*3+j];Button b=Ui.button(this,digit,0xFF25385B,Color.WHITE);b.setOnClickListener(v->{call.playDtmfTone(digit.charAt(0));handler.postDelayed(call::stopDtmfTone,130);});addControl(row,b);}body.addView(row);Ui.space(body,6);}
        }
        Ui.space(body,32);
        if(AnswerService.ringing(call)){Button answer=Ui.button(this,"接听",Ui.GREEN,Color.WHITE);answer.setOnClickListener(v -> service.answer(call));body.addView(answer,Ui.full());Ui.space(body,12);}
        Button hangup=Ui.button(this,AnswerService.ringing(call)?"拒接":"挂断",Ui.RED,Color.WHITE);hangup.setOnClickListener(v -> service.hangup(call));body.addView(hangup,Ui.full());
        Ui.space(body,16);Button back=Ui.button(this,"返回首页",Color.TRANSPARENT,0xFFAEBFDF);back.setOnClickListener(v -> startActivity(new Intent(this,MainActivity.class)));body.addView(back);
    }
    private void addControl(LinearLayout row,Button b) {LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.setMargins(dp(4),0,dp(4),0);row.addView(b,p);}
}
