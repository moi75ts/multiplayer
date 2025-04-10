package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SectorCleanup {
    public static void cleanupSector(JSONObject data) throws JSONException {
        //SectorAPI sector = Global.getSector();
        //List<StarSystemAPI> systemList = Global.getSector().getStarSystems();
//
        ////create clientSideSystemIdList
        //List<String> clientSideSystemIdList = new ArrayList<>(List.of());
        //for (StarSystemAPI system : systemList) {
        //    clientSideSystemIdList.add(system.getId());
        //}
        //JSONArray serverSideSystemList = data.getJSONArray("systems");
//
        ////create serverSideSystemIdList
        //List<Object> serverSideSystemIdList = new ArrayList<>(List.of());
        //int i;
        //for (i = 0; i <= serverSideSystemList.length() - 1; i++) {
        //    JSONObject system = serverSideSystemList.getJSONObject(i);
        //    serverSideSystemIdList.add(system.getString("systemID"));
        //}
//
        ////remove non server side systems
        //for (String clientId : clientSideSystemIdList) {
        //    if (!serverSideSystemIdList.contains(clientId)) {
        //        RemoveStarSystem.removeStarSystem(clientId);
        //    }
        //}
//
        ////remove systems that exist in both client and server but are not in the same position (id overlap)
        //for (StarSystemAPI remainingSystem : systemList) {
        //    double xcoord = remainingSystem.getLocation().x;
        //    double ycoord = remainingSystem.getLocation().y;
        //    for (i = 0; i <= serverSideSystemList.length() - 1; i++) {
        //        String serverSystemID = serverSideSystemList.getJSONObject(i).getString("systemID");
        //        String clientSystemId = remainingSystem.getId();
        //        if (Objects.equals(serverSystemID, clientSystemId)) {
        //            double serverSidexcoord = serverSideSystemList.getJSONObject(i).getDouble("locationx");
        //            double serverSideycoord = serverSideSystemList.getJSONObject(i).getDouble("locationy");
        //            if (xcoord == serverSidexcoord && ycoord == serverSideycoord) {
        //                continue;
        //            } else {
        //                RemoveStarSystem.removeStarSystem(remainingSystem.getId());
        //            }
        //        }
//
        //    }
        //}
        //the cleanup process is done that way to be mod friendly, if a mod adds a static system (like spindle for example)
        //as long as both client and server have the same installed mods spindle will not be deleted nor have to be recreated
    }
}
