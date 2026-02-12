package matlabmaster.multiplayer.client;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.campaign.Faction;
import matlabmaster.multiplayer.updates.FleetSync;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.FleetSerializer;
import org.json.JSONObject;

import java.util.Arrays;
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
                System.out.println("[CLIENT] CLEARING MESSAGE QUEUE.");
            }

            @Override
            public void onMessageReceived(String msg) {
                try {
                    // On transforme la string en JSON immédiatement et on l'ajoute à la queue
                    messageQueue.add(new JSONObject(msg));
                } catch (Exception e) {
                    System.err.println("[CLIENT-ERROR] JSON FORMAT ERROR: " + e.getMessage());
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

        if(Global.getSector().isPaused()){
            if(!Global.getSector().getCampaignUI().isShowingDialog()){
                Global.getSector().setPaused(false);
            }
        }

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
                        System.out.println("[ERROR] playerFleetUpdate : unknown fleet");
                        //todo fleet is unknown
                    }
                    break;
                case "handleAllFleetsSnapshot":
                    int i;
                    for(i = 0; i < message.getJSONArray("fleets").length() ; i ++){
                        FleetSerializer.unSerializeFleet((JSONObject) message.getJSONArray("fleets").get(i),Global.getFactory().createEmptyFleet(Faction.NO_FACTION, true));
                    }
                    System.out.println("[CLIENT] added " + i + " fleets");
                    break;
                case "fleetSnapshot":
                    FleetSerializer.unSerializeFleet(message.getJSONObject("fleet"),Global.getFactory().createEmptyFleet(Faction.NO_FACTION,true));
                    break;
                case "playerLeft":
                    FleetHelper.removeFleetById(message.getString("id"));
                    System.out.println("[LEFT] " + message.getString("id") + " left the game");
                    break;
                case "playerJoined":
                    System.out.println("[JOINED] " + message.getString("id") + " joined the game");
                default:
                    System.out.println("[ERROR] unknown command: " + commandId);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Exception in processMessage: " + e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()) + message.toString());
        }
    }

    private void executeTick() {
        try {
            // Envoi de notre position au serveur
            fleetSync.sendOwnFleetUpdate(client);
        } catch (Exception e) {
            System.err.println("[ERROR] Unable to send own fleet update: " + e.getMessage());
        }
    }
}