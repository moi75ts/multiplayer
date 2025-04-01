package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;

public class MessageHandler implements MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private final String playerId;

    public MessageHandler(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public void onMessageReceived(String message) {
        LOGGER.log(org.apache.log4j.Level.INFO, "Received message: " + message);
        try {
            // Parse JSON message
            JSONObject data = new JSONObject(message);
            String senderPlayerId = data.getString("playerId");
            int command = data.getInt("command");

            if (command == 5 && !Objects.equals(senderPlayerId, playerId)) { // Ignore own messages
                float x = (float) data.getDouble("x");
                float y = (float) data.getDouble("y");
                String starSystem = data.getString("starSystem");
                boolean transponder = data.getBoolean("transponder");
                JSONArray ships = data.getJSONArray("ships");

                // Get current player's star system
                SectorAPI sector = Global.getSector();
                LocationAPI currentLocation = sector.getCurrentLocation();
                String currentSystemName = currentLocation != null ? currentLocation.getName() : "";

                // Check if in the same star system
                if (Objects.equals(starSystem, currentSystemName)) {
                    // Check if fleet already exists
                    CampaignFleetAPI fleet = (CampaignFleetAPI) sector.getEntityById(senderPlayerId);
                    if (fleet == null) {
                        // Create a new empty fleet
                        fleet = Global.getFactory().createEmptyFleet("neutral", "Fleet of " + senderPlayerId, true);
                        fleet.setId(senderPlayerId);

                        // Add ships from the JSON array
                        for (int i = 0; i < ships.length(); i++) {
                            JSONArray ship = ships.getJSONArray(i);
                            String shipName = ship.getString(0).isEmpty() ? "Unnamed Ship" : ship.getString(0);
                            String variantId = ship.getString(1);

                            // Create fleet member with variant ID
                            FleetMemberAPI member = Global.getFactory().createFleetMember(
                                    FleetMemberType.SHIP, variantId);
                            member.setShipName(shipName);
                            fleet.getFleetData().addFleetMember(member);
                            LOGGER.log(org.apache.log4j.Level.INFO,
                                    "Added " + shipName + " (" + variantId + ") to fleet");
                        }

                        // Make fleet stationary
                        fleet.setAI(null);
                        // Add to current location
                        currentLocation.addEntity(fleet);
                    }
                    // Update fleet location and transponder
                    fleet.setLocation(x, y);
                    fleet.setTransponderOn(transponder);

                    LOGGER.log(org.apache.log4j.Level.INFO,
                            "Spawned/Updated stationary fleet for player " + senderPlayerId + " at [" + x + ", " + y + "] in " + starSystem);
                } else {
                    CampaignFleetAPI fleet = (CampaignFleetAPI) sector.getEntityById(senderPlayerId);
                    if (fleet != null){
                        fleet.despawn(); //if the other player fleet went out of the system
                    }
                    LOGGER.log(org.apache.log4j.Level.INFO,
                            "Fleet not spawned for " + senderPlayerId + ": different system (" + starSystem + " vs " + currentSystemName + ")");
                }
            }
        } catch (Exception e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Error processing message '" + message + "': " + e.getMessage());
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }
}