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
import org.json.JSONObject;
import org.lwjgl.Sys;

public class Server {
    private final int port;
    private ServerSocket serverSocket;
    public volatile boolean isRunning = false; // volatile pour garantir la visibilité entre threads
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private ExecutorService threadPool;
    private  ServerListener listener;

    public Server(int port) {
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

            // 3. Dispatcher
            switch (commandId) {
                case "playerFleetUpdate":
                    broadcastExcept(clientId, message);
                    break;
                case "requestAllFleetsSnapshot":
                    JSONArray fleetsSnapshot = FleetHelper.getFleetsSnapshot();
                    JSONObject packet = new JSONObject();
                    packet.put("commandId", "handleAllFleetsSnapshot");
                    packet.put("fleets",fleetsSnapshot);
                    sendTo(clientId, String.valueOf(packet));
                    break;
                case "fleetSnapshot":
                    //noinspection DuplicateBranchesInSwitch
                    broadcastExcept(clientId, message);
                    break;
                default:
                    System.out.println("[INFO] Unknown command: " + commandId);
                    break;
            }

        } catch (Exception e) {
            // Si org.json lève une exception de sécurité, on le saura ici
            System.err.println("[ERROR] JSON Error from " + clientId + " : " + e.getMessage());
        }
    }

    // --- INNER CLASS : GESTIONNAIRE DE CLIENT ---

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientId;
        private PrintWriter out;
        private final Server server;

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
            clients.remove(clientId);
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