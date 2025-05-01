package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import matlabmaster.multiplayer.fastUpdates.CargoPodsSync;
import matlabmaster.multiplayer.utils.FleetHelper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONObject;
import org.json.JSONException;

import java.security.NoSuchAlgorithmException;

public class FastUpdateScript implements EveryFrameScript {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private static final float INTERVAL = 0.05f; // ~20 TPS (0.05 seconds)
    private float timer = 0f;
    private final String playerId = User.getUserId();

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
        if(MultiplayerModPlugin.getMessageSender() != null){
            timer += amount;
            if (timer >= INTERVAL) {
                timer -= INTERVAL; // Reset with remainder to avoid drift
                sendPositionUpdate();
                try {
                    CargoPodsSync.cargoPodsCheck();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void sendPositionUpdate() {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
                message.put("playerId", playerId);
                message.put("command", 5);
                message.put("fleet",FleetHelper.serializeFleet(fleet));
                sender.sendMessage(message.toString());
                LOGGER.log(Level.DEBUG, "Sent position update for player " + playerId);
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e); // shouldn't happen (hash failed)
            }
        }
    }
}