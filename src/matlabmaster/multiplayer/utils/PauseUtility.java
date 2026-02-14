package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.client.Client;
import matlabmaster.multiplayer.updates.FleetSync;
import org.json.JSONException;
import org.json.JSONObject;

public class PauseUtility {
    public static void clientPauseUtility(Client client,FleetSync fleetSync){
        try {
            if(Global.getSector().isPaused()){
                if(!Global.getSector().getCampaignUI().isShowingDialog()){
                    Global.getSector().setPaused(false);
                }else if(!client.wasPaused){
                    client.wasPaused = true;
                    JSONObject packet = new JSONObject();
                    packet.put("commandId","paused");
                    String fleetName = Global.getSector().getPlayerFleet().getName();
                    fleetName += " [PAUSED]";//9 char long
                    Global.getSector().getPlayerFleet().setName(fleetName);
                    fleetSync.sendOwnFleetUpdate(client);//send the updated name
                    client.send(String.valueOf(packet));
                }
            }else{
                if(client.wasPaused){
                    client.wasPaused = false;
                    JSONObject packet = new JSONObject();
                    packet.put("commandId","unpaused");
                    String fleetName = Global.getSector().getPlayerFleet().getName();
                    // Check if it ends with " [paused]" and remove it if present
                    if (fleetName.endsWith(" [PAUSED]")) {
                        fleetName = fleetName.substring(0, fleetName.length() - " [paused]".length());
                        Global.getSector().getPlayerFleet().setName(fleetName);
                    }
                    client.send(packet.toString());
                }
            }
        }catch (JSONException e){
            MultiplayerLog.log().error("Error in the pause utility", e);
        }
    }
}
