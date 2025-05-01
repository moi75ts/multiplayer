package matlabmaster.multiplayer;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.events.*;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import matlabmaster.multiplayer.UI.NetworkWindow;

import javax.swing.*;

public class MultiplayerModPlugin extends BaseModPlugin {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private static Server server;
    private static Client client;
    private static MessageSender messageSender;
    private static MessageHandler messageHandler;
    private static Thread serverThread;
    public static String mode = "server"; // Default mode
    public static NetworkWindow networkWindow;

    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();
        LOGGER.log(Level.INFO, "Multiplayer mod is alive");
        LOGGER.log(Level.INFO, "Player id is " + User.getUserId());

        messageHandler = new MessageHandler(User.getUserId());

        // Launch UI on EDT
        SwingUtilities.invokeLater(() -> {
            networkWindow = new NetworkWindow();
            networkWindow.setVisible(true);
        });
    }


    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().addTransientScript(new ConnectionCheckScript());
        Global.getSector().addTransientScript(new MessageProcessingScript());
        Global.getSector().addTransientScript(new FastUpdateScript());
        Global.getSector().addTransientScript(new SlowUpdateScript());
        Global.getSector().addTransientScript(new SystemEntryScript());
        Global.getSector().addTransientScript(new HyperspaceEntryScript());
        Global.getSector().addTransientScript(new UnpauseScript());
        Global.getSector().addListener(new MultiplayerListener());

        try {
            OnMultiplayerGameLoad.onGameLoad();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static MessageSender getMessageSender() {
        return messageSender;
    }

    public void onApplicationShutdown() {
        stopNetwork();
        if (networkWindow != null) networkWindow.dispose();
    }

    public static String getMode() {
        return mode;
    }

    public static void startServer(int port) {
        try {
            if (server != null) server.stop();
            server = new Server(port, messageHandler);
            messageSender = server;
            serverThread = new Thread(() -> server.start());
            serverThread.setDaemon(true);
            serverThread.setName("ServerThread");
            serverThread.start();
            LOGGER.log(Level.INFO, "Server started on port " + port);
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to start server", e);
            throw new RuntimeException("Server start failed: " + e.getMessage());
        }
    }

    public static void startClient(String ip, int port) {
        try {
            if (client != null) client.stop();
            client = new Client(ip, port, messageHandler);
            client.connect(); // Connect first
            messageSender = client; // Set messageSender after connection is established
            LOGGER.log(Level.INFO, "Client connected to " + ip + ":" + port);
            Client.initiateHandShake(messageSender); // Call handshake after setting messageSender
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to start client", e);
            throw new RuntimeException("Client start failed: " + e.getMessage());
        }
    }

    public static void stopNetwork() {
        try {
            if (server != null) {
                server.stop();
                server = null;
                if (serverThread != null) {
                    serverThread.interrupt();
                    serverThread = null;
                }
            }
            if (client != null) {
                client.stop();
                client = null;
            }
            messageSender = null;
            LOGGER.log(Level.INFO, "Network stopped");
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to stop network", e);
        }
    }

    public static void setMode(String newMode) {
        mode = newMode;
    }

    public static NetworkWindow getNetworkWindow() {
        return networkWindow;
    }
}

