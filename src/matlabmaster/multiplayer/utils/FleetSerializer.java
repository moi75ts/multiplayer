package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;
import com.fs.starfarer.campaign.ai.CampaignFleetAI;
import com.fs.starfarer.campaign.ai.ModularFleetAI;
import com.fs.starfarer.campaign.fleet.CampaignFleet;
import com.fs.starfarer.launcher.opengl.GLModPickerV2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class FleetSerializer {

    /**
     * Applies a JSON Diff to a live CampaignFleetAPI.
     * This iterates through the changes and calls the appropriate game engine methods.
     */
    public static void applyFleetDiff(CampaignFleetAPI fleet, JSONObject diff) throws JSONException {
        Iterator<?> keys = diff.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object delta = diff.get(key);

            // Handle root fleet properties (UPDATE instructions)
            if (delta instanceof JSONObject && ((JSONObject) delta).has("action")) {
                applyRootProperty(fleet, key, (JSONObject) delta);
            }
            // Handle nested maps (ships, cargo, abilities, persons)
            else if (delta instanceof JSONObject) {
                applyNestedPatch(fleet, key, (JSONObject) delta);
            }
        }
    }

    private static void applyRootProperty(CampaignFleetAPI fleet, String key, JSONObject instruction) throws JSONException {
        String action = instruction.getString("action");
        if (!"UPDATE".equals(action)) return; //all the root parameters always exist for a fleet

        Object value = instruction.get("value");
        switch (key) {
            case "locationX":
                // Use ((Number) value).doubleValue() to handle both Integer and Double safely
                if (Math.abs(fleet.getLocation().getX() - ((Number) value).doubleValue()) > 50) {
                    fleet.setLocation(((Number) value).floatValue(), fleet.getLocation().getY());
                }
                break;
            case "locationY":
                if (Math.abs(fleet.getLocation().getY() - ((Number) value).doubleValue()) > 50) {
                    fleet.setLocation(fleet.getLocation().getX(), ((Number) value).floatValue());
                }
                break;
            case "location":
                LocationAPI area;
                if(Objects.equals(value, "hyperspace")){
                    area = Global.getSector().getHyperspace();
                }else{
                    area = Global.getSector().getStarSystem((String) value);
                }
                SectorEntityToken landingZone = area.createToken(fleet.getLocation().x,fleet.getLocation().y);
                JumpPointAPI.JumpDestination destination = new JumpPointAPI.JumpDestination(landingZone, "multiplayerJump");
                Global.getSector().doHyperspaceTransition(fleet,fleet, destination);
                area.removeEntity(landingZone);

                break;
            case "isTransponderOn":
                fleet.setTransponderOn((Boolean) value);
                break;
            case "factionId":
                fleet.setFaction((String) value);
                break;
            case "moveDestinationX":
                fleet.setMoveDestination(((Number) value).floatValue(), fleet.getMoveDestination().getY()); //update x and y independently (in case only one of them is updated)
                break;
            case "moveDestinationY":
                fleet.setMoveDestination(fleet.getMoveDestination().getX(), ((Number) value).floatValue());
                break;
            case "aiMode":
                fleet.setAIMode((Boolean) value);
                break;
            case "name":
                fleet.setName((String) value);
        }
    }

    private static void applyNestedPatch(CampaignFleetAPI fleet, String rootKey, JSONObject subDiff) throws JSONException {
        switch (rootKey) {
            case "ships":
                patchShips(fleet, subDiff);
                break;
            case "cargo":
                CargoSerializer.patchCargo(fleet.getCargo(), subDiff);
                break;
            case "abilities":
                patchAbilities(fleet, subDiff);
                break;
            case "assignment":
                System.out.println("[assignment] " + subDiff);
        }
    }

    private static void patchShips(CampaignFleetAPI fleet, JSONObject shipsDiff) throws JSONException {
        Iterator<?> shipIds = shipsDiff.keys();
        while (shipIds.hasNext()) {
            String shipId = (String) shipIds.next();
            Object delta = shipsDiff.get(shipId);

            FleetMemberAPI member = null;
            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                if (m.getId().equals(shipId)) {
                    member = m;
                    break;
                }
            }

            if (delta instanceof JSONObject obj && obj.has("action")) {
                String action = obj.getString("action");
                if ("REMOVED".equals(action) && member != null) {
                    fleet.getFleetData().removeFleetMember(member);
                } else if ("ADDED".equals(action)) {
                    FleetMemberAPI newMember = unSerializeFleetMember(obj.getJSONObject("value"));
                    newMember.setId(shipId);
                    fleet.getFleetData().addFleetMember(newMember);
                }
            } else if (delta instanceof JSONObject changes && member != null) {
                if (changes.has("hullMods")) {
                    patchHullMods(member.getVariant(), changes.getJSONObject("hullMods"), false);
                }

                if (changes.has("sHullMods")) {
                    patchHullMods(member.getVariant(), changes.getJSONObject("sHullMods"), true);
                }
                if (changes.has("combatReadiness")) {
                    Object crVal = changes.getJSONObject("combatReadiness").get("value");//todo maybe not needed since the game show be able to track it on its own, keep for now
                    member.getRepairTracker().setCR(((Number) crVal).floatValue());
                }
                if (changes.has("name")) {
                    member.setShipName(changes.getJSONObject("name").getString("value"));
                }
                if (changes.has("captain")) {
                    Object captainDelta = changes.get("captain");
                    PersonAPI currentCaptain = member.getCaptain();

                    if (captainDelta instanceof JSONObject obj && obj.has("action")) {
                        // CASE A: The entire captain object changed/swapped
                        String action = obj.getString("action");
                        if ("UPDATE".equals(action) || "ADDED".equals(action)) {
                            member.setCaptain(PersonsSerializer.unSerializePerson(obj.getJSONObject("value")));
                        }
                    } else if (captainDelta instanceof JSONObject nestedChanges && currentCaptain != null) {
                        // CASE B: Granular changes to the existing captain (skills, level, etc.)
                        PersonsSerializer.patchPerson(currentCaptain, nestedChanges);
                    }

                    // Safety: Always sync commander if it's the flagship
                    if (member.isFlagship()) {
                        fleet.setCommander(member.getCaptain());
                    }
                }
                if (changes.has("isMothballed")){
                    member.getRepairTracker().setMothballed(changes.getJSONObject("isMothballed").getBoolean("value"));
                }
                if (changes.has("fluxVents")){
                    member.getVariant().setNumFluxVents(changes.getJSONObject("fluxVents").getInt("value"));
                }
                if (changes.has("fluxCapacitors")){
                    member.getVariant().setNumFluxCapacitors(changes.getJSONObject("fluxCapacitors").getInt("value"));
                }

                if (changes.has("fittedGuns")) {
                    JSONObject gunsDiff = changes.getJSONObject("fittedGuns");
                    patchFittedGuns(member.getVariant(), gunsDiff);
                }

                if (changes.has("fittedWings")) {
                    JSONObject wingsDiff = changes.getJSONObject("fittedWings");
                    patchFittedWings(member.getVariant(), wingsDiff);
                }

                if (changes.has("weaponGroups")) {
                    patchWeaponGroups(member.getVariant(), changes.getJSONObject("weaponGroups"));
                }
                if (changes.has("isFlagShip")){
                    member.setFlagship(changes.getJSONObject("isFlagShip").getBoolean("value"),true);
                }
            }
        }
    }

    private static void patchAbilities(CampaignFleetAPI fleet, JSONObject abilitiesDiff) throws JSONException {
        Iterator<?> keys = abilitiesDiff.keys();
        while (keys.hasNext()) {
            String abilityId = (String) keys.next();
            Object delta = abilitiesDiff.get(abilityId);

            // 1. Structural changes (Added/Removed ability from the NPC fleet)
            if (delta instanceof JSONObject && ((JSONObject) delta).has("action")) {
                JSONObject obj = (JSONObject) delta;
                String action = obj.getString("action");

                if ("ADDED".equals(action)) {
                    fleet.addAbility(abilityId);
                    syncAbilityState(fleet, abilityId, obj.getJSONObject("value"));
                } else if ("REMOVED".equals(action)) {
                    fleet.removeAbility(abilityId);
                } else if ("UPDATE".equals(action)) {
                    syncAbilityState(fleet, abilityId, obj.getJSONObject("value"));
                }
            }
            // 2. Value changes (The 'active' toggle)
            else if (delta instanceof JSONObject) {
                syncAbilityState(fleet, abilityId, (JSONObject) delta);
            }
        }
    }

    private static void syncAbilityState(CampaignFleetAPI fleet, String abilityId, JSONObject changes) throws JSONException {
        if (!fleet.hasAbility(abilityId)) {
            fleet.addAbility(abilityId);
        }

        AbilityPlugin ability = fleet.getAbility(abilityId);
        if (ability == null) return;

        if (changes.has("active")) {
            Object activeObj = changes.get("active");
            boolean shouldBeActive;

            // Extracting from { "active": { "action": "UPDATE", "value": true } }
            if (activeObj instanceof JSONObject && ((JSONObject) activeObj).has("value")) {
                shouldBeActive = ((JSONObject) activeObj).getBoolean("value");
            }
            // Extracting from { "active": true }
            else {
                shouldBeActive = (activeObj instanceof Boolean) ? (Boolean)activeObj : false;
            }

            if (shouldBeActive && !ability.isActive()) {
                ability.activate();
            } else if (!shouldBeActive && ability.isActive()) {
                ability.deactivate();
            }
        }
    }

    public static JSONObject serializeFleet(CampaignFleetAPI fleet) throws JSONException {
        JSONObject serializedFleet = new JSONObject();
        serializedFleet.put("id", fleet.getId());

        // Coordinates and Location
        serializedFleet.put("locationX", ((int)(fleet.getLocation().getX() * 1000)) / 1000d);
        serializedFleet.put("locationY", ((int)(fleet.getLocation().getY() * 1000)) / 1000d);
        serializedFleet.put("location", fleet.getContainingLocation().getId());
        serializedFleet.put("factionId", fleet.getFaction().getId());
        serializedFleet.put("moveDestinationX", ((int)(fleet.getMoveDestination().getX() * 1000)) / 1000d);
        serializedFleet.put("moveDestinationY", ((int)(fleet.getMoveDestination().getY() * 1000)) / 1000d);
        serializedFleet.put("isPlayerFleet", fleet.isPlayerFleet());
        serializedFleet.put("isTransponderOn", fleet.isTransponderOn());
        serializedFleet.put("aiMode",fleet.isAIMode());
        serializedFleet.put("name",fleet.getName());

        // MAPS instead of ARRAYS
        serializedFleet.put("abilities", serializeAbilities(fleet.getAbilities()));

        // Cargo: keyed by Commodity ID
        serializedFleet.put("cargo", CargoSerializer.serializeCargoToMap(fleet.getCargo().getStacksCopy()));

        // Ships: keyed by FleetMember ID
        serializedFleet.put("ships", serializeFleetShips(fleet.getFleetData()));

        //if(fleet.getCurrentAssignment() != null){
        //    serializedFleet.put("assignment",serializeAssignment(fleet.getCurrentAssignment()));
        //}
        return serializedFleet;
    }

    /**
     * Replaces any weaponGroups diff (ADDED/REMOVED/UPDATE per group) with the full
     * weapon groups map from the new serialized fleet, so the receiver can clear-and-apply.
     * Call this on the fleet diff after getDifferences() and before sending.
     */
    public static void replaceWeaponGroupsDiffsWithFullState(JSONObject fleetDiff, JSONObject newSerializedFleet) {
        if (!fleetDiff.has("ships") || !newSerializedFleet.has("ships")) return;
        try {
            JSONObject shipsDiff = fleetDiff.getJSONObject("ships");
            JSONObject newShips = newSerializedFleet.getJSONObject("ships");
            Iterator<?> shipIds = shipsDiff.keys();
            while (shipIds.hasNext()) {
                String shipId = (String) shipIds.next();
                if (!newShips.has(shipId)) continue;
                JSONObject shipDiff = shipsDiff.optJSONObject(shipId);
                if (shipDiff == null || !shipDiff.has("weaponGroups")) continue;
                JSONObject fullWeaponGroups = newShips.getJSONObject(shipId).optJSONObject("weaponGroups");
                if (fullWeaponGroups != null) {
                    shipDiff.put("weaponGroups", fullWeaponGroups);
                }
            }
        } catch (JSONException ignored) { }
    }

    public static void unSerializeFleet(JSONObject serializedFleet, CampaignFleetAPI fleet) throws JSONException {
        fleet.setId(serializedFleet.getString("id"));
        fleet.setFaction(serializedFleet.getString("factionId"));
        fleet.setName(serializedFleet.getString("name"));
        if(Objects.equals(serializedFleet.getString("location"), "hyperspace")){
            fleet.setContainingLocation(Global.getSector().getHyperspace());
            Global.getSector().getHyperspace().addEntity(fleet); // the previous line doesn't actually spawn it in location
        }else{
            fleet.setContainingLocation(Global.getSector().getStarSystem(serializedFleet.getString("location")));
            Global.getSector().getStarSystem(serializedFleet.getString("location")).addEntity(fleet);
        }
        fleet.setLocation((float) serializedFleet.getDouble("locationX"),(float) serializedFleet.getDouble("locationY"));

        fleet.setMoveDestination((float) serializedFleet.getDouble("moveDestinationX"), (float) serializedFleet.getDouble("moveDestinationY"));

        fleet.setTransponderOn(serializedFleet.getBoolean("isTransponderOn"));

        // Unserialize mapped structures
        unSerializeAbilities(serializedFleet.getJSONObject("abilities"), fleet);

        CargoSerializer.addCargoFromMappedSerialized(serializedFleet.getJSONObject("cargo"), fleet.getCargo());

        unSerializeFleetMembers(serializedFleet.getJSONObject("ships"), fleet);

        //if(!serializedFleet.getBoolean("isPlayerFleet")){
        //    try {
        //        if(fleet.isAIMode() && !fleet.isPlayerFleet()){
        //            if(fleet.getAI() == null){
        //                fleet.setAI(new ModularFleetAI((CampaignFleet) fleet));
        //            }
//
        //            JSONObject assignment = serializedFleet.getJSONObject("assignment");
        //            SectorEntityToken target = Global.getSector().getEntityById(assignment.getString("target"));
        //            if(target != null){
        //                fleet.clearAssignments();
        //                fleet.inflateIfNeeded();
        //                fleet.getAI().addAssignment(FleetAssignment.valueOf(assignment.getString("assignment")), target, 1000f,null);
        //                fleet.getCurrentAssignment().setActionText(assignment.getString("text"));// no need to expire since will be replaced as soon as another update happens
        //            }
        //        }
//
        //    }catch (Exception e){
        //        System.out.println(e);
        //    }
        //}


    }

    public static JSONObject serializeAbilities(Map<String, AbilityPlugin> abilities) throws JSONException {
        JSONObject serializedMap = new JSONObject();
        for (AbilityPlugin ability : abilities.values()) {
            if (ability == null) continue;

            JSONObject data = new JSONObject();
            data.put("id", ability.getId()); // Explicitly keep ID
            data.put("active", ability.isActiveOrInProgress());
            serializedMap.put(ability.getId(), data);
        }
        return serializedMap;
    }

    public static void unSerializeAbilities(JSONObject abilitiesMap, CampaignFleetAPI fleet) throws JSONException {
        Iterator<?> keys = abilitiesMap.keys();
        while (keys.hasNext()) {
            String abilityId = (String) keys.next();
            JSONObject data = abilitiesMap.getJSONObject(abilityId);

            // NPC fleets don't "learn" abilities automatically. Force it.
            if (!fleet.hasAbility(abilityId)) {
                fleet.addAbility(abilityId);
            }

            AbilityPlugin ability = fleet.getAbility(abilityId);
            if (ability != null) {
                // Using optBoolean because 'active' might not exist in every partial sync
                boolean shouldBeActive = data.optBoolean("active", false);
                if (shouldBeActive) {
                    ability.activate();
                } else {
                    ability.deactivate();
                }
            }
        }
    }

    public static JSONObject serializeFleetShips(FleetDataAPI fleetData) throws JSONException {
        JSONObject shipsMap = new JSONObject();
        for (FleetMemberAPI ship : fleetData.getMembersListCopy()) {
            JSONObject shipSerialized = new JSONObject();

            String hullId = ship.getHullSpec().getHullId();
            if (hullId.contains("_default_")) {
                hullId = hullId.substring(0, hullId.indexOf("_default_"));
            }

            shipSerialized.put("id", ship.getId());
            shipSerialized.put("hull", hullId);
            shipSerialized.put("combatReadiness", ((int)(ship.getRepairTracker().getCR() * 1000)) / 1000d);
            shipSerialized.put("name", ship.getShipName());
            shipSerialized.put("isMothballed", ship.isMothballed());
            shipSerialized.put("fluxVents", ship.getVariant().getNumFluxVents());
            shipSerialized.put("fluxCapacitors", ship.getVariant().getNumFluxCapacitors());
            shipSerialized.put("isFlagShip", ship.isFlagship());

            ShipVariantAPI shipVariant = ship.getVariant();

            JSONObject hullModsMap = new JSONObject();
            for (String modId : shipVariant.getHullMods()) {
                hullModsMap.put(modId, true);
            }
            shipSerialized.put("hullMods", hullModsMap);

            // S-Mods (Permanent/Story Mods)
            JSONObject sModsMap = new JSONObject();
            for (String modId : shipVariant.getSMods()) {
                sModsMap.put(modId, true);
            }
            shipSerialized.put("sHullMods", sModsMap);

            JSONObject wingsMap = new JSONObject();
            List<String> wings = shipVariant.getFittedWings();
            for (int i = 0; i < wings.size(); i++) {
                if (wings.get(i) != null) wingsMap.put(String.valueOf(i), wings.get(i));
            }
            shipSerialized.put("fittedWings", wingsMap);

            // 2. Guns keyed by Slot ID (Already doing this, but confirming)
            JSONObject gunsMap = new JSONObject();
            for (String slotId : shipVariant.getFittedWeaponSlots()) {
                gunsMap.put(slotId, shipVariant.getWeaponId(slotId));
            }
            shipSerialized.put("fittedGuns", gunsMap);

            // 3. Weapon Groups keyed by group index
            JSONObject groupsMap = new JSONObject();
            List<WeaponGroupSpec> groups = shipVariant.getWeaponGroups();
            for (int i = 0; i < groups.size(); i++) {
                WeaponGroupSpec group = groups.get(i);
                JSONObject groupObj = new JSONObject();
                groupObj.put("type", group.getType().name());
                groupObj.put("autofire", group.isAutofireOnByDefault());
                groupObj.put("slots", new JSONArray(group.getSlots()));
                groupsMap.put(String.valueOf(i), groupObj);
            }
            shipSerialized.put("weaponGroups", groupsMap);
            shipSerialized.put("captain", PersonsSerializer.serializePerson(ship.getCaptain()));

            shipsMap.put(ship.getId(), shipSerialized);
        }
        return shipsMap;
    }

    public static FleetMemberAPI unSerializeFleetMember(JSONObject shipObject) throws JSONException {
        FleetMemberAPI ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, shipObject.getString("hull") + "_Hull");
        ShipVariantAPI variant = ship.getVariant();
        ship.getRepairTracker().setCR((float) shipObject.getDouble("combatReadiness"));
        ship.setShipName(shipObject.optString("name", ""));
        ship.getRepairTracker().setMothballed(shipObject.getBoolean("isMothballed"));
        variant.setNumFluxVents(shipObject.getInt("fluxVents"));
        variant.setNumFluxCapacitors(shipObject.getInt("fluxCapacitors"));
        ship.setFlagship(shipObject.getBoolean("isFlagShip"));

