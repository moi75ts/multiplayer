package matlabmaster.multiplayer.listeners;

import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.client.Client;
import matlabmaster.multiplayer.server.Server;

import java.util.Objects;

public class MultiplayerWatchdog extends Thread {
    private final Client client;
    private final Server server;

    public MultiplayerWatchdog(Client client, Server server) {
        this.client = client;
        this.server = server;
        this.setDaemon(true);
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (Objects.equals(Global.getCurrentState().toString(), "TITLE")) {
                    if (server.isRunning) {
                        server.stop();
                        MultiplayerLog.log().info("[SERVER SHUTDOWN] game is on main menu");
                    }
                    if (client.isConnected()) {
                        client.disconnect();
                        MultiplayerLog.log().info("[DISCONNECT] game is on main menu");
                    }
                }
                // Add a sleep to prevent busy looping and high CPU usage
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore interruption
                }
            }catch (Exception e){
            }

        }
    }
}