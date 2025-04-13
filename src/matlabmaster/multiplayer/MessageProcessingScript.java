package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import matlabmaster.multiplayer.SlowUpdates.CargoPodsSync;
import matlabmaster.multiplayer.utils.CargoPodsHelper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

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
            float serverX = (float) data.getDouble("x");
            float serverY = (float) data.getDouble("y");
            String starSystem = data.getString("starSystem");
            boolean transponder = data.getBoolean("transponder");
            JSONArray ships = data.getJSONArray("ships");
            float moveDestinationX = (float) data.getDouble("moveDestinationX");
            float moveDestinationY = (float) data.getDouble("moveDestinationY");
            JSONArray abilities = data.getJSONArray("abilities");
            int i;
            SectorAPI sector = Global.getSector();
            LocationAPI currentLocation = sector.getCurrentLocation();
            String currentSystemName = currentLocation != null ? currentLocation.getName() : "";

            CampaignFleetAPI fleet = (CampaignFleetAPI) sector.getEntityById(senderPlayerId);
            if (Objects.equals(starSystem.toLowerCase(), currentSystemName.toLowerCase())) {
                if (fleet == null) {
                    fleet = Global.getFactory().createEmptyFleet("neutral", "Fleet of " + senderPlayerId, true);
                    fleet.setId(senderPlayerId);
                    for (i = 0; i < ships.length(); i++) {
                        JSONArray ship = ships.getJSONArray(i);
                        String shipName = ship.getString(0).isEmpty() ? "Unnamed Ship" : ship.getString(0);
                        String variantId = ship.getString(1) + "_Hull";//this is done this way (with the +_Hull) because the default ship have a weird default Hullvariant id

                        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                        member.setShipName(shipName);
                        fleet.getFleetData().addFleetMember(member);
                        LOGGER.log(Level.DEBUG, "Added " + shipName + " (" + variantId + ") to fleet");
                    }

                    fleet.setAI(null);
                   assert currentLocation != null;
                    currentLocation.addEntity(fleet);
                }


                for(i = 0; i<abilities.length() ;i++){
                    JSONObject abilityObject = abilities.getJSONObject(i);
                    if(Objects.equals(abilityObject.getString("abilityId"), "transponder")){
                        fleet.setTransponderOn(transponder);
                        continue; // transponder seems to behave differently than other abilities with inprogress always true, even when off
                    }
                    AbilityPlugin ability = fleet.getAbility(abilityObject.getString("abilityId"));
                    if(ability == null){
                        fleet.addAbility(abilityObject.getString("abilityId"));
                    }else{
                        if(abilityObject.getBoolean("abilityActive")){
                            ability.activate();
                        } else if (abilityObject.getBoolean("abilityInProgress")){
                            ability.activate();
                        }else {
                            ability.deactivate();
                        }
                    }
                }

                if (Math.abs(serverX - fleet.getLocation().x) > 200 || Math.abs(serverY - fleet.getLocation().y) > 200) {
                    fleet.setLocation(serverX, serverY); // this player fleet is not where it should be by a noticeable margin (+ or -), TP it to the right cords
                    //200 is arbitrary number, may be adjusted for better results
                }else{
                    fleet.setMoveDestination(moveDestinationX,moveDestinationY);//smooth movement
                }

                LOGGER.log(Level.DEBUG, "Updated fleet for player " + senderPlayerId + " at [" + serverX + ", " + serverY + "] in " + starSystem);
            } else {
                if (fleet != null) {
                    fleet.despawn();
                }
                LOGGER.log(Level.DEBUG, "Fleet not updated for " + senderPlayerId + ": different system (" + starSystem + " vs " + currentSystemName + ")");
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
    private void handleHandshake(JSONObject data){
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
    private void handleDisconnect(JSONObject data){
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "client")) {
                Client.handleDisconnect(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling disconnect " + e.getMessage());
        }
    }

    private void handleMarketUpdate(JSONObject data){
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

    private void handleCargoPods(JSONObject data){
        try {
            if (Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
                CargoPodsSync.compareCargoPods(data);
            }else{
                CargoPodsHelper.updateLocalPods(data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling cargo pods check " + e.getMessage());
        }
    }
}