package cn.lightanswer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.PowerManager;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.CallEndpointException;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class AnswerService extends InCallService {
    static AnswerService current;
    static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();
    private final Map<Call, Call.Callback> tracked = new LinkedHashMap<>();
    private final Map<Call, Integer> ids = new LinkedHashMap<>();
    private final Set<Call> autoRequested = new HashSet<>();
    private final Set<Call> answerAttempted = new HashSet<>();
    private final Set<Call> speakerAfterAnswer = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SettingsStore settings;
    private int nextId = 200;
    private List<CallEndpoint> endpoints = new ArrayList<>();
    private CallEndpoint endpoint;
    private boolean muted;
    private boolean legacySpeaker;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = (p, key) -> {
        if ("enabled".equals(key)) for (Call c : new ArrayList<>(tracked.keySet())) autoAnswer(c);
        signal();
    };

    @Override public void onCreate() {
        super.onCreate(); current = this; settings = new SettingsStore(this);
        settings.prefs.registerOnSharedPreferenceChangeListener(preferences);
        NotificationChannel channel = new NotificationChannel("calls", "来电与通话", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public void onCallAdded(Call call) {
        super.onCallAdded(call);
        ids.put(call, nextId++);
        Call.Callback callback = new Call.Callback() {
            @Override public void onStateChanged(Call c, int state) { changed(c); }
            @Override public void onDetailsChanged(Call c, Call.Details details) { changed(c); }
            @Override public void onChildrenChanged(Call c, List<Call> children) { changed(c); }
            @Override public void onConferenceableCallsChanged(Call c, List<Call> calls) { signal(); }
        };
        tracked.put(call, callback); call.registerCallback(callback, handler);
        // Ask Telecom to answer before building any UI. No delayed task or screen check.
        autoAnswer(call);
        changed(call);
        if (!ringing(call) && state(call) != Call.STATE_DISCONNECTED
                && !autoRequested.contains(call) && getSystemService(PowerManager.class).isInteractive()) showCall(call);
    }

    private void autoAnswer(Call call) {
        TelecomManager telecom = getSystemService(TelecomManager.class);
        if (AnswerPolicy.shouldAnswer(settings.enabled(), getPackageName().equals(telecom.getDefaultDialerPackage()), state(call))
                && !answerAttempted.contains(call)) {
            answerAttempted.add(call); autoRequested.add(call);
            if (settings.speaker()) speakerAfterAnswer.add(call);
            try {
                Log.i("LightAnswer", "AUTO_ANSWER_REQUEST id=" + id(call));
                call.answer(VideoProfile.STATE_AUDIO_ONLY);
            } catch (RuntimeException e) {
                autoRequested.remove(call); speakerAfterAnswer.remove(call);
                Log.e("LightAnswer", "Automatic answer failed", e);
                Toast.makeText(this, "系统未接通来电，请手动接听", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void changed(Call call) {
        int state = state(call);
        if (ringing(call)) autoAnswer(call);
        if (state == Call.STATE_ACTIVE) {
            if (autoRequested.remove(call)) {
                settings.increment();
                Log.i("LightAnswer", "AUTO_ANSWER_CONNECTED id=" + id(call));
            }
            if (speakerAfterAnswer.contains(call) && (Build.VERSION.SDK_INT < 34 || !endpoints.isEmpty())) {
                routeSpeaker(true); speakerAfterAnswer.remove(call);
            }
        }
        if (state == Call.STATE_DISCONNECTED) {
            autoRequested.remove(call); speakerAfterAnswer.remove(call);
        }
        refreshNotification();
        signal();
    }

    @Override public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Call.Callback callback = tracked.remove(call);
        if (callback != null) call.unregisterCallback(callback);
        ids.remove(call); autoRequested.remove(call); answerAttempted.remove(call); speakerAfterAnswer.remove(call);
        refreshNotification();
        signal();
    }

    static int state(Call call) { return Build.VERSION.SDK_INT >= 31 ? call.getDetails().getState() : call.getState(); }
    static boolean ringing(Call call) { return state(call) == Call.STATE_RINGING || state(call) == Call.STATE_SIMULATED_RINGING; }
    int id(Call call) { Integer i = ids.get(call); return i == null ? -1 : i; }
    Call find(int id) { for (Call c : tracked.keySet()) if (id(c) == id && state(c) != Call.STATE_DISCONNECTED) return c; return primary(); }
    List<Call> calls() {
        List<Call> result = new ArrayList<>();
        for (Call c : tracked.keySet()) if (state(c) != Call.STATE_DISCONNECTED) result.add(c);
        return result;
    }
    Call primary() {
        List<Call> calls = calls();
        for (Call c : calls) if (ringing(c)) return c;
        for (Call c : calls) if (state(c) == Call.STATE_ACTIVE) return c;
        return calls.isEmpty() ? null : calls.get(0);
    }
    static String number(Call call) {
        Call.Details d = call.getDetails();
        if (d.getHandlePresentation() != TelecomManager.PRESENTATION_ALLOWED || d.getHandle() == null) return "未知号码";
        String value = d.getHandle().getSchemeSpecificPart();
        return value == null || value.isEmpty() ? "未知号码" : value;
    }
    static String stateLabel(Call call) {
        return switch (state(call)) {
            case Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> "来电";
            case Call.STATE_ACTIVE -> "通话中";
            case Call.STATE_HOLDING -> "已保持";
            case Call.STATE_DIALING, Call.STATE_CONNECTING -> "正在呼叫";
            case Call.STATE_SELECT_PHONE_ACCOUNT -> "请选择 SIM 卡";
            case Call.STATE_DISCONNECTING -> "正在挂断";
            case Call.STATE_DISCONNECTED -> "通话结束";
            default -> "正在连接";
        };
    }
    void answer(Call call) {
        if (call != null && ringing(call)) {
            autoRequested.remove(call); speakerAfterAnswer.remove(call);
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
        }
    }
    void hangup(Call call) {
        if (call == null) return;
        if (ringing(call)) call.reject(false, null); else call.disconnect();
    }
    boolean isMuted() { return muted; }
    boolean isSpeaker() { return Build.VERSION.SDK_INT >= 34 ? endpoint != null && endpoint.getEndpointType() == CallEndpoint.TYPE_SPEAKER : legacySpeaker; }
    List<CallEndpoint> endpoints() { return new ArrayList<>(endpoints); }
    void route(CallEndpoint e) {
        if (Build.VERSION.SDK_INT >= 34) requestCallEndpointChange(e, getMainExecutor(), new OutcomeReceiver<Void, CallEndpointException>() {
            @Override public void onResult(Void result) { signal(); }
            @Override public void onError(CallEndpointException error) {
                Log.w("LightAnswer", "Audio route unavailable", error);
                Toast.makeText(AnswerService.this, "音频切换失败，请重试", Toast.LENGTH_SHORT).show();
            }
        });
    }
    void routeSpeaker(boolean speaker) {
        if (Build.VERSION.SDK_INT >= 34) {
            for (CallEndpoint e : endpoints) {
                if (speaker && e.getEndpointType() == CallEndpoint.TYPE_SPEAKER) { route(e); return; }
                if (!speaker && (e.getEndpointType() == CallEndpoint.TYPE_EARPIECE || e.getEndpointType() == CallEndpoint.TYPE_WIRED_HEADSET)) { route(e); return; }
            }
        } else setAudioRoute(speaker ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_WIRED_OR_EARPIECE);
    }
    @Override public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState); muted = audioState.isMuted();
        legacySpeaker = audioState.getRoute() == CallAudioState.ROUTE_SPEAKER; signal();
    }
    @Override public void onMuteStateChanged(boolean value) { muted = value; signal(); }
    @Override public void onAvailableCallEndpointsChanged(List<CallEndpoint> value) {
        endpoints = new ArrayList<>(value);
        for (Call c : new ArrayList<>(speakerAfterAnswer)) if (state(c) == Call.STATE_ACTIVE && !endpoints.isEmpty()) {
            routeSpeaker(true); speakerAfterAnswer.remove(c);
        }
        signal();
    }
    @Override public void onCallEndpointChanged(CallEndpoint value) { endpoint = value; signal(); }
    @Override public void onBringToForeground(boolean showDialpad) { Call c = primary(); if (c != null) showCall(c); }
    private void showCall(Call call) {
        try { startActivity(new Intent(this, CallActivity.class).putExtra("callId", id(call)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException e) { Log.w("LightAnswer", "Open call notification to manage call", e); }
    }
    static void signal() { for (Runnable r : listeners) r.run(); }

    private PendingIntent action(Call call, String action) {
        Intent i = new Intent(this, CallActionReceiver.class).setAction(action).putExtra("callId", id(call));
        return PendingIntent.getBroadcast(this, id(call), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private void refreshNotification() {
        Call call = primary();
        if (call == null) { stopForeground(STOP_FOREGROUND_REMOVE); return; }
        boolean ringing = ringing(call);
        Intent screen = new Intent(this, CallActivity.class).putExtra("callId", id(call));
        PendingIntent content = PendingIntent.getActivity(this, id(call), screen, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, "calls").setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(number(call)).setContentText(stateLabel(call)).setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).setColor(Ui.BLUE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (Build.VERSION.SDK_INT >= 31) {
            Person person = new Person.Builder().setName(number(call)).setImportant(true).build();
            b.setStyle(ringing ? Notification.CallStyle.forIncomingCall(person, action(call,"hangup"), action(call,"answer"))
                    : Notification.CallStyle.forOngoingCall(person, action(call,"hangup")));
        } else {
            if (ringing) b.addAction(new Notification.Action.Builder(null, "接听", action(call,"answer")).build());
            b.addAction(new Notification.Action.Builder(null, ringing ? "拒接" : "挂断", action(call,"hangup")).build());
        }
        if (ringing && !autoRequested.contains(call)) b.setFullScreenIntent(content, true);
        // One notification follows the selected incoming/active call and satisfies CallStyle's FGS requirement.
        try { startForeground(100, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL); }
        catch (RuntimeException e) {
            Log.e("LightAnswer", "Call foreground notification unavailable", e);
            // Notification failure must never terminate the phone service or interrupt answering.
            b.setStyle(null);
            try { getSystemService(NotificationManager.class).notify(100, b.build()); }
            catch (RuntimeException notificationError) { Log.e("LightAnswer", "Call notification unavailable", notificationError); }
        }
    }
    @Override public void onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(preferences);
        for (Map.Entry<Call, Call.Callback> entry : tracked.entrySet()) {
            entry.getKey().unregisterCallback(entry.getValue());
        }
        stopForeground(STOP_FOREGROUND_REMOVE); getSystemService(NotificationManager.class).cancel(100);
        tracked.clear(); ids.clear(); current = null; signal(); super.onDestroy();
    }
}
