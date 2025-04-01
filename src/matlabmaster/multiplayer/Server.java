package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean isRunning;
    private List<PrintWriter> clientWriters;
    private MessageReceiver messageHandler;

    public Server(int port, MessageReceiver handler) {
        this.messageHandler = handler;
        try {
            serverSocket = new ServerSocket(port);
            executor = Executors.newCachedThreadPool();
            clientWriters = new ArrayList<>();
            isRunning = true;
            LOGGER.log(org.apache.log4j.Level.INFO, "Server initialized on port " + port);
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Failed to start server on port " + port + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void start() {
        LOGGER.log(org.apache.log4j.Level.INFO, "Server started on port " + serverSocket.getLocalPort());
        try {
            while (isRunning && !serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.log(org.apache.log4j.Level.INFO, "New client connected: " + clientSocket.getInetAddress());
                executor.execute(() -> handleClientConnection(clientSocket));
            }
        } catch (IOException e) {
            if (isRunning) {
                LOGGER.log(org.apache.log4j.Level.ERROR, "Server error: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            synchronized (clientWriters) {
                clientWriters.add(out);
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                LOGGER.log(org.apache.log4j.Level.DEBUG, "Received from client: " + inputLine);
                if (messageHandler != null) {
                    messageHandler.onMessageReceived(inputLine); // Immediate callback
                }
            }
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Client connection error: " + e.getMessage());
        } finally {
            if (out != null) {
                synchronized (clientWriters) {
                    clientWriters.remove(out);
                }
            }
            closeQuietly(in);
            closeQuietly(out);
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.log(org.apache.log4j.Level.ERROR, "Error closing client socket: " + e.getMessage());
            }
        }
    }

    @Override
    public void sendMessage(String message) {
        if (!isRunning) {
            LOGGER.log(org.apache.log4j.Level.WARN, "Server not running, cannot send message: " + message);
            return;
        }
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println(message);
            }
        }
        LOGGER.log(Level.DEBUG, "Server broadcasted: " + message);
    }

    @Override
    public void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message); // Delegate to handler
        }
    }

    @Override
    public boolean isActive() {
        return isRunning && !serverSocket.isClosed();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executor != null) {
                executor.shutdown();
            }
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.close();
                }
                clientWriters.clear();
            }
            LOGGER.log(org.apache.log4j.Level.INFO, "Server stopped");
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Error stopping server: " + e.getMessage());
        }
    }

    private void closeQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public static void sendOrbitingBodiesUpdate(String systemName) throws JSONException {
        //client method
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try{
                JSONObject message = new JSONObject();
                message.put("command", 6);
                StarSystemAPI system = Global.getSector().getStarSystem(systemName);
                JSONArray planets = new JSONArray();
                for (SectorEntityToken entity : system.getAllEntities()) {
                    if (entity instanceof PlanetAPI) {
                        JSONObject planet = new JSONObject();
                        planet.put("PId", entity.getId());
                        planet.put("a", entity.getCircularOrbitAngle());
                        planets.put(planet);
                    }
                }
                message.put("planet",planets);
                sender.sendMessage(message.toString());
            } catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }
}