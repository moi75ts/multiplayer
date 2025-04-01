package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;



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
            StarSystemAPI currentLocation = Global.getSector().getPlayerFleet().getStarSystem();
            if(Objects.equals(MultiplayerModPlugin.getMode(), "client")){
                if(currentLocation != null){
                    try {
                        Client.requestOrbitingBodiesUpdate(currentLocation.getBaseName());
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            processMessages();
        }
    }


    private void processMessages() {
        ConcurrentLinkedQueue<String> queue = MessageHandler.getMessageQueue();
        String message;
        while ((message = queue.poll()) != null) {
            try {
                JSONObject data = new JSONObject(message);
                int command = data.getInt("command");
                if (command != 5) { // Non-location data (slow updates)
                    handleSlowUpdate(data);
                }
                // Command 5 skipped here, handled in FastUpdateScript
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Error processing slow update message '" + message + "': " + e.getMessage());
            }
        }
    }

    private void handleSlowUpdate(JSONObject data) {
        try {
            int command = data.getInt("command");
            // Example: Handle hypothetical slow-changing data (e.g., stationary fleet status)
            if(command == 6){
                if(Objects.equals(MultiplayerModPlugin.getMode(), "server")){
                     Server.sendOrbitingBodiesUpdate(data.getString("system"));
                }else{
                    Client.handleOrbitingBodiesUpdate(data);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling slow update: " + e.getMessage());
        }
    }
}