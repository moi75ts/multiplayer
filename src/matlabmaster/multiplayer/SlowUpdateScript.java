package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentLinkedQueue;

public class SlowUpdateScript implements EveryFrameScript {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private static final float INTERVAL = 2.0f; // Every 2 seconds
    private float timer = 0f;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        timer += amount;
        if (timer >= INTERVAL) {
            timer -= INTERVAL; // Reset with remainder
            processMessages();
        }
    }

    private void processMessages() {
        ConcurrentLinkedQueue<String> queue = MessageHandler.getMessageQueue();
        String message;
        while ((message = queue.poll()) != null) {
            try {
                JSONObject data = new JSONObject(message);
                int command = data.getInt("command");
                if (command != 5) { // Non-location data (slow updates)
                    handleSlowUpdate(data);
                }
                // Command 5 skipped here, handled in FastUpdateScript
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Error processing slow update message '" + message + "': " + e.getMessage());
            }
        }
    }

    private void handleSlowUpdate(JSONObject data) {
        try {
            String senderPlayerId = data.getString("playerId");
            int command = data.getInt("command");
            // Example: Handle hypothetical slow-changing data (e.g., stationary fleet status)
            LOGGER.log(Level.INFO, "Processed slow update for player " + senderPlayerId + " with command " + command);
            // Add logic here for less frequent updates (e.g., command 6 or new commands)
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error handling slow update: " + e.getMessage());
        }
    }
}