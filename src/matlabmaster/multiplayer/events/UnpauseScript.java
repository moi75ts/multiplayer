package matlabmaster.multiplayer.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.requests.StarSystemSync;

public class UnpauseScript implements EveryFrameScript {
    private boolean wasPaused = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true; // Must run while paused to detect the transition
    }

    @Override
    public void advance(float amount) {
        boolean isPaused = Global.getSector().isPaused();
        if (wasPaused && !isPaused) {
            Global.getSector().getCampaignUI().addMessage("Game unpaused!");
            if(!Global.getSector().getCurrentLocation().isHyperspace()){
                StarSystemSync.orbitUpdateRequest();
            }
        }
        wasPaused = isPaused;
    }
}