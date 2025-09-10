package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.AICoreOfficerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;
import matlabmaster.multiplayer.MessageSender;
import matlabmaster.multiplayer.MultiplayerModPlugin;
import matlabmaster.multiplayer.User;
import matlabmaster.multiplayer.events.SystemEntryScript;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.Sys;


import java.security.NoSuchAlgorithmException;
import java.util.*;

public class FleetHelper {
    private static Map<String, Map<String, String>> trackedFleets = new HashMap<>();

    // Method to add or update a fleet entry
    static public void addOrUpdateTrackedFleet(String id, String shipsHash, String officersHash, String commanderHash, String cargoHash) {
        Map<String, String> fleet = new HashMap<>();
        fleet.put("id", id);
        fleet.put("shipsHash", shipsHash);
        fleet.put("officersHash", officersHash);
        fleet.put("commanderHash", commanderHash);
        fleet.put("cargoHash", cargoHash);
        trackedFleets.put(id, fleet);
    }

    // Method to get fleet data by ID
    static public Map<String, String> getTrackedFleetById(String id) {
        return trackedFleets.get(id); // Returns null if ID not found
    }

    // Method to check if an ID exists
    static public boolean hasTrackedFleet(String id) {
        return trackedFleets.containsKey(id);
    }

    // Method to get all fleets
    static public List<Map<String, String>> getAllTrackedFleets() {
        return new ArrayList<>(trackedFleets.values());
    }


    public static JSONObject serializeFleet(CampaignFleetAPI fleet) throws JSONException, NoSuchAlgorithmException {
        JSONObject serializedFleet = new JSONObject();
        serializedFleet.put("id",fleet.getId());
        serializedFleet.put("locationX",fleet.getLocation().getX());
        serializedFleet.put("locationY",fleet.getLocation().getY());
        serializedFleet.put("location",Global.getSector().getCurrentLocation().getName());
        serializedFleet.put("factionId",fleet.getFaction().getId());
        serializedFleet.put("acceleration",fleet.getAcceleration());
        try{
            serializedFleet.put("currentAssignment",fleet.getCurrentAssignment().getActionText());
            serializedFleet.put("currentAssignmentTargetId",fleet.getCurrentAssignment().getTarget().getId());
        }catch (Exception e){
            //do nothing no Assignment
            //occurs when players fleet
        }

        serializedFleet.put("moveDestinationX",fleet.getMoveDestination().getX());
        serializedFleet.put("moveDestinationY",fleet.getMoveDestination().getY());
        serializedFleet.put("isPlayerFleet",fleet.isPlayerFleet());
        serializedFleet.put("isTransponderOn",fleet.isTransponderOn());
        serializedFleet.put("maxBurnLevel",fleet.getFleetData().getBurnLevel());

        serializedFleet.put("abilities", serializeAbilities(fleet.getAbilities()));// no need for hash, apply

        JSONArray serializedOfficers = PersonsHelper.serializePersons(PersonsHelper.extractPersonsFromOfficers(fleet.getFleetData().getOfficersCopy()));
        serializedFleet.put("officers", serializedOfficers);
        serializedFleet.put("officersHash", HashHelper.hashJsonArray(serializedOfficers));

        JSONObject serializedCommander = PersonsHelper.serializePerson(fleet.getCommander());
        serializedFleet.put("commander", serializedCommander);
        serializedFleet.put("commanderHash", HashHelper.hashJsonObject(serializedCommander));


        JSONArray serializedCargo = CargoHelper.serializeCargo(fleet.getCargo().getStacksCopy());
        serializedFleet.put("cargo",serializedCargo);
        serializedFleet.put("cargoHash", HashHelper.hashJsonArray(serializedCargo));

        JSONArray serializedShips = serializeFleetShips(fleet.getFleetData());
        serializedFleet.put("ships", serializedShips);
        serializedFleet.put("shipsHash", hashFleetWithoutCombatReadiness(serializedShips));

        //Combat readiness change constantly when a ship is recuperating
        //which means that every single tick the fleet is destroyed and remade to update one ship CR
        //So I excluded it from the hash,
        //the fix would be instead of deleting then remaking the fleet everytime the fleet changes, to only update what needs updating using existing ships
        //todo fix it
        return serializedFleet;
    }