// 1. Unserialize Hull Mods (Now a Map)
        JSONObject hullMods = shipObject.getJSONObject("hullMods");
        Iterator<?> modKeys = hullMods.keys();
        while (modKeys.hasNext()) {
            variant.addMod((String) modKeys.next());
        }

        // 2. Unserialize S-Mods (Now a Map)
        JSONObject sHullMods = shipObject.getJSONObject("sHullMods");
        Iterator<?> sModKeys = sHullMods.keys();
        while (sModKeys.hasNext()) {
            variant.addPermaMod((String) sModKeys.next(), true);
        }

        JSONObject wings = shipObject.getJSONObject("fittedWings");
        for (Iterator<?> it = wings.keys(); it.hasNext(); ) {
            String key = (String) it.next();
            variant.setWingId(Integer.parseInt(key), wings.getString(key));
        }

        JSONObject fittedGuns = shipObject.getJSONObject("fittedGuns");
        Iterator<?> gunKeys = fittedGuns.keys();
        while (gunKeys.hasNext()) {
            String slotId = (String) gunKeys.next();
            variant.addWeapon(slotId, fittedGuns.getString(slotId));
        }

        JSONObject groups = shipObject.getJSONObject("weaponGroups");
        ship.getVariant().setMayAutoAssignWeapons(false);
        variant.getWeaponGroups().clear(); // Clear defaults before applying sync
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.getJSONObject(String.valueOf(i));
            WeaponGroupSpec spec = new WeaponGroupSpec(WeaponGroupType.valueOf(g.getString("type")));
            spec.setAutofireOnByDefault(g.getBoolean("autofire"));
            JSONArray slots = g.getJSONArray("slots");
            for (int j = 0; j < slots.length(); j++) spec.addSlot(slots.getString(j));
            variant.addWeaponGroup(spec);
        }
        ship.setCaptain(PersonsSerializer.unSerializePerson(shipObject.getJSONObject("captain")));
        return ship;
    }

    public static void unSerializeFleetMembers(JSONObject shipsMap, CampaignFleetAPI fleet) throws JSONException {
        Iterator<?> keys = shipsMap.keys();
        while (keys.hasNext()) {
            String shipId = (String) keys.next();
            JSONObject shipObject = shipsMap.getJSONObject(shipId);
            FleetMemberAPI member = unSerializeFleetMember(shipObject);
            member.setId(shipId);
            fleet.getFleetData().addFleetMember(member);
            if(shipObject.getBoolean("isFlagShip")){
                fleet.setCommander(member.getCaptain());
                fleet.getFleetData().setFlagship(member);
            }
        }
    }

    public static void clearFleetMembers(CampaignFleetAPI fleet) {
        for (FleetMemberAPI ship : fleet.getFleetData().getMembersListWithFightersCopy()) {
            fleet.getFleetData().removeFleetMember(ship);
        }
    }

    private static void patchHullMods(ShipVariantAPI variant, JSONObject diff, boolean isSMod) throws JSONException {
        Iterator<?> keys = diff.keys();
        while (keys.hasNext()) {
            String modId = (String) keys.next();
            Object delta = diff.get(modId);

            if (delta instanceof JSONObject instruction && instruction.has("action")) {
                String action = instruction.getString("action");

                if ("ADDED".equals(action)) {
                    if (isSMod) {
                        variant.addPermaMod(modId, true); // true = counts as S-Mod
                    } else {
                        variant.addMod(modId);
                    }
                } else if ("REMOVED".equals(action)) {
                    variant.removeMod(modId);
                    variant.removePermaMod(modId);
                }
            }
        }
    }

    private static void patchFittedGuns(ShipVariantAPI variant, JSONObject diff) throws JSONException {
        Iterator<?> keys = diff.keys();
        while (keys.hasNext()) {
            String slotId = (String) keys.next();
            Object delta = diff.get(slotId);
            if (delta instanceof JSONObject instruction) {
                String action = instruction.getString("action");
                if ("REMOVED".equals(action)) {
                    variant.clearSlot(slotId);
                } else {
                    variant.addWeapon(slotId, instruction.getString("value"));
                }
            }
        }
    }

    private static void patchFittedWings(ShipVariantAPI variant, JSONObject diff) throws JSONException {
        Iterator<?> keys = diff.keys();
        while (keys.hasNext()) {
            String indexStr = (String) keys.next();
            int index = Integer.parseInt(indexStr);
            Object delta = diff.get(indexStr);
            if (delta instanceof JSONObject instruction) {
                String action = instruction.getString("action");
                variant.setWingId(index, "REMOVED".equals(action) ? null : instruction.getString("value"));
            }
        }
    }

    /**
     * Replaces all weapon groups with the full state from the given map.
     * Expects the same format as serialization: keys "0", "1", "2", ... and each value
     * is { "type", "autofire", "slots" }. Clears existing groups then applies in index order.
     */
    private static void patchWeaponGroups(ShipVariantAPI variant, JSONObject fullWeaponGroups) throws JSONException {
        List<WeaponGroupSpec> currentGroups = variant.getWeaponGroups();
        currentGroups.clear();

        List<Integer> indices = new ArrayList<>();
        Iterator<?> keyIt = fullWeaponGroups.keys();
        while (keyIt.hasNext()) {
            String indexStr = (String) keyIt.next();
            indices.add(Integer.parseInt(indexStr));
        }
        Collections.sort(indices);

        for (int index : indices) {
            String indexStr = String.valueOf(index);
            Object raw = fullWeaponGroups.opt(indexStr);
            if (!(raw instanceof JSONObject groupData) || !groupData.has("type") || !groupData.has("slots")) {
                continue;
            }
            WeaponGroupSpec spec = new WeaponGroupSpec();
            spec.setType(WeaponGroupType.valueOf(groupData.getString("type")));
            spec.setAutofireOnByDefault(groupData.optBoolean("autofire", false));
            JSONArray slots = groupData.getJSONArray("slots");
            for (int i = 0; i < slots.length(); i++) {
                spec.addSlot(slots.getString(i));
            }
            variant.addWeaponGroup(spec);
        }
    }

    private static JSONObject serializeAssignment(FleetAssignmentDataAPI assignment) throws JSONException{
        JSONObject serializedAssignments = new JSONObject();
        serializedAssignments.put("target",assignment.getTarget().getId());
        serializedAssignments.put("assignment",assignment.getAssignment().name());
        serializedAssignments.put("text",assignment.getActionText());
        return serializedAssignments;
    }
}