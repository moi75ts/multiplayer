package matlabmaster.multiplayer.requests;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import matlabmaster.multiplayer.Client;
import matlabmaster.multiplayer.MultiplayerModPlugin;
import org.json.JSONException;

import java.util.Objects;

public class StarSystemSync {
    public static void orbitUpdateRequest(){
        StarSystemAPI currentLocation = Global.getSector().getPlayerFleet().getStarSystem();
        if (Objects.equals(MultiplayerModPlugin.getMode(), "client")) {
            if (currentLocation != null) {
                try {
                    Client.requestOrbitingBodiesUpdate(currentLocation.getBaseName());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
