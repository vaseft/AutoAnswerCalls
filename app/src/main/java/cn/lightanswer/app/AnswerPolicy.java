package cn.lightanswer.app;

/** No timer, number filter or screen-state gate. Only ringing calls may be answered. */
public final class AnswerPolicy {
    private AnswerPolicy() {}
    public static boolean shouldAnswer(boolean enabled, boolean defaultDialer, int state) {
        return enabled && defaultDialer && (state == 2 || state == 13); // RINGING or SIMULATED_RINGING
    }
}
