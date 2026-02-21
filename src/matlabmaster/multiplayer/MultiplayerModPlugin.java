package matlabmaster.multiplayer;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.client.Client;
import matlabmaster.multiplayer.client.ClientScripts;
import matlabmaster.multiplayer.listeners.MultiplayerWatchdog;
import matlabmaster.multiplayer.server.Server;
import matlabmaster.multiplayer.server.ServerScripts;
import matlabmaster.multiplayer.ui.UI;

import java.util.Objects;


public class MultiplayerModPlugin extends BaseModPlugin {

    // Unique Client and Server Instance
    private static Server serverInstance;
    private static Client clientInstance;
    private static UI uiInstance;
    private static ClientScripts clientScriptsInstance;
    private static ServerScripts serverScriptsInstance;

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
            new MultiplayerWatchdog(clientInstance, serverInstance).start();}

        MultiplayerLog.log().info("Multiplayer mod UI initialized");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        if (clientScriptsInstance == null) { //ensure only one client script exist at any time
            clientScriptsInstance = new ClientScripts(clientInstance);
        }
        Global.getSector().addTransientScript(clientScriptsInstance);

        if (serverScriptsInstance == null) {
            serverScriptsInstance = new ServerScripts(serverInstance);
        }
        Global.getSector().addTransientScript(serverScriptsInstance);
        MultiplayerLog.log().info("registered scripts");
    }

    public static Server getServer() {
        return serverInstance;
    }

    public static Client getClient(){
        return clientInstance;
    }

    public static UI getUI(){
        return uiInstance;
    }
}