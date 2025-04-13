package matlabmaster.multiplayer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import static matlabmaster.multiplayer.MultiplayerModPlugin.networkWindow;

public class MessageHandler implements MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private String playerId;
    private static final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();

    public MessageHandler(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public void onMessageReceived(String message) {
        LOGGER.log(Level.DEBUG, "Received message: " + message);
        this.playerId = User.getUserId();
        try {
            JSONObject data = new JSONObject(message);
            String senderPlayerId = data.getString("playerId");
            String reason = "";
            String seed = "";
            if (!Objects.equals(senderPlayerId, playerId)) { // Ignore own messages
                //reason is only used i we were kicked
                try{
                    reason = data.getString("reason");
                    seed = data.getString("seed");
                }catch (Exception e){
                    reason = null;
                    seed = null;
                }
                if( reason != null){
                    if ( seed != null){
                        networkWindow.setServerSeed(seed);
                    }
                    networkWindow.getMessageField().append("Server kicked, client disconnected, reason: " + data.getString("reason") + "\n");
                }else{
                    messageQueue.add(message); // Queue for processing in EveryFrameScript
                    LOGGER.log(Level.DEBUG, "Message queued for processing");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error queuing message '" + message + "': " + e.getMessage());
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }

    public static ConcurrentLinkedQueue<String> getMessageQueue() {
        return messageQueue;
    }
}