    /**
     * Unserializes a fleet from a JSONArray
     * @param serializedFleet JSONArray containing serialized ship data
     * @param fleet The fleet unSerialize (give it an empty fleet)
     * @throws JSONException If JSON parsing fails
     */
    public static void unSerializeFleet(JSONObject serializedFleet, CampaignFleetAPI fleet, boolean moveDestination) throws JSONException {
        Map<String,String> trackedData;
        fleet.setId(serializedFleet.getString("id"));
        float remoteX = (float)serializedFleet.getDouble("locationX");
        float remoteY = (float) serializedFleet.getDouble("locationY");
        float localX  = fleet.getLocation().getX();
        float localY = fleet.getLocation().getY();
        float deltaX = remoteX - localX;
        float deltaY = remoteY - localY;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        //rubberbanding is caused by incorrect acceleration values, I tried to fix it but failed
        if(distance > 200){ //fleet are too far appart, hard tp to remote location
            fleet.setLocation(remoteX, remoteY);
        }
        if(moveDestination){
            fleet.setMoveDestination((float) serializedFleet.getDouble("moveDestinationX"), (float) serializedFleet.getDouble("moveDestinationY"));
        }


        if(!Objects.equals(serializedFleet.getString("factionId"), "player")){
            fleet.setFaction(serializedFleet.getString("factionId"));//it is the fleet of another player
        }else{
            fleet.setFaction("neutral");
        }
        fleet.setTransponderOn(serializedFleet.getBoolean("isTransponderOn"));
        //todo assignments

        // Unserialize abilities
        JSONArray abilities = serializedFleet.getJSONArray("abilities");
        unSerializeAbilities(abilities, fleet);

        if(hasTrackedFleet(serializedFleet.getString("id"))){
            //fleet exists and is tracked (tracking is used for updating the fleet)
            trackedData = getTrackedFleetById(serializedFleet.getString("id"));
            if(!Objects.equals(serializedFleet.getString("officersHash"), trackedData.get("officersHash"))){
                PersonsHelper.unSerializePersons(serializedFleet.getJSONArray("officers"));
            }
            if(!Objects.equals(serializedFleet.getString("commanderHash"), trackedData.get("commanderHash"))){
                PersonsHelper.unSerializePerson(serializedFleet.getJSONObject("commander"));
            }
            if(!Objects.equals(serializedFleet.getString("cargoHash"), trackedData.get("cargoHash"))){
                fleet.getCargo().clear();
                CargoHelper.addCargoFromSerialized(serializedFleet.getJSONArray("cargo"), fleet.getCargo());
            }

            if(!Objects.equals(serializedFleet.getString("shipsHash"), trackedData.get("shipsHash"))){
                clearFleetMembers(fleet);
                JSONArray ships = serializedFleet.getJSONArray("ships");
                unSerializeFleetMember(ships, fleet);
            }

            addOrUpdateTrackedFleet(
                    serializedFleet.getString("id"),
                    serializedFleet.getString("shipsHash"),
                    serializedFleet.getString("officersHash"),
                    serializedFleet.getString("commanderHash"),
                    serializedFleet.getString("cargoHash")
            );
        }else{
            //fleet is not tracked, usually at spawn
            PersonsHelper.unSerializePersons(serializedFleet.getJSONArray("officers"));
            PersonsHelper.unSerializePerson(serializedFleet.getJSONObject("commander"));
            fleet.getCargo().clear();
            CargoHelper.addCargoFromSerialized(serializedFleet.getJSONArray("cargo"), fleet.getCargo());
            clearFleetMembers(fleet);
            JSONArray ships = serializedFleet.getJSONArray("ships");
            unSerializeFleetMember(ships, fleet);

            addOrUpdateTrackedFleet(
                    serializedFleet.getString("id"),
                    serializedFleet.getString("shipsHash"),
                    serializedFleet.getString("officersHash"),
                    serializedFleet.getString("commanderHash"),
                    serializedFleet.getString("cargoHash")
            );
        }

        //the burn code was birthed in pain
        //the base fleet burn is 4, 8 if ability is used
        //there is no setBurn method
        //So I just take the local and remote burn value, compare them
        //it goes
        //remoteMaxBurn-localMaxBurn how much the burn level should increase / decrease by
        //+ localMaxBurn the current burn level
        //-minus the base value (baseFleetBurn)
        //the value is then fed into fleet.getStats().getFleetwideMaxBurnMod().modifyFlat("sync",burnBonus); which sets the correct burn level
        float remoteMaxBurn = (float) serializedFleet.getDouble("maxBurnLevel");
        float localMaxBurn = fleet.getFleetData().getBurnLevel();
        float baseFleetBurn = 4;
        if(isAbilityBeingUsed("sustained_burn",fleet) || isAbilityBeingUsed("emergency_burn",fleet)){
            baseFleetBurn = 8;
        }
        float burnBonus = remoteMaxBurn-localMaxBurn + localMaxBurn - baseFleetBurn;
        fleet.getStats().getFleetwideMaxBurnMod().modifyFlat("sync",burnBonus);
        //sometime the fleet will generate empty, and thus will be instantly deleted
        //but the hash will be correct and thus not updating the fleet, so until the hash changes, an empty fleet will keep spawning and despawning
        //so this is a quick fix to prevent this issue
        if(fleet.getFleetData().getNumMembers() == 0){
            addOrUpdateTrackedFleet(
                    serializedFleet.getString("id"),
                    "needUpdate",
                    "needUpdate",
                    "needUpdate",
                    "needUpdate"
            );
        }
    }

