package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivityIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.campaign.Faction;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.Market;
import matlabmaster.multiplayer.UI.NetworkWindow;
import matlabmaster.multiplayer.utils.CreateSystem;
import matlabmaster.multiplayer.utils.SectorCleanup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.Sys;
import org.lwjgl.util.vector.Vector2f;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.fs.starfarer.api.Global.getFactory;
import static com.fs.starfarer.api.Global.getSettings;

public class Client implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean isRunning;
    private Thread listenerThread;
    private MessageReceiver messageHandler;
    private static NetworkWindow networkWindow;

    public Client(String serverIp, int serverPort, MessageReceiver handler) {
        this.messageHandler = handler;
        this.networkWindow = MultiplayerModPlugin.getNetworkWindow();
        try {
            socket = new Socket(serverIp, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isRunning = true;
            startListener();
            LOGGER.log(Level.INFO, "Client connected to " + serverIp + ":" + serverPort);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to connect to server " + serverIp + ":" + serverPort + ": " + e.getMessage());
            stop();
        }
    }

    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                String response;
                while (isRunning && (response = in.readLine()) != null) {
                    LOGGER.log(Level.DEBUG, "Received from server: " + response);
                    if (messageHandler != null) {
                        messageHandler.onMessageReceived(response); // Queue the message for processing
                    }
                }
                LOGGER.log(Level.INFO, "Server closed connection gracefully");
            } catch (IOException e) {
                if (isRunning) {
                    LOGGER.log(Level.ERROR, "Error reading from server: " + e.getMessage());
                }
            } finally {
                stop();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.setName("ClientListener");
        listenerThread.start();
    }

    @Override
    public void sendMessage(String message) {
        if (!isRunning || out == null) {
            LOGGER.log(Level.WARN, "Client not connected, cannot send message: " + message);
            return;
        }
        out.println(message);
        LOGGER.log(Level.DEBUG, "Client sent: " + message);
    }

    @Override
    public void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message);
        }
    }

    @Override
    public boolean isActive() {
        return isRunning && socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void stop() {
        if (!isRunning) return; // Prevent multiple calls to stop
        isRunning = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (listenerThread != null) {
                listenerThread.interrupt();
                listenerThread.join(1000); // Wait up to 1 second for thread to finish
                if (listenerThread.isAlive()) {
                    LOGGER.log(Level.WARN, "Listener thread did not terminate cleanly");
                }
            }
            LOGGER.log(Level.INFO, "Client stopped");
            SwingUtilities.invokeLater(() -> {
                if (networkWindow != null) {
                    networkWindow.updateStatus(false, "Disconnected due to network issue");
                }
            });
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.ERROR, "Error stopping client: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                if (networkWindow != null) {
                    networkWindow.updateStatus(false, "Disconnected with error: " + e.getMessage());
                }
            });
        }
    }

    public static void initiateHandShake(MessageSender sender) {
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 0);
                message.put("playerId", MultiplayerModPlugin.GetPlayerId());
                String seed = Global.getSector().getSeedString();
                // Put seed even if null
                message.put("seed", seed != null ? seed : "none");
                message.put("gameVersion", getSettings().getVersionString());
                JSONArray modList = new JSONArray();
                for (ModPlugin mod : Global.getSettings().getModManager().getEnabledModPlugins()) {
                    modList.put(mod.getClass().getName());
                }
                message.put("modList", modList);
                networkWindow.getMessageField().append("Checking seed: " + seed + "\n");
                LOGGER.log(Level.INFO, "Initiating handshake with seed: " + seed);

                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to initiate handshake: " + e.getMessage());
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.log(Level.WARN, "Cannot initiate handshake: MessageSender is null or not active");
        }
    }

    public static void requestOrbitingBodiesUpdate(String currentLocation) throws JSONException {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 6);
                message.put("playerId", MultiplayerModPlugin.GetPlayerId());
                message.put("system", currentLocation);
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }

    public static void handleOrbitingBodiesUpdate(JSONObject data) {
        try {
            JSONArray toSync = data.getJSONArray("toSync");
            for (int i = 0; i < toSync.length(); i++) {
                JSONObject objectData = toSync.getJSONObject(i);
                String id = objectData.getString("id");
                float angle = (float) objectData.getDouble("a");
                if (Global.getSector().getEntityById(id) != null) {
                    SectorEntityToken token = Global.getSector().getEntityById(id);
                    token.setCircularOrbitAngle(angle);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling orbiting bodies update: " + e.getMessage());
        }
    }

    public static void requestStarscapeUpdate() throws JSONException {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command", 4);
                message.put("playerId", MultiplayerModPlugin.GetPlayerId());
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }

    public static void handleStarscapeUpdate(@NotNull JSONObject data) throws JSONException {
        //SectorAPI sector = Global.getSector();
        //JSONArray serverSideSystemList = data.getJSONArray("systems");
        //SectorCleanup.cleanupSector(data);
        //for (int i = 0; i <= serverSideSystemList.length() - 1; i++) {
        //    CreateSystem.createSystem(serverSideSystemList.getJSONObject(i));
        //}
    }

    public static void handleHandshake(JSONObject data) throws JSONException {
        String serverSeed = data.optString("seed", null);  // Use optString to handle null safely
        if (serverSeed == null) {
            networkWindow.getMessageField().append("Server has not started the game yet\n");
            networkWindow.setServerSeed("Not available");  // New method we'll add
        } else if (!Objects.equals(serverSeed, Global.getSector().getSeedString())) {
            networkWindow.getMessageField().append("Save seeds are different, please create a new game with following seed then attempt to reconnect: " + serverSeed + "\n");
            networkWindow.setServerSeed(serverSeed);  // New method we'll add
        } else {
            networkWindow.getMessageField().append("Seed match, continue with handshake\n");
            networkWindow.setServerSeed(serverSeed);  // New method we'll add
        }
    }

    public static void handleDisconnect(JSONObject data) throws JSONException {
        String reason = data.getString("reason");
        LOGGER.log(Level.INFO, "Received disconnect from server: " + reason);
        networkWindow.getMessageField().append("Server kicked, client disconnected, reason: " + reason + "\n");
        SwingUtilities.invokeLater(() -> {
            if (networkWindow != null) {
                networkWindow.updateStatus(false, "Disconnected: " + reason);
            }
        });
    }

    public static void requestMarketUpdate() throws JSONException {
        //runcode matlabmaster.multiplayer.Client.requestMarketUpdate()
        JSONObject message = new JSONObject();
        message.put("command", 2);
        message.put("playerId", MultiplayerModPlugin.GetPlayerId());
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        sender.sendMessage(message.toString());
    }

    public static void handleMarketUpdate(JSONObject data) throws JSONException {
        EconomyAPI economy = Global.getSector().getEconomy();
        JSONArray markets = data.getJSONArray("markets");
        Boolean newMarket = false;
        int i;
        int j;
        for (i = 0; i <= markets.length() - 1; i++) {
            JSONObject marketObject = markets.getJSONObject(i);
            MarketAPI market;
            newMarket = false;
            StarSystemAPI systemMarket = Global.getSector().getStarSystem(marketObject.getString("marketSystem"));
            market = economy.getMarket(marketObject.getString("marketId"));
            if (market == null) {
                market = Global.getFactory().createMarket(marketObject.getString("marketId"),marketObject.getString("name"),marketObject.getInt("marketSize"));
                newMarket = true;
            }
            SectorEntityToken primaryEntity = Global.getSector().getEntityById(marketObject.getString("primaryEntity"));
            market.setName(marketObject.getString("name"));
            market.setSize(marketObject.getInt("marketSize"));
            market.setFactionId(marketObject.getString("ownerFactionId"));
            market.setFreePort(marketObject.getBoolean("isFreePort"));
            market.setHasSpaceport(marketObject.getBoolean("hasSpaceport"));
            market.setHasWaystation(marketObject.getBoolean("hasWaystation"));
            market.setSize(marketObject.getInt("marketSize"));
            market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
            market.setPlayerOwned(false);
            market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());
            market.setHidden(marketObject.getBoolean("isHidden"));
            market.setPrimaryEntity(Global.getSector().getEntityById(marketObject.getString("primaryEntity")));
            // Get current market conditions as a Set for easier comparison
            Set<String> currentConditions = new HashSet<>();
            for (MarketConditionAPI condition : market.getConditions()) {
                currentConditions.add(condition.getId());
            }

            // Get new conditions from JSONArray as a Set
            Set<String> newConditions = new HashSet<>();
            JSONArray conditions = marketObject.getJSONArray("conditions");
            for (j = 0; j < conditions.length(); j++) {
                String condition = conditions.get(j).toString();
                newConditions.add(condition);
            }

            // Remove conditions that are in current but not in new
            for (String conditionId : currentConditions) {
                if (!newConditions.contains(conditionId)) {
                    market.removeCondition(conditionId);
                }
            }

            // Add conditions that are in new but not in current
            for (String conditionId : newConditions) {
                if (!currentConditions.contains(conditionId)) {
                    market.addCondition(conditionId);
                }
            }

            //conditions is a JSONArray of conditions id
            JSONArray connectedEntities = marketObject.getJSONArray("connectedEntities");
            for (j = 0; j <= connectedEntities.length() - 1; j++) {
                JSONObject entityObject = connectedEntities.getJSONObject(j);
                SectorEntityToken entity = Global.getSector().getEntityById(entityObject.getString("EntityID"));

                    if(entity == null){
                    BaseThemeGenerator.EntityLocation loc = new BaseThemeGenerator.EntityLocation();
                    loc.type = BaseThemeGenerator.LocationType.STAR_ORBIT;
                    loc.orbit = Global.getFactory().createCircularOrbit(Objects.requireNonNullElse(Global.getSector().getEntityById(entityObject.getString("entityOrbitFocusId")), systemMarket.getCenter()),(float) entityObject.getDouble("orbitAngle"),(float) entityObject.getDouble("orbitRadius"),(float) entityObject.getDouble("orbitPeriod"));
                    BaseThemeGenerator.AddedEntity added = BaseThemeGenerator.addNonSalvageEntity(systemMarket, loc, Entities.MAKESHIFT_STATION, marketObject.getString("ownerFactionId"));
                    added.entity.setName(entityObject.getString("entityName"));
                    added.entity.setId(entityObject.getString("EntityID"));
                    added.entity.getLocation().setX((float) entityObject.getDouble("locationx"));
                    added.entity.getLocation().setY((float) entityObject.getDouble("locationy"));
                    if(market.getPrimaryEntity() == null){
                        market.setPrimaryEntity(added.entity);
                    }else{
                        market.getConnectedEntities().add(added.entity);
                    }
                }else{
                    market.getConnectedEntities().add(entity);
                }
            }


            JSONArray industries = marketObject.getJSONArray("industries");
            for (j = 0; j <= industries.length() - 1; j++) {
                //you cannot have the same industry twice, so it dramatically simplify the code
                JSONObject industryObject = industries.getJSONObject(j);
                market.addIndustry(industryObject.getString("industryId"));
                Industry industry = market.getIndustry(industryObject.getString("industryId"));
                industry.setImproved(industryObject.getBoolean("isImproved"));
                if(industryObject.getBoolean("isDisrupted")){
                    industry.setDisrupted((float) industryObject.getDouble("distruptedDays"));
                }
            }


            if(market.getConnectedEntities().contains(null)){
                market.getConnectedEntities().remove(null); // somehow sometime i get null in there so i remove them to avoid crash
            }
            for(SectorEntityToken connectedEntity : market.getConnectedEntities()){
                connectedEntity.setMarket(market); //add the market to the planet's surface https://www.youtube.com/watch?v=HUbEhuzHur8 <3 <3 <3
            }

            market.addSubmarket(Submarkets.SUBMARKET_OPEN);
            market.addSubmarket(Submarkets.SUBMARKET_BLACK);

            if (newMarket) {
                economy.addMarket(market,false);
            }
        }
    }
}