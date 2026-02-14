package matlabmaster.multiplayer.client;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.campaign.Faction;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.updates.FleetSync;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.FleetSerializer;
import matlabmaster.multiplayer.utils.PauseUtility;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientScripts implements EveryFrameScript {
    private final Client client;
    private float timer = 0f;
    private final FleetSync fleetSync = new FleetSync();

    // File d'attente sécurisée pour les messages venant du thread Client
    private static final ConcurrentLinkedQueue<JSONObject> messageQueue = new ConcurrentLinkedQueue<>();

    public ClientScripts(Client client) {
        this.client = client;

        // On attache le listener pour remplir la queue
        this.client.addListener(new Client.ClientListener() {  // CHANGED FROM setListener TO addListener
            @Override
            public void onDisconnected() {
                messageQueue.clear();
                MultiplayerLog.log().info("CLEARING MESSAGE QUEUE.");
            }

            @Override
            public void onMessageReceived(String msg) {
                try {
                    // On transforme la string en JSON immédiatement et on l'ajoute à la queue
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
        PauseUtility.clientPauseUtility(client);

        // --- 1. TRAITEMENT DES MESSAGES REÇUS (À CHAQUE FRAME) ---
        // On traite TOUS les messages en attente pour éviter la latence
        while (!messageQueue.isEmpty()) {
            JSONObject json = messageQueue.poll();
            if (json != null) {
                processMessage(json);
            }
        }

        // --- 2. ENVOI DES MISES À JOUR (TICKS 20 TPS) ---
        timer += amount;
        float INTERVAL = 0.05f;
        if (timer >= INTERVAL) {
            timer -= INTERVAL;
            executeTick();
        }
    }

    /**
     * Dispatcher pour les messages entrants.
     * Exécuté dans le thread principal du jeu.
     */
    private void processMessage(JSONObject message) {
        try {
            if (!message.has("commandId")) return;
            String commandId = message.getString("commandId");

            switch (commandId) {
                case "playerFleetUpdate":
                    if(Global.getSector().getEntityById(message.getString("fleetId")) instanceof CampaignFleetAPI){
                        fleetSync.handleRemoteFleetUpdate(message);
                    }else{
                        //usually called when a fleet dies and respawn
                        MultiplayerLog.log().error("playerFleetUpdate : unknown fleet");
                        JSONObject packet = new JSONObject();
                        packet.put("commandId","requestPlayerFleetSnapshot");
                        packet.put("to",message.getString("from"));
                        packet.put("from",client.clientId);
                        client.send(packet.toString());
                    }
                    break;
                case "handleAllFleetsSnapshot":
                    int i;
                    for(i = 0; i < message.getJSONArray("fleets").length() ; i ++){
                        FleetSerializer.unSerializeFleet((JSONObject) message.getJSONArray("fleets").get(i),Global.getFactory().createEmptyFleet(Faction.NO_FACTION, true));
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
                            JSONObject packet = new JSONObject();
                            packet.put("commandId","requestFleetSnapshot");
                            packet.put("fleetId",fleetId);
                            MultiplayerLog.log().warn("globalFleetsUpdate : unknown fleet " + fleetId);
                            client.send(packet.toString());
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
                    JSONObject packet = new JSONObject();
                    packet.put("fleet",FleetSerializer.serializeFleet((CampaignFleetAPI) Global.getSector().getEntityById(message.getString("fleetId"))));
                    packet.put("commandId","handleFleetSnapshotRequest");
                    packet.put("to",message.getString("from"));
                    client.send(packet.toString());
                    break;
                case "handleFleetSnapshotRequest":
                    //noinspection DuplicateBranchesInSwitch
                    FleetSerializer.unSerializeFleet(message.getJSONObject("fleet"),Global.getFactory().createEmptyFleet(Faction.NO_FACTION,true));
                    MultiplayerLog.log().info("new fleet");
                    break;
                case "requestPlayerFleetSnapshot":
                    packet = new JSONObject();
                    packet.put("commandId","handleFleetSnapshotRequest");
                    packet.put("to",message.getString("from"));
                    packet.put("fleet",FleetSerializer.serializeFleet(Global.getSector().getPlayerFleet()));
                    client.send(packet.toString());
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