    public static JSONArray serializeAbilities(Map<String, AbilityPlugin> abilities) throws JSONException {
        JSONArray serializedAbilities = new JSONArray();
        for(AbilityPlugin ability: abilities.values()){
            JSONObject serializedAbility = new JSONObject();
            if(!Objects.equals(ability.getId(), "transponder")){
                serializedAbility.put("abilityId",ability.getId());
                serializedAbility.put("abilityActive",ability.isActive());//used for continous abilities ei, go dark, sustained burn, transponder
                serializedAbility.put("abilityInProgress",ability.isInProgress());//used for one time then cooldown ie, emergency burn, interdiction pulse ...
                serializedAbilities.put(serializedAbility);
            }
        }
        return serializedAbilities;
    }

    public static void unSerializeAbilities(JSONArray abilitiesArray, CampaignFleetAPI fleet) throws JSONException {
        for(int i = 0; i<abilitiesArray.length() ;i++){
            JSONObject abilityObject = abilitiesArray.getJSONObject(i);
            AbilityPlugin ability = fleet.getAbility(abilityObject.getString("abilityId"));
            if(ability == null){
                fleet.addAbility(abilityObject.getString("abilityId"));
            }else{
                if(abilityObject.getBoolean("abilityActive") && !ability.isActive()){
                    ability.activate();
                } else if (abilityObject.getBoolean("abilityInProgress") && !ability.isActiveOrInProgress()){
                    ability.activate();
                }else if(ability.isActiveOrInProgress() && !abilityObject.getBoolean("abilityInProgress")){
                    ability.deactivate();
                }else if(ability.isActive() && !abilityObject.getBoolean("abilityActive")){
                    ability.deactivate();
                }
            }
        }
    }


