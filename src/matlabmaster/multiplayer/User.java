package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;

import java.io.IOException;

public class User {
    private static String userId; // Changed to static

    // Constructor (optional, not strictly needed for static context)
    public User(){
        if (userId == null) {
            try {
                userId = Global.getSettings().readTextFileFromCommon("multiplayer");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String getUserIdFromFile(){
        try {
            userId = Global.getSettings().readTextFileFromCommon("multiplayer");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return userId;
    }

    public static String getUserId(){
        if (userId == null) {
            try {
                userId = Global.getSettings().readTextFileFromCommon("multiplayer");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return userId;
    }

    public static void setUserId(String id){
        try {
            Global.getSettings().writeTextFileToCommon("multiplayer", id);
            userId = Global.getSettings().readTextFileFromCommon("multiplayer");
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static void setUserIdWithoutUpdatingTheFile(String id){
        userId = id;
    }
}