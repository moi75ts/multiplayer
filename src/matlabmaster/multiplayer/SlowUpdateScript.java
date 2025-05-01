package matlabmaster.multiplayer;

import com.fs.starfarer.api.EveryFrameScript;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;


public class SlowUpdateScript implements EveryFrameScript {
    private static final Logger LOGGER = LogManager.getLogger("multiplayer");
    private static final float INTERVAL = 5.0f; // Every 5 seconds
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
        if(MultiplayerModPlugin.getMessageSender() != null){
            timer += amount;
            if (timer >= INTERVAL) {
                timer -= INTERVAL; // Reset with remainder
            }
        }
    }
}