    public static JSONArray serializeFleetShips(FleetDataAPI fleet) throws JSONException {
        JSONArray serializedFleet = new JSONArray();
        for(FleetMemberAPI ship : fleet.getMembersListWithFightersCopy()){
            JSONObject shipSerialized = new JSONObject();
            //todo ask on forums for fix for starting spec id
            //the regex is a hack to have it work
            //todo also the regex breaks some more complicated variants, D A variants transforming it into a D variant
            //I had a beautiful code using getSpecId, until for some reason i realised that the starting ships have a weird specId, and if i use baseHull, i kill all the variants
            //weird spec id instead of apogee_Hull, the spec id for the starting apogee is 9f38
            String hullId = ship.getHullSpec().getHullId();
            // Remove variant suffix if present (like "_default_D")
            if (hullId.contains("_default_")) {
                hullId = hullId.substring(0, hullId.indexOf("_default_"));
            }
            shipSerialized.put("hull", hullId);
            shipSerialized.put("combatReadiness",ship.getRepairTracker().getCR());
            shipSerialized.put("name",ship.getShipName());
            shipSerialized.put("isMothballed",ship.isMothballed());
            shipSerialized.put("fluxVents",ship.getVariant().getNumFluxVents());
            shipSerialized.put("fluxCapacitors",ship.getVariant().getNumFluxCapacitors());
            shipSerialized.put("isFlagShip",ship.isFlagship());
            ShipVariantAPI shipVariant = ship.getVariant();

            JSONArray hullModsArray = new JSONArray();
            Collection<String> hullMods = shipVariant.getHullMods();
            for(String hullMod : hullMods){
                hullModsArray.put(hullMod);
            }
            shipSerialized.put("hullMods",hullMods);

            JSONArray sHullModsArray = new JSONArray();
            Set<String> sHullMods= shipVariant.getSMods();
            for(String sHullMod : sHullMods){
                sHullModsArray.put(sHullMod);
            }
            shipSerialized.put("sHullMods",sHullMods);

            JSONArray fittedWingsArray = new JSONArray();
            List<String> fittedWings = shipVariant.getFittedWings();
            for(String fittedWing : fittedWings){
                fittedWingsArray.put(fittedWing);
            }
            shipSerialized.put("fittedWings",fittedWingsArray);

            JSONArray fittedGunsArray = new JSONArray();
            for(String gunSlot : shipVariant.getFittedWeaponSlots()){
                JSONObject gunSlotObject = new JSONObject();
                gunSlotObject.put("slotId",gunSlot);
                gunSlotObject.put("gunId",shipVariant.getWeaponId(gunSlot));
                fittedGunsArray.put(gunSlotObject);
            }
            shipSerialized.put("fittedGuns",fittedGunsArray);
            JSONArray weaponGroupsArray = new JSONArray();
            for (WeaponGroupSpec weaponGroup : shipVariant.getWeaponGroups()) {
                JSONObject weaponGroupObject = new JSONObject();
                weaponGroupObject.put("type", weaponGroup.getType());
                weaponGroupObject.put("autofire", weaponGroup.isAutofireOnByDefault());
                JSONArray slots = new JSONArray();
                for (String slotId : weaponGroup.getSlots()) {
                    slots.put(slotId);
                }
                weaponGroupObject.put("slots", slots);
                weaponGroupsArray.put(weaponGroupObject);
            }
            shipSerialized.put("weaponGroups", weaponGroupsArray);

            shipSerialized.put("captain", PersonsHelper.serializePerson(ship.getCaptain()));
            serializedFleet.put(shipSerialized);
        }
        return serializedFleet;
    }

    public static FleetMemberAPI unSerializeFleetMember(JSONObject shipObject) throws JSONException {
        int i;
        FleetMemberAPI ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP,shipObject.getString("hull")+"_Hull");
        ship.getRepairTracker().setCR((float) shipObject.getDouble("combatReadiness"));
        try{
            ship.setShipName(shipObject.getString("name"));
        }catch (Exception e){
            ship.setShipName("");
        }
        ship.getRepairTracker().setMothballed(shipObject.getBoolean("isMothballed"));
        ship.getVariant().setNumFluxVents(shipObject.getInt("fluxVents"));
        ship.getVariant().setNumFluxCapacitors(shipObject.getInt("fluxCapacitors"));
        ship.setFlagship(shipObject.getBoolean("isFlagShip"));

        JSONArray hullMods = shipObject.getJSONArray("hullMods");
        for(i = 0 ; i < hullMods.length() ; i++){
            ship.getVariant().addMod(hullMods.getString(i));
        }

