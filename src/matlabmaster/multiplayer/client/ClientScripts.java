package matlabmaster.multiplayer.client;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.campaign.Faction;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.updates.FleetSync;
import matlabmaster.multiplayer.updates.WorldSync;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.FleetSerializer;
import matlabmaster.multiplayer.utils.PauseUtility;
import matlabmaster.multiplayer.utils.WorldSerializer;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientScripts implements EveryFrameScript {
    private final Client client;
    private float timer = 0f;
    private final FleetSync fleetSync = new FleetSync();
    private final WorldSync worldSync = new WorldSync();

    // Message waitlist coming from client thread
    private static final ConcurrentLinkedQueue<JSONObject> messageQueue = new ConcurrentLinkedQueue<>();

    public ClientScripts(Client client) {
        this.client = client;

        // Attaching listener to fill the queue
        this.client.addListener(new Client.ClientListener() {  // CHANGED FROM setListener TO addListener
            @Override
            public void onDisconnected() {
                messageQueue.clear();
                MultiplayerLog.log().info("CLEARING MESSAGE QUEUE.");
            }

            @Override
            public void onMessageReceived(String msg) {
                try {
                    // Transform msg to JSON and add it to the queue
                    messageQueue.add(new JSONObject(msg));
                } catch (Exception e) {
                    MultiplayerLog.log().error("JSON FORMAT ERROR: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (client == null || !client.isConnected()) {
            return;
        }

        //handle the game pausing , disable classic in game pause
        //if the game is in a dialog inform the server
        PauseUtility.clientPauseUtility(client,fleetSync);

        // --- 1. process received message every frame ---
        // they are processed every frame to limit lag
        while (!messageQueue.isEmpty()) {
            JSONObject json = messageQueue.poll();
            if (json != null) {
                processMessage(json);
            }
        }

        // --- 2. send updates (TICKS 20 TPS) ---
        timer += amount;
        float INTERVAL = 0.05f;
        if (timer >= INTERVAL) {
            timer -= INTERVAL;
            executeTick();
        }
    }

    /**
     * Incoming message dispatcher.
     * Executed in the main game thread
     */
    private void processMessage(JSONObject message) {
        try {
            if (!message.has("commandId")) return;
            String commandId = message.getString("commandId");
            JSONObject packet;
            switch (commandId) {
                case "playerFleetUpdate":
                    if(Global.getSector().getEntityById(message.getString("fleetId")) instanceof CampaignFleetAPI){
                        fleetSync.handleRemoteFleetUpdate(message);
                    }else{
                        //only run if not paused because if the client is pause it will continuously ask for snapshots
                        //and then try to spawn all of them when unpausing resulting in 1000 fleet spawning
                        if(!Global.getSector().isPaused()){
                            //usually called when a fleet dies and respawn
                            MultiplayerLog.log().error("playerFleetUpdate : unknown fleet");
                            packet = new JSONObject();
                            packet.put("commandId","requestPlayerFleetSnapshot");
                            packet.put("to",message.getString("from"));
                            packet.put("from",client.clientId);
                            client.send(packet.toString());
                        }
                    }
                    break;
                case "handleAllFleetsSnapshot":
                    int i;
                    for(i = 0; i < message.getJSONArray("fleets").length() ; i ++){
                        JSONObject unserializedFleet = (JSONObject) message.getJSONArray("fleets").get(i);
                        if(Global.getSector().getEntityById(unserializedFleet.getString("id")) instanceof CampaignFleetAPI){
                            ((CampaignFleetAPI) Global.getSector().getEntityById(unserializedFleet.getString("id"))).despawn();
                        }
                        FleetSerializer.unSerializeFleet(unserializedFleet,Global.getFactory().createEmptyFleet(Faction.NO_FACTION, true));
                    }
                    MultiplayerLog.log().info("added " + i + " fleets");
                    break;
                case "fleetSnapshot":
                    FleetSerializer.unSerializeFleet(message.getJSONObject("fleet"),Global.getFactory().createEmptyFleet(Faction.NO_FACTION,true));
                    break;
                case "playerLeft":
                    FleetHelper.removeFleetById(message.getString("id"));
                    MultiplayerLog.log().info("[LEFT] " + message.getString("id") + " left the game");
                    break;
                case "playerJoined":
                    MultiplayerLog.log().info("[JOINED] " + message.getString("id") + " joined the game");
                    break;
                case "globalFleetsUpdate":
                    //modifying msg to match what fleetSync.handleRemoteFleetUpdate expect
                    JSONObject updates = message.getJSONObject("updates");
                    Iterator<?> keys = updates.keys();
                    while (keys.hasNext()){
                        String fleetId = (String) keys.next();
                        JSONObject updateWrapper = new JSONObject();
                        updateWrapper.put("fleetId", fleetId);
                        updateWrapper.put("changes", updates.getJSONObject(fleetId));

                        //same as PLayerFleetUpdate but with a list of fleets to update
                        if (Global.getSector().getEntityById(fleetId) instanceof CampaignFleetAPI) {
                            fleetSync.handleRemoteFleetUpdate(updateWrapper);
                        } else {
                            if(!Global.getSector().isPaused()){
                                //only run if not paused because if the client is pause it will continuously ask for snapshots
                                //and then try to spawn all of them when unpausing resulting in 1000 fleet spawning
                                packet = new JSONObject();
                                packet.put("commandId","requestFleetSnapshot");
                                packet.put("fleetId",fleetId);
                                MultiplayerLog.log().warn("globalFleetsUpdate : unknown fleet " + fleetId);
                                client.send(packet.toString());
                            }
                        }
                    }
                    break;
                case "youAreAuthority":
                    client.isAuthority = true;
                    MultiplayerLog.log().debug("you are the authority");
                    break;
                case "youAreNoLongerAuthority":
                    client.isAuthority = false;
                    MultiplayerLog.log().debug("you are no longer the authority");
                    break;
                case "requestFleetSnapshot":
                    packet = new JSONObject();
                    packet.put("fleet",FleetSerializer.serializeFleet((CampaignFleetAPI) Global.getSector().getEntityById(message.getString("fleetId"))));
                    packet.put("commandId","handleFleetSnapshotRequest");
                    packet.put("to",message.getString("from"));
                    client.send(packet.toString());
                    break;
                case "handleFleetSnapshotRequest":
                    FleetSerializer.unSerializeFleet(message.getJSONObject("fleet"),Global.getFactory().createEmptyFleet(Faction.NO_FACTION,true));
                    MultiplayerLog.log().info("spawned "+ message.getJSONObject("fleet").getString("id")+ " fleet following request");
                    break;
                case "requestPlayerFleetSnapshot":
                    packet = new JSONObject();
                    packet.put("commandId","handleFleetSnapshotRequest");
                    packet.put("to",message.getString("from"));
                    packet.put("fleet",FleetSerializer.serializeFleet(Global.getSector().getPlayerFleet()));
                    client.send(packet.toString());
                    break;
                case "requestOrbitSnapshotForLocation":
                    LocationAPI location;
                    if(Objects.equals(message.getString("location"), "hyperspace")){
                        location = Global.getSector().getHyperspace();
                    }else{
                        location = Global.getSector().getStarSystem(message.getString("location"));
                    }
                    worldSync.sendOrbitSnapshotForLocation(location,client,message.getString("from"));
                    break;
                case "handleOrbitSnapshotForLocation":
                    JSONObject orbits = message.getJSONObject("orbits");
                    Iterator<?> orbitKeys = orbits.keys();
                    while (orbitKeys.hasNext()) {
                        String key = (String) orbitKeys.next();
                        try {
                            WorldSerializer.unSerializeOrbit(orbits.getJSONObject(key));
                        } catch (Exception e) {
                            MultiplayerLog.log().warn("Failed to apply orbit for " + key + ": " + e.getMessage());
                        }
                    }
                    break;
                default:
                    MultiplayerLog.log().warn("unknown command: " + commandId);
                    break;
            }
        } catch (Exception e) {
            MultiplayerLog.log().error("Exception in processMessage: " + e.getMessage() + " " + message.toString(), e);
        }
    }

    private void executeTick() {
        try {
            fleetSync.sendOwnFleetUpdate(client);
            if(client.isAuthority){
                fleetSync.sendGlobalFleetsUpdate(client);
            }
        } catch (Exception e) {
            MultiplayerLog.log().error("Unable to send own fleet update: " + e.getMessage(), e);
        }
    }
}