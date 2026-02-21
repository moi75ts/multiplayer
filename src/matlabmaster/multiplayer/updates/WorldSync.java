package matlabmaster.multiplayer.updates;

import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.combat.entities.terrain.Asteroid;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.client.Client;
import matlabmaster.multiplayer.utils.WorldSerializer;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

public class WorldSync {
    public static void requestServerTime(Client client) throws JSONException {
        JSONObject packet = new JSONObject();
        packet.put("commandId","serverTimeRequest");
        packet.put("from",client.clientId);
        client.send(String.valueOf(packet));
    }

    public void sendOrbitSnapshotForLocation(LocationAPI location, Client client, String from) throws JSONException {
        List<SectorEntityToken> allEntities = location.getAllEntities();
        JSONObject packet = new JSONObject();
        JSONObject orbits = new JSONObject();
        for(SectorEntityToken entity : allEntities){
            if(entity.getOrbit() != null && !(entity instanceof AsteroidAPI) && !Objects.equals(entity.getCustomEntityType(), "orbital_junk")){
                if(Objects.equals(entity.getName(), "Habitat")){
                    System.out.println(entity.getCustomEntityType());
                }
                if(!(entity instanceof CampaignFleetAPI)){
                    try {
                        JSONObject serializedEntity = WorldSerializer.serializeOrbit(entity);
                        orbits.put(entity.getId(),serializedEntity);
                    }catch (Exception e){
                        MultiplayerLog.log().warn("Failed to add entity " + entity.getId() + " to orbit snapshot");
                    }
                }
            }
        }
        packet.put("commandId","handleOrbitSnapshotForLocation");
        packet.put("to",from);
        packet.put("orbits",orbits);
        client.send(String.valueOf(packet));
    }
    public static void requestOrbitSnapshotForLocation(LocationAPI location,Client client) throws JSONException {
        JSONObject packet = new JSONObject();
        packet.put("commandId","requestOrbitSnapshotForLocation");
        packet.put("location",location.getId());
        packet.put("from",client.clientId);
        client.send(packet.toString());
    }
}