        JSONArray sHullMods = shipObject.getJSONArray("sHullMods");
        for(i = 0 ; i < sHullMods.length() ; i++){
            ship.getVariant().addPermaMod(sHullMods.getString(i),true);
        }

        JSONArray fittedWings = shipObject.getJSONArray("fittedWings");
        for(i = 0; i < fittedWings.length() ; i++){
            ship.getVariant().setWingId(i, fittedWings.getString(i));
        }

        JSONArray fittedGuns = shipObject.getJSONArray("fittedGuns");
        for(i = 0; i < fittedGuns.length() ; i++){
            JSONObject fittedGun = fittedGuns.getJSONObject(i);
            ship.getVariant().addWeapon(fittedGun.getString("slotId"),fittedGun.getString("gunId"));
        }
        JSONArray weaponGroups = shipObject.getJSONArray("weaponGroups");
        for (i = 0; i < weaponGroups.length(); i++) {
            JSONObject weaponGroupObject = weaponGroups.getJSONObject(i);
            WeaponGroupSpec weaponGroup = new WeaponGroupSpec();
            weaponGroup.setType(WeaponGroupType.valueOf(weaponGroupObject.getString("type")));
            weaponGroup.setAutofireOnByDefault(weaponGroupObject.getBoolean("autofire"));
            for (int j = 0; j < weaponGroupObject.getJSONArray("slots").length(); j++) {
                weaponGroup.addSlot(weaponGroupObject.getJSONArray("slots").getString(j));
            }
            ship.getVariant().addWeaponGroup(weaponGroup);
        }
        ship.setCaptain(PersonsHelper.unSerializePerson(shipObject.getJSONObject("captain")));
        return ship;
    }

    /**
     * Unserializes multiple fleet members from a JSONArray and adds them to the fleet
     * @param ships JSONArray containing serialized ship data
     * @param fleet The fleet to add the ships to
     * @throws JSONException If JSON parsing fails
     */
    public static void unSerializeFleetMember(JSONArray ships, CampaignFleetAPI fleet) throws JSONException {
        for (int i = 0; i < ships.length(); i++) {
            JSONObject shipObject = ships.getJSONObject(i);
            FleetMemberAPI member = unSerializeFleetMember(shipObject);
            fleet.getFleetData().addFleetMember(member);
        }
    }

    public static void fleetSpawned(FleetDataAPI fleet){

    }

    public static void clearFleetMembers(CampaignFleetAPI fleet){
        for(FleetMemberAPI ship : fleet.getFleetData().getMembersListWithFightersCopy()){
            fleet.getFleetData().removeFleetMember(ship);
        }
    }

    public static String hashFleetWithoutCombatReadiness(JSONArray serializedShips) throws JSONException, NoSuchAlgorithmException {
        JSONArray modifiedShips = new JSONArray();
        for (int i = 0; i < serializedShips.length(); i++) {
            JSONObject ship = serializedShips.getJSONObject(i);
            JSONObject modifiedShip = new JSONObject(ship.toString()); // Deep copy
            modifiedShip.remove("combatReadiness"); // Remove the key
            modifiedShips.put(modifiedShip);
        }
        return HashHelper.hashJsonArray(modifiedShips);
    }

    public static Boolean isAbilityBeingUsed(String abilityID,CampaignFleetAPI fleet){
        return fleet.getAbility(abilityID).isActiveOrInProgress();
    }

    public static void sendSpawnedFleet(CampaignFleetAPI fleet) throws JSONException, NoSuchAlgorithmException {
        JSONObject message = new JSONObject();
        message.put("command", 9);
        message.put("playerId", User.getUserId());
        message.put("fleet",serializeFleet(fleet));
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        sender.sendMessage(message.toString());
    }

    public static void spawnNewFleet(JSONObject message) throws JSONException {
        JSONObject serializedFleet = message.getJSONObject("fleet");
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(serializedFleet.getString("factionId"),"grou grou gabi",true);
        unSerializeFleet(serializedFleet,fleet,true);
    }
}