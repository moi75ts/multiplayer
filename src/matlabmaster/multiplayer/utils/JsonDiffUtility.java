package matlabmaster.multiplayer.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Iterator;

public class JsonDiffUtility {

    public static JSONObject getDifferences(JSONObject oldState, JSONObject newState) {
        JSONObject diffs = new JSONObject();
        compareObjects(oldState, newState, diffs);
        return diffs;
    }

    private static void compareObjects(JSONObject obj1, JSONObject obj2, JSONObject diffs) {
        if (obj1 == null || obj2 == null) return;

        // 1. Check removals and updates
        Iterator<?> keys = obj1.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object v1 = obj1.opt(key);

            if (!obj2.has(key)) {
                safePut(diffs, key, createInstruction("REMOVED", null, v1));
            } else {
                Object v2 = obj2.opt(key);
                handleValueComparison(v1, v2, key, diffs);
            }
        }

        // 2. Check for additions
        Iterator<?> keys2 = obj2.keys();
        while (keys2.hasNext()) {
            String key = (String) keys2.next();
            if (!obj1.has(key)) {
                safePut(diffs, key, createInstruction("ADDED", obj2.opt(key), null));
            }
        }
    }

    private static void handleValueComparison(Object v1, Object v2, String key, JSONObject diffs) {
        v1 = (v1 == JSONObject.NULL) ? null : v1;
        v2 = (v2 == JSONObject.NULL) ? null : v2;

        if (v1 == null && v2 == null) return;

        if (v1 instanceof JSONObject o1 && v2 instanceof JSONObject o2) {
            JSONObject subDiff = new JSONObject();
            compareObjects(o1, o2, subDiff);
            if (subDiff.length() > 0) {
                safePut(diffs, key, subDiff);
            }
        } else if (v1 instanceof JSONArray a1 && v2 instanceof JSONArray a2) {
            JSONObject arrayDiffs = new JSONObject();
            compareArrays(a1, a2, arrayDiffs);
            if (arrayDiffs.length() > 0) {
                safePut(diffs, key, arrayDiffs);
            }
        } else if (v1 == null || !v1.equals(v2)) {
            safePut(diffs, key, createInstruction("UPDATE", v2, v1));
        }
    }

    private static void compareArrays(JSONArray arr1, JSONArray arr2, JSONObject arrayDiffs) {
        int max = Math.max(arr1.length(), arr2.length());
        for (int i = 0; i < max; i++) {
            Object v1 = arr1.opt(i);
            Object v2 = arr2.opt(i);
            String indexKey = "[" + i + "]";

            // Use opt to safely check existence vs null
            if (v1 != null && (v2 == null && i >= arr2.length())) {
                safePut(arrayDiffs, indexKey, createInstruction("REMOVED", null, v1));
            } else {
                handleValueComparison(v1, v2, indexKey, arrayDiffs);
            }
        }
    }

    private static JSONObject createInstruction(String action, Object newValue, Object oldValue) {
        JSONObject instruction = new JSONObject();
        // Since we are creating a fresh object here, safePut ensures no crashes
        // if keys are null or values are unsupported.
        safePut(instruction, "action", action);
        safePut(instruction, "value", newValue == null ? JSONObject.NULL : newValue);
        safePut(instruction, "oldValue", oldValue == null ? JSONObject.NULL : oldValue);
        return instruction;
    }

    /**
     * Internal helper to handle the checked JSONException from .put()
     * This is vital for restrictive sandboxes where you can't allow crashes.
     */
    private static void safePut(JSONObject obj, String key, Object value) {
        try {
            if (key != null) {
                obj.put(key, value);
            }
        } catch (JSONException e) {
            // In a sandbox, we usually log to stderr or a custom logger.
            // We ignore it here to keep the sync flowing.
            System.err.println("JSON Sync Error: " + e.getMessage());
        }
    }
}