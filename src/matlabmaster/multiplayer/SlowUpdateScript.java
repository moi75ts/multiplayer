package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONException;
import java.util.Objects;



public class SlowUpdateScript implements EveryFrameScript {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private static final float INTERVAL = 5.0f; // Every 5 seconds
    private float timer = 0f;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        timer += amount;
        if (timer >= INTERVAL) {
            timer -= INTERVAL; // Reset with remainder
            orbitUpdateRequest();
        }
    }

    public void orbitUpdateRequest(){
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