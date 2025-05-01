package matlabmaster.multiplayer.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import matlabmaster.multiplayer.MultiplayerModPlugin;
import matlabmaster.multiplayer.requests.StarSystemSync;

public class SystemEntryScript implements EveryFrameScript {
    private LocationAPI lastLocation = null;
    private boolean hasRun = false; // Optional: to make it fire only once per entry

    @Override
    public boolean isDone() {
        return false; // Keep running indefinitely
    }

    @Override
    public boolean runWhilePaused() {
        return false; // Don’t run while paused
    }

    @Override
    public void advance(float amount) {
        if(MultiplayerModPlugin.getMessageSender() != null){
            SectorEntityToken playerFleet = Global.getSector().getPlayerFleet();
            if (playerFleet == null) return;

            LocationAPI currentLocation = playerFleet.getContainingLocation();

            // Check if the player has moved from hyperspace to a system
            if (lastLocation != null && lastLocation.isHyperspace() && currentLocation.getName() != lastLocation.getName()) {
                if (!hasRun) {
                    StarSystemSync.orbitUpdateRequest();
                    Global.getSector().getCampaignUI().addMessage("Player entered system: " + currentLocation.getName());
                    hasRun = true; // Prevent repeated triggers in the same system
                }
            } else if (lastLocation != null && currentLocation.isHyperspace()) {
                hasRun = false; // Reset when leaving to hyperspace
            }else{
                //logged in system
                if(!hasRun){
                    StarSystemSync.orbitUpdateRequest();
                    hasRun = true;
                }
            }

            lastLocation = currentLocation;
        }
    }
}