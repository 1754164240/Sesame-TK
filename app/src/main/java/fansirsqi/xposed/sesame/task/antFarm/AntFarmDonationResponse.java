package fansirsqi.xposed.sesame.task.antFarm;

import org.json.JSONObject;

final class AntFarmDonationResponse {
    static String failureMessage(JSONObject response) {
        for (String key : new String[]{"memo", "resultDesc", "resultCode"}) {
            String value = response.optString(key, "");
            if (!value.trim().isEmpty()) return value;
        }
        return "庄园接口返回失败，未提供原因";
    }

    static boolean requiresVerification(JSONObject response) {
        String code = response.optString("resultCode");
        return "RPC_VERIFICATION_REQUIRED".equals(code) || "1009".equals(code)
                || "1009".equals(response.optString("error"));
    }

    static boolean supportsAutomaticDonation(JSONObject activity) {
        return !"SOLDBY".equals(activity.optString("projectType"));
    }
}
