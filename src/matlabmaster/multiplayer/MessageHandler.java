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
    private final String playerId;
    private static final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();

    public MessageHandler(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public void onMessageReceived(String message) {
        LOGGER.log(Level.DEBUG, "Received message: " + message);
        try {
            JSONObject data = new JSONObject(message);
            String senderPlayerId = data.getString("playerId");
            String reason = "";
            if (!Objects.equals(senderPlayerId, playerId)) { // Ignore own messages
                //reason is only used i we were kicked
                try{
                    reason = data.getString("reason");
                }catch (Exception e){
                    reason = null;
                }
                if( reason != null){
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