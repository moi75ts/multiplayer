package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.abilities.TransponderAbility;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.print.Book;
import java.util.*;

public class FleetHelper {
    public static JSONObject serializeFleet(CampaignFleetAPI fleet) throws JSONException {
        JSONObject serializedFleet = new JSONObject();
        serializedFleet.put("id",fleet.getId());
        serializedFleet.put("locationX",fleet.getLocation().getX());
        serializedFleet.put("locationY",fleet.getLocation().getY());
        serializedFleet.put("location",Global.getSector().getCurrentLocation());
        serializedFleet.put("factionId",fleet.getFaction().getId());
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

        serializedFleet.put("abilities", serializeAbilities(fleet.getAbilities()));
        serializedFleet.put("cargo",CargoHelper.serializeCargo(fleet.getCargo().getStacksCopy()));
        serializedFleet.put("ships",serializeFleetShips(fleet.getFleetData()));
        //todo add persons ie commanders
        return serializedFleet;
    }


    /**
     * Unserializes a fleet from a JSONArray
     * @param serializedFleet JSONArray containing serialized ship data
     * @param fleet The fleet unSerialize (give it an empty fleet)
     * @throws JSONException If JSON parsing fails
     */
    public static void unSerializeFleet(JSONObject serializedFleet, CampaignFleetAPI fleet, boolean moveDestination) throws JSONException {
        fleet.setId(serializedFleet.getString("id"));
        fleet.getLocation().set((float)serializedFleet.getDouble("locationX"), (float) serializedFleet.getDouble("locationY"));
        fleet.setFaction(serializedFleet.getString("factionId"));
        if(moveDestination){
            fleet.setMoveDestination((float) serializedFleet.getDouble("moveDestinationX"), (float) serializedFleet.getDouble("moveDestinationY"));
        }
        fleet.setTransponderOn(serializedFleet.getBoolean("isTransponderOn"));
        //todo assignments

        // Unserialize abilities
        JSONArray abilities = serializedFleet.getJSONArray("abilities");
        unSerializeAbilities(abilities, fleet);

        // Unserialize cargo
        fleet.getCargo().clear();
        CargoHelper.addCargoFromSerialized(serializedFleet.getJSONArray("cargo"), fleet.getCargo());

        // Unserialize ships
        JSONArray ships = serializedFleet.getJSONArray("ships");
        unSerializeFleetMember(ships, fleet);
        
        //todo add persons ie commanders when implemented in serialize
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
            String hullId = ship.getHullSpec().getHullId();
            // Remove variant suffix if present (like "_default_D")
            if (hullId.contains("_default_")) {
                hullId = hullId.substring(0, hullId.indexOf("_default_"));
            }
            //todo please fix fucked specId for starting ships
            //todo also the regex breaks some more complicated variants, D A variants transforming it into a D variant
            //I had a beautiful code using getSpecId, until for some reason i realised that the starting ships have a fucked up specId, and if i use baseHull, i kill all the variants
            shipSerialized.put("hull", hullId);
            shipSerialized.put("combatReadiness",ship.getRepairTracker().getCR());
            shipSerialized.put("name",ship.getShipName());
            shipSerialized.put("isMothballed",ship.isMothballed());
            shipSerialized.put("fluxVents",ship.getVariant().getNumFluxVents());
            shipSerialized.put("fluxCapacitors",ship.getVariant().getNumFluxCapacitors());
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

}