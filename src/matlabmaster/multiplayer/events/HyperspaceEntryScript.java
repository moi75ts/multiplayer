package matlabmaster.multiplayer.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import matlabmaster.multiplayer.MultiplayerModPlugin;

public class HyperspaceEntryScript implements EveryFrameScript {
    private LocationAPI lastLocation = null;
    private boolean hasRun = false;

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
            SectorEntityToken playerFleet = Global.getSector().getPlayerFleet();
            if (playerFleet == null) return;

            LocationAPI currentLocation = playerFleet.getContainingLocation();

            // Check if the player has moved from a system to hyperspace
            if (lastLocation != null && lastLocation.getName() != currentLocation.getName() && currentLocation.isHyperspace()) {
                if (!hasRun) {
                    Global.getSector().getCampaignUI().addMessage("Player entered hyperspace!");
                    //hyperspace sync request
                    hasRun = true;
                }
            } else if (lastLocation != null && lastLocation.getName() != currentLocation.getName()) {
                hasRun = false; // Reset when entering a system
            }else{
                //logged in hyperspace
                if(!hasRun){
                    //hyperspace sync request
                    hasRun = true;
                }

            }

            lastLocation = currentLocation;
        }
    }
}