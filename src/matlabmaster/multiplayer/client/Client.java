package matlabmaster.multiplayer.client;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.UserError;
import matlabmaster.multiplayer.updates.WorldSync;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.FleetSerializer;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class Client {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean isConnected = false;
    private final CopyOnWriteArrayList<ClientListener> listeners = new CopyOnWriteArrayList<>();
    private boolean savedIdleWhileWindowNotVisible = true;
    private float savedCampaignSpeedupMult = 2f;
    public boolean isSelfHosted = false;
    public boolean isAuthority = false;
    public boolean wasPaused = false;
    public String clientId;
    public Long multiplayerTimestamp;
    public CampaignClockAPI multiplayerClock;

    public interface ClientListener {
        void onDisconnected();
        void onMessageReceived(String msg);
    }

    public void addListener(ClientListener listener) {
        listeners.add(listener);
    }

    public void connect(String ip, int port) throws IOException {
        if(Objects.equals(Global.getCurrentState().toString(), "TITLE")){
            throw new UserError("You cannot connect to a server while on the main menu, join any singleplayer game then try connecting");
        }
        socket = new Socket();
        socket.connect(new InetSocketAddress(ip, port), 5000);

        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        Global.getSector().getPlayerFleet().setId("User-" + socket.getLocalPort());
        isConnected = true;
        clientId = "User-" + socket.getLocalPort();

        // Apply multiplayer-only settings (reverted on disconnect)
        savedIdleWhileWindowNotVisible = Global.getSettings().getBoolean("idleWhileWindowNotVisible");
        savedCampaignSpeedupMult = Global.getSettings().getFloat("campaignSpeedupMult");
        Global.getSettings().setBoolean("idleWhileWindowNotVisible", false);
        Global.getSettings().setFloat("campaignSpeedupMult", 1f);

        // SUCCESS MESSAGE
        MultiplayerLog.log().info("CONNECTED SUCCESSFULLY TO SERVER " + ip + ":" + port);
        if(!isSelfHosted){
            try {
                //send our fleet to the server so that it knows about it
                JSONObject packet = new JSONObject();
                MultiplayerLog.log().info("SENDING PLAYER FLEET TO SERVER");
                packet.put("commandId","fleetSnapshot");
                packet.put("fleet",FleetSerializer.serializeFleet(Global.getSector().getPlayerFleet()));
                send(packet.toString());

                //prepare for all fleet syncing
                MultiplayerLog.log().info("DESTROYING EXISTING FLEETS");
                FleetHelper.killAllFleetsExceptPlayer();

                //ask for all the sectors fleet snapshot
                MultiplayerLog.log().info("REQUESTING FLEETS SNAPSHOT");
                packet = new JSONObject();
                packet.put("commandId","requestAllFleetsSnapshot");
                send(packet.toString());

                //ask for current location orbits
                //todo update for all locations maybe if rly useful?
                WorldSync.requestOrbitSnapshotForLocation(Global.getSector().getPlayerFleet().getContainingLocation(),this);
                WorldSync.requestServerTime(this);

            }catch (Exception e){
                MultiplayerLog.log().error("Handshake failed", e);
                disconnect();
            }
        }else{
            //is self-hosted also since it's the first client it will be the authority
            multiplayerTimestamp = Global.getSector().getClock().getTimestamp();
        }
        new Thread(() -> {
            try {
                String line;
                while (isConnected && (line = in.readLine()) != null) {
                    // Notify ALL listeners
                    for (ClientListener listener : listeners) {
                        try {
                            listener.onMessageReceived(line);
                        } catch (Exception e) {
                            MultiplayerLog.log().error("Exception in listener.onMessageReceived(): " + e.getMessage(), e);
                        }
                    }
                }
            } catch (IOException e) {
                // server probably cut out
            } finally {
                // Crucial : inform ui in case of error
                handleDisconnect();
            }
        }, "Client-Read-Thread").start();
    }

    public void send(String message){
        out.println(message);
    }

    private void handleDisconnect() {
        if (isConnected) {
            isSelfHosted = false;
            isConnected = false;
            multiplayerTimestamp = null;
            multiplayerClock = null;
            Global.getSettings().setBoolean("idleWhileWindowNotVisible", savedIdleWhileWindowNotVisible);
            Global.getSettings().setFloat("campaignSpeedupMult", savedCampaignSpeedupMult);
            MultiplayerLog.log().info("DISCONNECTED FROM SERVER.");
            try { if (socket != null) socket.close(); } catch (IOException e) {MultiplayerLog.log().error("Unknown IO exception" + Arrays.toString(e.getStackTrace()));}

            // Notify ALL listeners
            for (ClientListener listener : listeners) {
                try {
                    listener.onDisconnected();
                } catch (Exception e) {
                    MultiplayerLog.log().error("Exception in listener.onDisconnected(): " + e.getMessage(), e);
                }
            }
        }
    }

    public void disconnect() {
        handleDisconnect();
    }

    public boolean isConnected() { return isConnected; }
}