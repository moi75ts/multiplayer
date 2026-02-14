package matlabmaster.multiplayer.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import matlabmaster.multiplayer.UserError;
import matlabmaster.multiplayer.utils.FleetHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.Sys;

public class Server {
    private int port;
    private ServerSocket serverSocket;
    public volatile boolean isRunning = false; // volatile pour garantir la visibilité entre threads
    public final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private ExecutorService threadPool;
    private  ServerListener listener;
    public ClientHandler authority;

    public Server(int port) {
        this.port = port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public interface ServerListener{
        void onServerStopped();
    }

    public void setListener(ServerListener listener) {
        this.listener = listener;
    }

    public void start() {
        if(Objects.equals(Global.getCurrentState().toString(), "TITLE")){
            throw new UserError("You cannot host a server while on the main menu, join any singleplayer game then try hosting");
        }
        if (isRunning) return;
        isRunning = true;
        threadPool = Executors.newCachedThreadPool();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("[SERVER] Server started on port " + port);

                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        if (!isRunning) break; // Sécurité si stop() est appelé pile au moment d'un accept

                        String clientId = "User-" + socket.getPort();
                        ClientHandler handler = new ClientHandler(socket, clientId, this);
                        clients.put(clientId, handler);

                        System.out.println("[JOINED] " + clientId + " is connected");
                        JSONObject packet = new JSONObject();
                        try {
                            packet.put("commandId","playerJoined");
                            packet.put("id",clientId);
                        }catch (Exception e){
                            System.out.println("[ERROR] failed to broadcast player joined");
                        }
                        threadPool.execute(handler);

                    } catch (IOException e) {
                        if (isRunning) System.err.println("[ERROR] Accept : " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (isRunning) System.err.println("[ERROR] Port " + port + " unavailable.");
            } finally {
                internalStop();
            }
        }, "Server-Main-Thread").start();
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        System.out.println("[SERVER] SERVER STOPPING...");
        internalStop();
    }

    private synchronized void internalStop() {
        // Si le socket est déjà nul ou fermé, on ne fait rien
        if (serverSocket == null || serverSocket.isClosed()) return;

        try {
            serverSocket.close();
            for (ClientHandler handler : clients.values()) {
                handler.closeConnection();
            }
            clients.clear();
            if (threadPool != null) threadPool.shutdownNow();

            System.out.println("[SERVER] SERVER STOPPED.");

            if (listener != null) {
                listener.onServerStopped();
            }
        } catch (IOException e) {
            // Pas besoin de log les erreurs de fermeture ici
        } finally {
            serverSocket = null; // Important pour éviter le double log
        }
    }

    public void processIncomingMessage(String clientId, String message) {
        try {
            // 1. Instanciation de l'objet JSON (pas de réflexion ici, juste du parsing de texte)
            JSONObject json = new JSONObject(message);
            //System.out.println("[DEBUG] message recieved from " + clientId + " : " + message);
            // 2. Vérification de la commande
            if (!json.has("commandId")) return;

            String commandId = json.getString("commandId");
            JSONObject packet = new JSONObject();
            // 3. Dispatcher
            switch (commandId) {
                case "playerFleetUpdate":
                    broadcastExcept(clientId, message);
                    break;
                case "requestAllFleetsSnapshot":
                    JSONArray fleetsSnapshot = FleetHelper.getFleetsSnapshot();

                    packet.put("commandId", "handleAllFleetsSnapshot");
                    packet.put("fleets",fleetsSnapshot);
                    sendTo(clientId, String.valueOf(packet));
                    break;
                case "fleetSnapshot":
                    //noinspection DuplicateBranchesInSwitch
                    broadcastExcept(clientId, message);
                    break;
                case "globalFleetsUpdate":
                    //noinspection DuplicateBranchesInSwitch
                    broadcastExcept(clientId, message);
                    break;
                case "requestFleetSnapshot":
                    packet.put("commandId","requestFleetSnapshot");
                    packet.put("from",clientId);
                    packet.put("fleetId",json.getString("fleetId"));
                    authority.sendMessage(packet.toString());
                    //relay request to authority client
                    //send back reply to original asker
                    break;
                case "handleFleetSnapshotRequest":
                    sendTo(json.getString("to"),json.toString());
                    break;
                case "paused":
                    clients.get(clientId).isPaused = true;
                    System.out.println("[INFO] client " + clientId + " has paused");
                    if(clients.get(clientId) == authority){
                        authorityManager(this);
                    }
                    break;
                case "unpaused":
                    clients.get(clientId).isPaused = false;
                    System.out.println("[INFO] client " + clientId + " has unpaused");
                    break;
                default:
                    System.out.println("[INFO] Unknown command: " + commandId);
                    break;
            }

        } catch (Exception e) {
            System.err.println("[ERROR] JSON Error from " + clientId + " : " + e.getMessage());
        }
    }

    private void authorityManager(Server server) throws JSONException {
        //if this method was called this means that the authority paused / left
        //to ensure smooth gameplay across clients a new authority must be set so that updates keep flowing
        //if all the clients are paused keep the authority the same
        ClientHandler newAuthority = null;
        ClientHandler lastClient = null;
        ClientHandler originalAuthority = server.authority;
        
        // Clean up: if current authority is no longer in clients, clear it
        if(originalAuthority != null && !server.clients.containsValue(originalAuthority)){
            originalAuthority = null;
            server.authority = null;
        }
        
        for (ClientHandler client : server.clients.values()){
            lastClient = client;
            if(!client.isPaused){
                newAuthority = client;
                break; // Found an unpaused client, use them as authority
            }
        }
        
        // If no unpaused client found, keep the current authority (or set to last client if authority was removed)
        if(newAuthority == null){
            if(authority != null && server.clients.containsValue(authority)){
                // Keep current authority if they're still in the clients list
                return;
            } else {
                // Authority was removed, set to last client (or null if no clients)
                // If no clients remain, authority will be null (handled by ServerScripts)
                authority = lastClient;
            }
        } else {
            authority = newAuthority;
        }

        if(authority != originalAuthority){
            if(originalAuthority != null){
                JSONObject packet = new JSONObject();
                packet.put("commandId","youAreNoLongerAuthority");
                originalAuthority.sendMessage(packet.toString());
            }
            JSONObject packet = new JSONObject();
            packet.put("commandId","youAreAuthority");
            authority.sendMessage(packet.toString());
        }
    }

    // --- INNER CLASS : GESTIONNAIRE DE CLIENT ---

    public class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientId;
        private PrintWriter out;
        private final Server server;
        public boolean isPaused;

        public ClientHandler(Socket socket, String clientId, Server server) {
            this.socket = socket;
            this.clientId = clientId;
            this.server = server;
        }

        @Override
        public void run() {
            Thread.currentThread().setContextClassLoader(Server.class.getClassLoader());
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
                this.out = new PrintWriter(socket.getOutputStream(), true);

                String input;
                // La boucle s'arrête si le client coupe (input == null) OU si le serveur s'arrête
                while (server.isRunning && (input = in.readLine()) != null) {
                    server.processIncomingMessage(clientId, input);
                }
            } catch (IOException e) {
                // Erreur de lecture souvent due à une fermeture brutale
            } finally {
                closeConnection();
                JSONObject packet = new JSONObject();
                try {
                    packet.put("commandId","playerLeft");
                    packet.put("id",clientId);
                    broadcast(String.valueOf(packet));
                }catch (Exception e){
                    System.out.println("Failed to broadcast playerLeft of leaving player");
                }
            }
        }

        public void sendMessage(String msg) {
            if (out != null && !socket.isClosed()) {
                out.println(msg);
            }
        }

        public void closeConnection() {
            boolean wasAuthority = (this == server.authority);
            clients.remove(clientId);
            
            // If the authority disconnected, reassign authority
            if (wasAuthority) {
                try {
                    authorityManager(server);
                } catch (JSONException e) {
                    System.err.println("[ERROR] Failed to reassign authority after disconnect: " + e.getMessage());
                }
            }
            
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // Déjà fermé
            }
        }
    }

    public void broadcast(String message) {
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(message);
        }
    }

    public void broadcastExcept(String senderId, String message) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(senderId)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    public void sendTo(String clientId, String message) {
        ClientHandler handler = clients.get(clientId);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }
}