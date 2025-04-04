package matlabmaster.multiplayer;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class DebugScript {
    public static void removeAllEntitiesFromGalatiaAndHyperspaceLinks() {
        // Get the sector
        SectorAPI sector = Global.getSector();

        // Get the Galatia system
        StarSystemAPI system = sector.getStarSystem("galatia");
        if (system == null) {
            Global.getLogger(DebugScript.class).error("System 'galatia' not found!");
            return;
        }

        // Step 1: Remove all entities from Galatia
        System.out.println("Is Galatia empty? " + system.getAllEntities().isEmpty());
        List<SectorEntityToken> galatiaEntities = new ArrayList<>(system.getAllEntities());
        for (SectorEntityToken entity : galatiaEntities) {
            system.removeEntity(entity);
            Global.getLogger(DebugScript.class).info("Removed entity from Galatia: " + entity.getName());
        }
        System.out.println("After removal, is Galatia empty? " + system.getAllEntities().isEmpty());

        // Step 2: Remove hyperspace entities linking to Galatia
        LocationAPI hyperspace = sector.getHyperspace();
        if (hyperspace == null) {
            Global.getLogger(DebugScript.class).error("Hyperspace not found!");
            return;
        }

        // Get Galatia's hyperspace coordinates
        Vector2f systemLocation = system.getLocation();
        String systemId = system.getId();
        System.out.println("Galatia hyperspace location: " + systemLocation + ", System ID: " + systemId);

        List<SectorEntityToken> hyperspaceEntities = new ArrayList<>(hyperspace.getAllEntities());
        for (SectorEntityToken entity : hyperspaceEntities) {
            String entityName = entity.getName() != null ? entity.getName() : "Unnamed Entity";
            String entityType = entity.getCustomEntityType() != null ? entity.getCustomEntityType() : entity.getClass().getSimpleName();
            Vector2f entityLocation = entity.getLocation();
            float distance = Misc.getDistance(systemLocation, entityLocation);

            System.out.println("Entity: " + entityName + ", Type: " + entityType + ", Location: " + entityLocation + ", Distance to Galatia: " + distance);

            // Handle Jump Points
            if (entity instanceof JumpPointAPI) {
                JumpPointAPI jumpPoint = (JumpPointAPI) entity;
                List<JumpPointAPI.JumpDestination> destinations = jumpPoint.getDestinations();
                for (JumpPointAPI.JumpDestination dest : destinations) {
                    if (dest.getDestination() != null && dest.getDestination().getContainingLocation() == system) {
                        hyperspace.removeEntity(jumpPoint);
                        Global.getLogger(DebugScript.class).info("Removed hyperspace jump point linking to Galatia: " + entityName);
                        break;
                    }
                }
            }
            // Handle Gravity Wells (including NascentGravityWell)
            else {
                boolean isGravityWell = entityType.equals("NascentGravityWell") ||
                        entityName.toLowerCase().contains("gravity well");
                boolean isNearGalatia = distance < 700f;

                if (isGravityWell && isNearGalatia) {
                    hyperspace.removeEntity(entity);
                    Global.getLogger(DebugScript.class).info("Removed gravity well near Galatia: " + entityName + " at " + entityLocation);
                }
            }
        }

        System.out.println("Finished removing hyperspace links and gravity wells for Galatia.");
    }
}