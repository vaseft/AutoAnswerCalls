package cn.lightanswer.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerPolicyTest {
    @Test public void ringingAnswersImmediatelyWhenEnabledAndDefault() {
        assertTrue(AnswerPolicy.shouldAnswer(true,true,2));
        assertTrue(AnswerPolicy.shouldAnswer(true,true,13));
    }
    @Test public void disabledModeDoesNotAnswer() {
        assertFalse(AnswerPolicy.shouldAnswer(false,true,2));
    }
    @Test public void losingDefaultRolePreventsAnswering() {
        assertFalse(AnswerPolicy.shouldAnswer(true,false,2));
    }
    @Test public void outgoingActiveHeldAndDisconnectedStatesDoNotAnswer() {
        for (int state=-1;state<=13;state++) {
            if(state!=2 && state!=13) assertFalse("state="+state,AnswerPolicy.shouldAnswer(true,true,state));
        }
    }
}
