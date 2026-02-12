package matlabmaster.multiplayer;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.client.Client;
import matlabmaster.multiplayer.client.ClientScripts;
import matlabmaster.multiplayer.listeners.MultiplayerWatchdog;
import matlabmaster.multiplayer.server.Server;
import matlabmaster.multiplayer.ui.UI;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Objects;


public class MultiplayerModPlugin extends BaseModPlugin {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");

    // Unique Client and Server Instance
    private static Server serverInstance;
    private static Client clientInstance;
    private static UI uiInstance; // ADD THIS

    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();

        // Unique instantiation of client / server
        if (serverInstance == null) {
            serverInstance = new Server(20603);
        }
        if(clientInstance == null){
            clientInstance = new Client();
        }

        // CREATE UI ONLY ONCE
        if (uiInstance == null) {
            uiInstance = new UI(serverInstance, clientInstance);
            uiInstance.showUI();
            new MultiplayerWatchdog(clientInstance, serverInstance).start();
        }

        LOGGER.log(Level.INFO, "Multiplayer mod UI initialized");
    }

    @Override
    public void onGameLoad(boolean newGame){
        super.onGameLoad(newGame);
        Global.getSector().addTransientScript(new ClientScripts(clientInstance));
        System.out.println("registered scripts");
    }

    public static Server getServer() {
        return serverInstance;
    }

    public static Client getClient(){
        return clientInstance;
    }
}