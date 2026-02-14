package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.client.Client;
import org.json.JSONException;
import org.json.JSONObject;

public class PauseUtility {
    public static void clientPauseUtility(Client client){
        try {
            if(Global.getSector().isPaused()){
                if(!Global.getSector().getCampaignUI().isShowingDialog()){
                    Global.getSector().setPaused(false);
                }else if(!client.wasPaused){
                    client.wasPaused = true;
                    JSONObject packet = new JSONObject();
                    packet.put("commandId","paused");
                    client.send(String.valueOf(packet));
                }
            }else{
                if(client.wasPaused){
                    client.wasPaused = false;
                    JSONObject packet = new JSONObject();
                    packet.put("commandId","unpaused");
                    client.send(packet.toString());
                }
            }
        }catch (JSONException e){
            MultiplayerLog.log().error("Error in the pause utility", e);
        }
    }
}
