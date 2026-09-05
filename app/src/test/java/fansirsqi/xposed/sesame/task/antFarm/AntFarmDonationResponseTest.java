package fansirsqi.xposed.sesame.task.antFarm;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AntFarmDonationResponseTest {
    @Test
    public void verificationStopsDonationButBusinessFailureDoesNot() throws Exception {
        assertTrue(AntFarmDonationResponse.requiresVerification(new JSONObject("{\"success\":false,\"resultCode\":\"RPC_VERIFICATION_REQUIRED\"}")));
        assertTrue(AntFarmDonationResponse.requiresVerification(new JSONObject("{\"error\":1009}")));
        assertFalse(AntFarmDonationResponse.requiresVerification(new JSONObject("{\"success\":false,\"resultCode\":\"218\"}")));
    }

    @Test
    public void soldByProjectNeverGuessesTargetFromOrderOrDonationHistory() throws Exception {
        // 抓包第 1、3、10 条的结构：同一项目含多个未完成标的，历史捐赠不是本次选择。
        JSONObject activity = new JSONObject("{\"projectType\":\"SOLDBY\",\"projectId\":\"project\",\"activityId\":\"activity\",\"batchInfo\":[{\"batchId\":\"batch\",\"targetList\":[{\"targetId\":\"target1\",\"finished\":false,\"hasDonated\":true},{\"targetId\":\"target2\",\"finished\":false}]}]}");
        assertFalse(AntFarmDonationResponse.supportsAutomaticDonation(activity));
        assertTrue(AntFarmDonationResponse.supportsAutomaticDonation(new JSONObject("{\"projectType\":\"HELP\",\"activityId\":\"normal\"}")));
        assertTrue(AntFarmDonationResponse.supportsAutomaticDonation(new JSONObject("{\"activityId\":\"legacy\"}")));
    }

    @Test
    public void missingAndNullMessagesHaveStableFallback() throws Exception {
        assertEquals("218", AntFarmDonationResponse.failureMessage(new JSONObject("{\"memo\":null,\"resultDesc\":\"\",\"resultCode\":\"218\"}")));
        assertEquals("庄园接口返回失败，未提供原因", AntFarmDonationResponse.failureMessage(new JSONObject()));
    }

    @Test
    public void verificationWithoutMemoUsesDescription() throws Exception {
        JSONObject response = new JSONObject("{\"success\":false,\"resultCode\":\"RPC_VERIFICATION_REQUIRED\",\"resultDesc\":\"触发安全验证，请人工验证后继续\"}");
        assertEquals("触发安全验证，请人工验证后继续", AntFarmDonationResponse.failureMessage(response));
    }

    @Test
    public void emptyResponseWrapperUsesDescription() throws Exception {
        JSONObject response = new JSONObject("{\"success\":false,\"resultCode\":\"EMPTY_RPC_RESPONSE\",\"resultDesc\":\"RPC返回为空\"}");
        assertEquals("RPC返回为空", AntFarmDonationResponse.failureMessage(response));
    }

    @Test
    public void existingMemoIsPreserved() throws Exception {
        assertEquals("SUCCESS", AntFarmDonationResponse.failureMessage(new JSONObject("{\"success\":true,\"memo\":\"SUCCESS\"}")));
    }
}
