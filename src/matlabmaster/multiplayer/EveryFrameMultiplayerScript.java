package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberViewAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.List;

//Please add setClock(timestamp) campaignClock API


public class EveryFrameMultiplayerScript implements EveryFrameScript {
    private String playerId = MultiplayerModPlugin.GetPlayerId();
    private float timer = 0f;
    private static final float INTERVAL = 5f; // 20tps

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
            timer = 0f;
            MessageSender sender = MultiplayerModPlugin.getMessageSender();
            if (sender != null && sender.isActive()) {
                try {
                    JSONObject message = new JSONObject();
                    message.put("playerId", playerId);
                    message.put("command", 5);
                    message.put("x", Global.getSector().getPlayerFleet().getLocation().x);
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
                } catch (JSONException e) {
                    // Log the error if JSON construction fails
                    Global.getLogger(this.getClass()).log(org.apache.log4j.Level.ERROR,
                            "Failed to construct JSON message: " + e.getMessage());
                }
            }
        }
    }
}