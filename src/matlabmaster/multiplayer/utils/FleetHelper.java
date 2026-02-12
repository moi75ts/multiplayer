package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class FleetHelper {
    public static void killAllFleetsExceptPlayer() {
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            // Create a copy of the fleet list to avoid ConcurrentModificationException
            List<CampaignFleetAPI> fleetsCopy = new ArrayList<>(location.getFleets()); //needs a copy to avoid comodification exceptions
            for (CampaignFleetAPI fleet : fleetsCopy) {
                if (!fleet.isPlayerFleet() && !fleet.isStationMode()) {
                    fleet.despawn();
                    System.out.println("despawned" + fleet.getName());
                }
            }
        }
    }
    public static JSONArray getFleetsSnapshot() throws JSONException {
        JSONArray fleets = new JSONArray();
        for(LocationAPI location : Global.getSector().getAllLocations()){
            for (CampaignFleetAPI fleet : location.getFleets()){
                if(!fleet.isStationMode()){ //sometimes stations are considered fleets
                    fleets.put(FleetSerializer.serializeFleet(fleet));
                }
            }
        }
        return fleets;
    }
    public static void removeFleetById(String id){
        Global.getSector().getEntityById(id).getContainingLocation().removeEntity(Global.getSector().getEntityById(id));
    }

}
