package cn.lightanswer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;

public final class CallActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AnswerService service = AnswerService.current;
        if (service == null) return;
        Call call = null;
        for (Call c : service.calls()) if (service.id(c) == intent.getIntExtra("callId", -1)) call = c;
        if (call == null) return;
        if ("answer".equals(intent.getAction())) service.answer(call);
        if ("hangup".equals(intent.getAction())) service.hangup(call);
    }
}
