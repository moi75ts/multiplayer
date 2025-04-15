package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import matlabmaster.multiplayer.SlowUpdates.CargoPodsSync;
import matlabmaster.multiplayer.utils.CargoHelper;
import matlabmaster.multiplayer.utils.FleetHelper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Math.abs;

public class MessageProcessingScript implements EveryFrameScript {
    private static final Logger LOGGER = LogManager.getLogger("MessageProcessingScript");

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
        processMessages();
    }

    private void processMessages() {
        ConcurrentLinkedQueue<String> queue = MessageHandler.getMessageQueue();
        String message;
        while ((message = queue.poll()) != null) {
            try {
                JSONObject data = new JSONObject(message);
                int command = data.getInt("command");

                switch (command) {
                    case 0:
                        handleHandshake(data);
                        break;
                    case 1:
                        handleDisconnect(data);
                        break;
                    case 2:
                        handleMarketUpdate(data);
                        break;
                    case 4:
                        handleStarscapeUpdate(data);
                        break;
                    case 5: // Fleet position update
                        handleFleetUpdate(data);
                        break;
                    case 6: // Orbiting bodies update
                        handeOrbitUpdate(data);
                        break;
                    case 7:// cargopods check
                        handleCargoPods(data);
                        break;
                    default:
                        LOGGER.log(Level.WARN, "Unknown command received: " + command);
                }
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Error processing message '" + message + "': " + e.getMessage());
            }
        }
    }

    private void handleFleetUpdate(JSONObject data) {
        try {
            String senderPlayerId = data.getString("playerId");
            JSONObject serializedFleet = data.getJSONObject("fleet");
            String location = serializedFleet.getString("location");
            SectorAPI sector = Global.getSector();
            LocationAPI currentLocation = sector.getCurrentLocation();
            float serverX = 0;
            float serverY = 0;
            float remoteX = (float) serializedFleet.getDouble("locationX");
            float remoteY = (float) serializedFleet.getDouble("locationY");
            String currentSystemName = currentLocation != null ? currentLocation.getName() : "";//??
            CampaignFleetAPI fleet = (CampaignFleetAPI) sector.getEntityById(senderPlayerId);
            if (Objects.equals(location.toLowerCase(), currentSystemName.toLowerCase())) {
                if (fleet == null) {
                    fleet = Global.getFactory().createEmptyFleet("neutral", "WIP", true);
                    assert currentLocation != null;
                    currentLocation.addEntity(fleet);
                    FleetHelper.unSerializeFleet(serializedFleet,fleet,false);
                    fleet.setId(senderPlayerId);
                    fleet.setFaction("neutral");
                    fleet.setName("Fleet of " + senderPlayerId);
                } else {
                    SectorEntityToken entity = Global.getSector().getEntityById(senderPlayerId);
                    if (entity instanceof CampaignFleetAPI) {
                        fleet = (CampaignFleetAPI) entity;
                    }
                }
                fleet.setAI(null);
                fleet.setMoveDestination(fleet.getLocation().getX(), fleet.getLocation().getY());
                FleetHelper.unSerializeAbilities(serializedFleet.getJSONArray("abilities"),fleet);
                fleet.setTransponderOn(serializedFleet.getBoolean("isTransponderOn"));
                serverX = fleet.getLocation().getX();
                serverY = fleet.getLocation().getY();
                float deltaX = remoteX - serverX;
                float deltaY = remoteY - serverY;
                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                fleet.setLocation(remoteX, remoteY);


                LOGGER.log(Level.DEBUG, "Updated fleet for player " + senderPlayerId + " in " + location);
            } else {
                if (fleet != null) {
                    fleet.despawn();
                }
                LOGGER.log(Level.DEBUG, "Fleet not updated for " + senderPlayerId + ": different system (" + location + " vs " + currentSystemName + ")");
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling fleet update: " + e.getMessage());
        }
    }

    private void handeOrbitUpdate(JSONObject data) {
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
                Server.sendOrbitingBodiesUpdate(data.getString("system"));
            } else {
                Client.handleOrbitingBodiesUpdate(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling orbit update: " + e.getMessage());
        }
    }

    private void handleStarscapeUpdate(JSONObject data) {
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
                Server.sendStarscapeUpdate();
            } else {
                Client.handleStarscapeUpdate(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling starscape update: " + e.getMessage());
        }
    }

    private void handleHandshake(JSONObject data) {
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
                Server.handleHandshake(data);
            } else {
                Client.handleHandshake(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling handshake " + e.getMessage());
        }
    }

    private void handleDisconnect(JSONObject data) {
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "client")) {
                Client.handleDisconnect(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling disconnect " + e.getMessage());
        }
    }

    private void handleMarketUpdate(JSONObject data) {
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
                Server.handleMarketUpdateRequest(data.getString("playerId"));
            } else {
                Client.handleMarketUpdate(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling marketUpdate " + e.getMessage());
        }
    }

    private void handleCargoPods(JSONObject data) {
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
                CargoPodsSync.compareCargoPods(data);
            } else {
                CargoHelper.updateLocalPods(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling cargo pods check " + e.getMessage());
        }
    }
}