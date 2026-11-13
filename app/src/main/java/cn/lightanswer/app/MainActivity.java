package cn.lightanswer.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.CallLog;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private SettingsStore settings;
    private LinearLayout body;
    private int tab;
    private String dialNumber = "";
    private int renderedCount = -1;
    private boolean renderedCall;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable callListener = () -> runOnUiThread(() -> {
        AnswerService service = AnswerService.current;
        boolean hasCall = service != null && service.primary() != null;
        if (!isFinishing() && (settings.count() != renderedCount || hasCall != renderedCall)) render();
    });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); settings = new SettingsStore(this);
        if (state != null) { tab = state.getInt("tab"); dialNumber = state.getString("dial", ""); }
        readIntent(getIntent());
    }
    @Override protected void onResume() { super.onResume(); AnswerService.listeners.add(callListener); render(); }
    @Override protected void onPause() { AnswerService.listeners.remove(callListener); super.onPause(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); readIntent(intent); render(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("tab", tab); state.putString("dial", dialNumber); super.onSaveInstanceState(state); }
    private void readIntent(Intent i) {
        if (Intent.ACTION_DIAL.equals(i.getAction())) {
            tab = 1;
            if (i.getData() != null && "tel".equals(i.getData().getScheme())) dialNumber = i.getData().getSchemeSpecificPart();
        }
    }
    private boolean isDefault() { return getPackageName().equals(getSystemService(TelecomManager.class).getDefaultDialerPackage()); }

    private void render() {
        renderedCount = settings.count();
        AnswerService service = AnswerService.current;
        renderedCall = service != null && service.primary() != null;
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.BG); setContentView(root); Ui.insets(this, root, false);
        LinearLayout header = Ui.row(this); header.setPadding(dp(24), dp(16), dp(24), dp(12));
        TextView title = Ui.text(this, "轻接", 26, Ui.INK, true); header.addView(title, new LinearLayout.LayoutParams(0,-2,1));
        Button about = Ui.button(this,"使用说明", Color.TRANSPARENT, Ui.MUTED);
        about.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("轻接 1.0")
                .setMessage("1. 设为默认电话应用。\n2. 保持自动接听开关开启。\n3. 来电后立即向系统请求接通，不添加等待时间；陌生和隐藏号码也会接听。\n\n关屏和锁屏时由系统通话服务处理，无需常亮或一直打开本页面。\n\n接通后由你本人说话，可选免提。支持系统管理的电话来电，不支持微信 / QQ 等应用内来电。\n\n部分品牌需在应用设置中允许自启动和后台运行。实际接通时间取决于系统与运营商。\n\n本应用无联网权限，设置保存在本机。")
                .setPositiveButton("知道了", null).show()); header.addView(about); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        body = Ui.column(this); body.setPadding(dp(24), dp(8), dp(24), dp(24)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        if (tab == 0) home(); else if (tab == 1) dialer(); else history();
        LinearLayout nav = Ui.row(this); nav.setPadding(dp(12),dp(8),dp(12),dp(8)); nav.setBackgroundColor(Color.WHITE);
        String[] tabs = {"自动接听", "拨号", "通话记录"};
        for (int i=0;i<tabs.length;i++) {
            final int selected = i; Button b = Ui.button(this,tabs[i],i==tab ? 0xFFEDF1FF : Color.TRANSPARENT, i==tab ? Ui.BLUE : Ui.MUTED);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,dp(52),1); p.setMargins(dp(3),0,dp(3),0); nav.addView(b,p);
            b.setOnClickListener(v -> { tab = selected; render(); });
        }
        root.addView(nav);
    }
    private int dp(int n) { return Ui.dp(this,n); }
    private void addCard(LinearLayout card) { body.addView(card,Ui.full()); Ui.space(body,16); }
    private void heading(String s) { body.addView(Ui.text(this,s,20,Ui.INK,true)); Ui.space(body,16); }
    private void home() {
        boolean ready = isDefault() && settings.enabled();
        LinearLayout hero = Ui.card(this); hero.setBackground(Ui.shape(0xFF142344, dp(24)));
        TextView badge = Ui.text(this,ready ? "●  自动接听已就绪" : (isDefault() ? "○  自动接听已暂停" : "○  完成设置后启用"),13,0xFFA9E9CD,true); hero.addView(badge);
        Ui.space(hero,18); hero.addView(Ui.text(this,"来电，即刻接通",29,Color.WHITE,true));
        Ui.space(hero,10); hero.addView(Ui.text(this,"全部号码自动接听\n关屏、锁屏时同样工作",15,0xFFC1CCE1,false));
        Ui.space(hero,22); hero.addView(Ui.text(this,"0 秒等待    /    不限号码    /    关屏可用",12,0xFFAEBFDF,false)); addCard(hero);
        if (!isDefault()) {
            LinearLayout setup = Ui.card(this); setup.addView(Ui.text(this,"先完成这一步",18,Ui.INK,true)); Ui.space(setup,8);
            setup.addView(Ui.text(this,"将轻接设为默认电话应用，才能处理来电。",14,Ui.MUTED,false)); Ui.space(setup,16);
            Button button = Ui.button(this,"设为默认电话应用",Ui.BLUE,Color.WHITE); button.setOnClickListener(v -> requestDefault()); setup.addView(button,Ui.full()); addCard(setup);
        }
        LinearLayout enabled = Ui.card(this);
        switchRow(enabled,"自动接听","收到来电立即接通，包含未知号码",settings.enabled(),value -> { settings.set("enabled", value); render(); });
        addCard(enabled);
        heading("接听偏好");
        LinearLayout options = Ui.card(this);
        switchRow(options,"接通后开启免提","关闭时使用系统默认音频设备",settings.speaker(),value -> settings.set("speaker",value));
        Ui.space(options,16);
        Button battery = Ui.button(this,"后台运行设置  ›",0xFFF3F5FB,Ui.INK);
        battery.setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:" + getPackageName())))); options.addView(battery,Ui.full());
        Ui.space(options,10);
        Button power = Ui.button(this,"电池优化设置  ›",0xFFF3F5FB,Ui.INK);
        power.setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))); options.addView(power,Ui.full());
        Ui.space(options,10);
        boolean optimized = !getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        options.addView(Ui.text(this,optimized ? "建议将轻接设为不优化，并允许自启动和后台运行。" : "已关闭电池优化；请同时允许自启动和后台运行。",12,Ui.MUTED,false));
        addCard(options);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Button n = Ui.button(this,"允许来电通知",0xFFE8EDFF,Ui.BLUE);
            n.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11)); body.addView(n,Ui.full()); Ui.space(body,16);
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager.class).canUseFullScreenIntent()) {
            Button f = Ui.button(this,"允许手动接听时显示来电界面",0xFFE8EDFF,Ui.BLUE);
            f.setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()))));
            body.addView(f,Ui.full()); Ui.space(body,16);
        }
        TextView count = Ui.text(this,"已自动接通 " + settings.count() + " 次",14,Ui.MUTED,false); count.setGravity(Gravity.CENTER); body.addView(count);
    }
    private interface Changed { void change(boolean value); }
    private void switchRow(LinearLayout parent, String title, String detail, boolean checked, Changed change) {
        LinearLayout row = Ui.row(this); LinearLayout text = Ui.column(this); text.addView(Ui.text(this,title,17,Ui.INK,true)); Ui.space(text,5); text.addView(Ui.text(this,detail,12,Ui.MUTED,false));
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1)); Switch sw = new Switch(this); sw.setContentDescription(title); sw.setChecked(checked);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2,dp(48)); p.leftMargin=dp(12); row.addView(sw,p);
        sw.setOnCheckedChangeListener((b,value) -> change.change(value)); parent.addView(row);
    }
    private void requestDefault() {
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles.isRoleAvailable(RoleManager.ROLE_DIALER)) startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER),10);
        else Toast.makeText(this,"这台设备不支持默认电话应用",Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request==10 && result==RESULT_OK && Build.VERSION.SDK_INT >=33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request,permissions,results); render();
        if (request==12 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) placeCall();
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); } catch (RuntimeException e) { Toast.makeText(this,"请到系统设置中找到轻接",Toast.LENGTH_LONG).show(); }
    }
    private void dialer() {
        heading("拨号");
        AnswerService service = AnswerService.current;
        if (service!=null && service.primary()!=null) {
            Button active = Ui.button(this,"返回当前通话",0xFFE6F5EF,Ui.GREEN); active.setOnClickListener(v -> startActivity(new Intent(this,CallActivity.class)));
            body.addView(active,Ui.full()); Ui.space(body,12);
        }
        EditText input = new EditText(this); input.setText(dialNumber); input.setTextColor(Ui.INK); input.setTextSize(30); input.setGravity(Gravity.CENTER);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_PHONE); input.setShowSoftInputOnFocus(false); input.setBackgroundColor(Color.TRANSPARENT);
        input.setHint("输入电话号码"); input.setHintTextColor(Ui.MUTED); body.addView(input,new LinearLayout.LayoutParams(-1,dp(90)));
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { dialNumber=s.toString(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        String[] keys = {"1","2","3","4","5","6","7","8","9","*","0","#"};
        for (int row=0;row<4;row++) {
            LinearLayout r = Ui.row(this);
            for (int col=0;col<3;col++) {
                String key=keys[row*3+col]; Button b=Ui.button(this,key,Color.WHITE,Ui.INK); b.setTextSize(26);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(66),1); p.setMargins(dp(5),dp(5),dp(5),dp(5)); r.addView(b,p);
                b.setOnClickListener(v -> { int at=Math.max(0,input.getSelectionStart()); input.getText().insert(at,key); });
                if ("0".equals(key)) b.setOnLongClickListener(v -> { input.getText().insert(Math.max(0,input.getSelectionStart()),"+"); return true; });
            } body.addView(r);
        }
        Ui.space(body,12); LinearLayout actions=Ui.row(this);
        Button call=Ui.button(this,"呼叫",Ui.GREEN,Color.WHITE); actions.addView(call,new LinearLayout.LayoutParams(0,dp(54),1)); call.setOnClickListener(v -> placeCall());
        Button delete=Ui.button(this,"退格",Color.TRANSPARENT,Ui.MUTED); actions.addView(delete); delete.setOnClickListener(v -> {int p=input.getSelectionStart(); if (p>0) input.getText().delete(p-1,p);});
        delete.setOnLongClickListener(v -> {input.setText("");return true;}); body.addView(actions);
        Ui.space(body,16); body.addView(Ui.text(this,"长按 0 输入 + 号",12,Ui.MUTED,false));
    }
    private void placeCall() {
        String number=dialNumber.trim(); if (number.isEmpty()) {Toast.makeText(this,"请先输入号码",Toast.LENGTH_SHORT).show();return;}
        if (!isDefault()) {requestDefault();return;}
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(new String[]{Manifest.permission.CALL_PHONE},12);return;}
        try {getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel",number,null),new Bundle());}
        catch (RuntimeException e) {Toast.makeText(this,"无法拨号，请检查 SIM 卡与电话权限",Toast.LENGTH_LONG).show();}
    }
    private void history() {
        heading("最近通话");
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED) {
            body.addView(Ui.text(this,"允许访问通话记录后，可在这里查看最近的电话。",15,Ui.MUTED,false));Ui.space(body,20);
            Button allow=Ui.button(this,"允许查看通话记录",Ui.BLUE,Color.WHITE); body.addView(allow,Ui.full());
            allow.setOnClickListener(v -> {if (!isDefault()) requestDefault(); else requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG},13);}); return;
        }
        LinearLayout target=body; TextView loading=Ui.text(this,"正在读取…",14,Ui.MUTED,false);target.addView(loading);
        executor.execute(() -> {
            List<String[]> entries=new ArrayList<>();String error=null;
            try (Cursor cursor=getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.TYPE,CallLog.Calls.DURATION},null,null,CallLog.Calls.DATE+" DESC")) {
                SimpleDateFormat format=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);
                if (cursor!=null) while (cursor.moveToNext() && entries.size()<50) {
                    String type=switch(cursor.getInt(2)) {case CallLog.Calls.INCOMING_TYPE -> "呼入";case CallLog.Calls.OUTGOING_TYPE -> "呼出";case CallLog.Calls.MISSED_TYPE -> "未接";case CallLog.Calls.REJECTED_TYPE -> "已拒接";default -> "电话";};
                    String number=cursor.getString(0); entries.add(new String[]{number==null || number.isEmpty()?"未知号码":number,type+" · "+format.format(new Date(cursor.getLong(1)))+" · "+cursor.getLong(3)+" 秒"});
                }
            } catch (RuntimeException e) {error="无法读取通话记录，请检查权限。";}
            final String failure=error;
            runOnUiThread(() -> {
                if (isDestroyed() || body!=target || tab!=2) return;
                target.removeView(loading);
                if (failure!=null || entries.isEmpty()) target.addView(Ui.text(this,failure==null?"还没有通话记录":failure,15,Ui.MUTED,false));
                for (String[] e:entries) {
                    LinearLayout card=Ui.card(this);card.addView(Ui.text(this,e[0],18,Ui.INK,true));Ui.space(card,5);card.addView(Ui.text(this,e[1],13,Ui.MUTED,false));
                    card.setOnClickListener(v -> {dialNumber=e[0];tab=1;render();});target.addView(card,Ui.full());Ui.space(target,10);
                }
            });
        });
    }
    @Override protected void onDestroy() {executor.shutdown();super.onDestroy();}
}
