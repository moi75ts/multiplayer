package matlabmaster.multiplayer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MessageHandler implements MessageReceiver {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private final String playerId;
    private static final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();

    public MessageHandler(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public void onMessageReceived(String message) {
        LOGGER.log(Level.INFO, "Received message: " + message);
        try {
            JSONObject data = new JSONObject(message);
            String senderPlayerId = data.getString("playerId");
            if (!Objects.equals(senderPlayerId, playerId)) { // Ignore own messages
                messageQueue.add(message); // Queue for processing in EveryFrameScript
                LOGGER.log(Level.INFO, "Message queued for processing");
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