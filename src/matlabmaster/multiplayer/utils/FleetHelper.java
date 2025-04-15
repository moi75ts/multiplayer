package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class FleetHelper {
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
}