package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class RemoveStarSystem {
    private static final Logger log = Global.getLogger(RemoveStarSystem.class);
    private static final float GRAVITY_WELL_PROXIMITY_THRESHOLD = 1000f;

    public static void removeStarSystem(String systemId) {
        SectorAPI sector = Global.getSector();
        StarSystemAPI system = sector.getStarSystem(systemId);

        if (system == null) {
            log.error("System '"+ systemId + "' not found!");
            return;
        }

        removeEntitiesFromSystem(system);
        removeHyperspaceLinksToSystem(sector, system);
        Global.getSector().removeStarSystem(system);

        log.debug("Finished removing Galatia entities and hyperspace links.");
    }

    private static void removeEntitiesFromSystem(StarSystemAPI system) {
        boolean wasEmpty = system.getAllEntities().isEmpty();
        log.debug("Is " + system.getName() + " empty? " + wasEmpty);

        List<SectorEntityToken> entities = new ArrayList<>(system.getAllEntities());
        for (SectorEntityToken entity : entities) {
            system.removeEntity(entity);
            log.debug("Removed entity: " + entity.getName());
        }

        log.debug("After removal, is " + system.getName() + " empty? " + system.getAllEntities().isEmpty());
    }

    private static void removeHyperspaceLinksToSystem(SectorAPI sector, StarSystemAPI targetSystem) {
        LocationAPI hyperspace = sector.getHyperspace();
        if (hyperspace == null) {
            log.error("Hyperspace not found!");
            return;
        }

        Vector2f systemLocation = targetSystem.getLocation();
        log.debug(targetSystem.getName() + " hyperspace location: " + systemLocation + ", System ID: " + targetSystem.getId());

        List<SectorEntityToken> hyperspaceEntities = new ArrayList<>(hyperspace.getAllEntities());
        for (SectorEntityToken entity : hyperspaceEntities) {
            processHyperspaceEntity(entity, hyperspace, targetSystem, systemLocation);
        }
    }

    private static void processHyperspaceEntity(SectorEntityToken entity, LocationAPI hyperspace,
                                                StarSystemAPI targetSystem, Vector2f systemLocation) {
        String entityName = entity.getName() != null ? entity.getName() : "Unnamed Entity";
        String entityType = entity.getCustomEntityType() != null ? entity.getCustomEntityType() : entity.getClass().getSimpleName();
        Vector2f entityLocation = entity.getLocation();
        float distance = Misc.getDistance(systemLocation, entityLocation);

        log.debug("Checking entity: " + entityName + ", Type: " + entityType +
                ", Distance to " + targetSystem.getName() + ": " + distance);

        if (entity instanceof JumpPointAPI) {
            processJumpPoint((JumpPointAPI) entity, hyperspace, targetSystem, entityName);
        } else {
            processGravityWell(entity, hyperspace, entityName, entityType, distance, entityLocation);
        }
    }

    private static void processJumpPoint(JumpPointAPI jumpPoint, LocationAPI hyperspace,
                                         StarSystemAPI targetSystem, String entityName) {
        for (JumpPointAPI.JumpDestination dest : jumpPoint.getDestinations()) {
            if (dest.getDestination() != null && dest.getDestination().getContainingLocation() == targetSystem) {
                hyperspace.removeEntity(jumpPoint);
                log.debug("Removed hyperspace jump point linking to " + targetSystem.getName() + ": " + entityName);
                break;
            }
        }
    }

    private static void processGravityWell(SectorEntityToken entity, LocationAPI hyperspace,
                                           String entityName, String entityType, float distance, Vector2f entityLocation) {
        boolean isGravityWell = entityType.equals("NascentGravityWell") ||
                entityName.toLowerCase().contains("gravity well");
        boolean isNearTargetSystem = distance < GRAVITY_WELL_PROXIMITY_THRESHOLD;

        if (isGravityWell && isNearTargetSystem) {
            hyperspace.removeEntity(entity);
            log.debug("Removed gravity well: " + entityName + " at " + entityLocation);
        }
    }
}