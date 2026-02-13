package matlabmaster.multiplayer.server;

import com.fs.starfarer.api.EveryFrameScript;
import org.json.JSONException;
import org.json.JSONObject;

public class ServerScripts implements EveryFrameScript {
    private final Server serverInstance;

    public ServerScripts(Server serverInstance){
        this.serverInstance = serverInstance;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if(serverInstance.isRunning){
            // Check if authority is null or points to a disconnected client
            if(serverInstance.authority == null || !serverInstance.clients.containsValue(serverInstance.authority)){
                for(Server.ClientHandler handler : serverInstance.clients.values()){
                    if(!handler.isPaused){
                        serverInstance.authority = handler;
                        JSONObject packet = new JSONObject();
                        try {
                            packet.put("commandId","youAreAuthority");
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        handler.sendMessage(packet.toString());
                        return;
                    }
                }
                //if we are here this means no unpaused client have been found, to ensure sync if a new client joins we must have any existing client has authority
                for(Server.ClientHandler handler : serverInstance.clients.values()){
                    serverInstance.authority = handler;
                    JSONObject packet = new JSONObject();
                    try {
                        packet.put("commandId","youAreAuthority");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    handler.sendMessage(packet.toString());
                    return;
                }
            }
        }
    }
}