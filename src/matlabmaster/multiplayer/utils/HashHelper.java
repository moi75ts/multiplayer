package matlabmaster.multiplayer.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashHelper {
    public static String hashJsonObject(JSONObject object) throws NoSuchAlgorithmException {
        String jsonString = object.toString();
        MessageDigest digest = MessageDigest.getInstance("md5");
        byte[] hashBytes = digest.digest(jsonString.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }

    public static String hashJsonArray(JSONArray object) throws NoSuchAlgorithmException {
        String jsonString = object.toString();
        MessageDigest digest = MessageDigest.getInstance("md5");
        byte[] hashBytes = digest.digest(jsonString.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
