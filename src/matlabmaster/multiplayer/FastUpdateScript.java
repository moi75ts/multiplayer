package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.FleetMemberViewAPI;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FastUpdateScript implements EveryFrameScript {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private static final float INTERVAL = 0.05f; // ~20 TPS (0.05 seconds)
    private float timer = 0f;
    private final String playerId = MultiplayerModPlugin.GetPlayerId();

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
            timer -= INTERVAL; // Reset with remainder to avoid drift
            sendPositionUpdate();
        }
    }

    private void sendPositionUpdate() {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("playerId", playerId);
                message.put("command", 5);
                message.put("x", Global.getSector().getPlayerFleet().getLocation().x );
                message.put("y", Global.getSector().getPlayerFleet().getLocation().y);
                message.put("starSystem", Global.getSector().getCurrentLocation().getName());
                message.put("transponder", Global.getSector().getPlayerFleet().isTransponderOn());

                JSONArray ships = new JSONArray();
                List<FleetMemberViewAPI> views = Global.getSector().getPlayerFleet().getViews();
                for (FleetMemberViewAPI view : views) {
                    JSONArray ship = new JSONArray();
                    ship.put(view.getMember().getShipName());
                    ship.put(view.getMember().getVariant().getHullVariantId());
                    ships.put(ship);
                }
                message.put("ships", ships);

                sender.sendMessage(message.toString());
                LOGGER.log(Level.DEBUG, "Sent position update for player " + playerId);
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }
}