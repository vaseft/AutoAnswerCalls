package cn.lightanswer.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Test APK only: configure preferences without launching a screen during screen-off tests. */
public final class SmokeTestRunner extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) {super.onCreate(arguments);args=arguments;start();}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            SettingsStore settings=new SettingsStore(getTargetContext());
            if(args.containsKey("enabled")) settings.prefs.edit().putBoolean("enabled",Boolean.parseBoolean(args.getString("enabled"))).commit();
            if(args.containsKey("speaker")) settings.prefs.edit().putBoolean("speaker",Boolean.parseBoolean(args.getString("speaker"))).commit();
            result.putString("enabled",Boolean.toString(settings.enabled()));
            result.putString("speaker",Boolean.toString(settings.speaker()));
            result.putString("count",Integer.toString(settings.count()));
            finish(Activity.RESULT_OK,result);
        } catch (Throwable e) {result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
