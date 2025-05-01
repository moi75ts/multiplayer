package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import matlabmaster.multiplayer.fastUpdates.CargoPodsSync;
import matlabmaster.multiplayer.utils.CargoHelper;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.MarketUpdateHelper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONException;
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
                        handleMarketUpdateRequest(data);
                        break;
                    case 3:
                        handleMarketUpdate(data);
                        break;
                    case 4:
                        handleStarscapeUpdate(data);
                        break;
                    case 5:
                        handleFleetUpdate(data);
                        break;
                    case 6:
                        handeOrbitUpdate(data);
                        break;
                    case 7:
                        handleCargoPods(data);
                        break;
                    case 9:
                        handleFleetSpawn(data);
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
            String currentSystemName = currentLocation != null ? currentLocation.getName() : "";
            CampaignFleetAPI fleet = (CampaignFleetAPI) sector.getEntityById(serializedFleet.getString("id"));
            if (Objects.equals(location.toLowerCase(), currentSystemName.toLowerCase())) {
                if (fleet == null) {
                    fleet = Global.getFactory().createEmptyFleet("neutral", "fleet of " + senderPlayerId, true);
                    assert currentLocation != null;
                    currentLocation.addEntity(fleet);
                } else {
                    SectorEntityToken entity = Global.getSector().getEntityById(serializedFleet.getString("id"));
                    if (entity instanceof CampaignFleetAPI) {
                        fleet = (CampaignFleetAPI) entity;
                    }
                }
                fleet.setId(senderPlayerId);
                FleetHelper.unSerializeFleet(serializedFleet, fleet, true);
                fleet.setAI(null);


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
                Server.sendOrbitingBodiesUpdate(data);
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

    private void handleMarketUpdate(JSONObject data) throws JSONException {
        String senderPlayerId = data.getString("playerId");
        if (Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
            MarketUpdateHelper.handleMarketUpdate(data);
            Server serverInstance = (Server) MultiplayerModPlugin.getMessageSender();
            serverInstance.sendToEveryoneBut(senderPlayerId, data.toString());
        } else {
            MarketUpdateHelper.handleMarketUpdate(data);
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
            LOGGER.log(Level.ERROR, "Error handling cargo pods check: " + e.getMessage());
        }
    }

    private void handleMarketUpdateRequest(JSONObject data) {
        try {
            MarketUpdateHelper.handleMarketUpdateRequest(data.getString("playerId"));
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling marketUpdate request " + e.getMessage());
        }
    }
    private void handleFleetSpawn(JSONObject data) throws JSONException {
        FleetHelper.spawnNewFleet(data);
    }
}