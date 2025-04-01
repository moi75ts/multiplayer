package matlabmaster.multiplayer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberViewAPI;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.Sys;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class Client implements MessageSender, MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean isRunning;
    private Thread listenerThread;
    private MessageReceiver messageHandler;

    public Client(String serverIp, int serverPort, MessageReceiver handler) {
        this.messageHandler = handler;
        try {
            socket = new Socket(serverIp, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isRunning = true;
            startListener();
            LOGGER.log(org.apache.log4j.Level.INFO, "Client connected to " + serverIp + ":" + serverPort);
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Failed to connect to server " + serverIp + ":" + serverPort + ": " + e.getMessage());
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
                        messageHandler.onMessageReceived(response); // Immediate callback
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    LOGGER.log(org.apache.log4j.Level.ERROR, "Error reading from server: " + e.getMessage());
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
            LOGGER.log(org.apache.log4j.Level.WARN, "Client not connected, cannot send message: " + message);
            return;
        }
        out.println(message);
        LOGGER.log(org.apache.log4j.Level.DEBUG, "Client sent: " + message);
    }

    @Override
    public void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.onMessageReceived(message); // Delegate to handler
        }
    }

    @Override
    public boolean isActive() {
        return isRunning && socket != null && !socket.isClosed();
    }

    public void stop() {
        isRunning = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            if (listenerThread != null) listenerThread.interrupt();
            LOGGER.log(org.apache.log4j.Level.INFO, "Client stopped");
        } catch (IOException e) {
            LOGGER.log(org.apache.log4j.Level.ERROR, "Error stopping client: " + e.getMessage());
        }
    }

    public static void requestOrbitingBodiesUpdate(String currentLocation) throws JSONException {
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        if (sender != null && sender.isActive()) {
            try {
                JSONObject message = new JSONObject();
                message.put("command",6);
                message.put("playerId", MultiplayerModPlugin.GetPlayerId());
                message.put("system",currentLocation);
                sender.sendMessage(message.toString());
            }catch (JSONException e) {
                LOGGER.log(Level.ERROR, "Failed to construct JSON message: " + e.getMessage());
            }
        }
    }
    public static void handleOrbitingBodiesUpdate(JSONObject data){
        try {
            JSONArray planets = data.getJSONArray("planet");
            for (int i = 0; i < planets.length(); i++) {
                JSONObject planetData = planets.getJSONObject(i);
                String planetId = planetData.getString("PId");
                float angle = (float) planetData.getDouble("a");
                PlanetAPI planet = (PlanetAPI) Global.getSector().getEntityById(planetId);
                if (planet != null) planet.setCircularOrbitAngle(angle);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling orbiting bodies update: " + e.getMessage());
        }
    }
}