package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import matlabmaster.multiplayer.UI.NetworkWindow;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectionCheckScript implements EveryFrameScript {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private boolean isDone = false;
    private long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 1000; // Check every second

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime < CHECK_INTERVAL) {
            return;
        }
        lastCheckTime = currentTime;
        AtomicBoolean wasConnectedLastTime = new AtomicBoolean(false);

        if (MultiplayerModPlugin.getMode().equals("client")) {
            MessageSender sender = MultiplayerModPlugin.getMessageSender();
            boolean isConnected = sender != null && sender.isActive();

            // Update UI on EDT thread
            SwingUtilities.invokeLater(() -> {
                NetworkWindow window = MultiplayerModPlugin.getNetworkWindow();
                if (window != null) {
                    if (!isConnected && wasConnectedLastTime.get()) {
                        window.updateStatus(false, "Disconnected - Connection lost");
                        window.getMessageField().append("Connection to server lost\n");
                        LOGGER.log(Level.INFO, "Client connection lost");
                        wasConnectedLastTime.set(false);
                    }else{
                        wasConnectedLastTime.set(true);
                    }
                }
            });
        }
    }
} 