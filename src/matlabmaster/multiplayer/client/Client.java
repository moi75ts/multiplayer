package matlabmaster.multiplayer.client;

import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.UserError;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.FleetSerializer;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class Client {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean isConnected = false;
    private final CopyOnWriteArrayList<ClientListener> listeners = new CopyOnWriteArrayList<>();
    public boolean isSelfHosted = false;
    public boolean isAuthority = false;
    public boolean wasPaused = false;

    public interface ClientListener {
        void onDisconnected();
        void onMessageReceived(String msg);
    }

    public void addListener(ClientListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ClientListener listener) {
        listeners.remove(listener);
    }

    public void connect(String ip, int port) throws IOException {
        if(Objects.equals(Global.getCurrentState().toString(), "TITLE")){
            throw new UserError("You cannot connect to a server while on the main menu, join any singleplayer game then try connecting");
        }
        socket = new Socket();
        socket.connect(new InetSocketAddress(ip, port), 5000);

        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

        Global.getSector().getPlayerFleet().setId("User-" + socket.getLocalPort());
        isConnected = true;

        // SUCCESS MESSAGE
        System.out.println("[CLIENT] CONNECTED SUCCESSFULLY TO SERVER " + ip + ":" + port);
        if(!isSelfHosted){
            try {
                //send our fleet to the server so that it knows about it
                JSONObject packet = new JSONObject();
                System.out.println("[CLIENT] SENDING PLAYER FLEET TO SERVER");
                packet.put("commandId","fleetSnapshot");
                packet.put("fleet",FleetSerializer.serializeFleet(Global.getSector().getPlayerFleet()));
                send(packet.toString());

                //prepare for all fleet syncing
                System.out.println("[CLIENT] DESTROYING EXISTING FLEETS");
                FleetHelper.killAllFleetsExceptPlayer();

                //ask for all the sectors fleet snapshot
                System.out.println("[CLIENT] REQUESTING FLEETS SNAPSHOT");
                packet = new JSONObject();
                packet.put("commandId","requestAllFleetsSnapshot");
                send(packet.toString());
            }catch (Exception e){
                System.out.println("[ERROR] Handshake failed " + Arrays.toString(e.getStackTrace()));
                disconnect();
            }
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
                            System.err.println("[ERROR] Exception in listener.onMessageReceived(): " + e.getMessage());
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
            System.out.println("[CLIENT] DISCONNECTED FROM SERVER.");
            try { if (socket != null) socket.close(); } catch (IOException e) {}

            // Notify ALL listeners
            for (ClientListener listener : listeners) {
                try {
                    listener.onDisconnected();
                } catch (Exception e) {
                    System.err.println("[ERROR] Exception in listener.onDisconnected(): " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public void disconnect() {
        handleDisconnect();
    }

    public boolean isConnected() { return isConnected; }
}