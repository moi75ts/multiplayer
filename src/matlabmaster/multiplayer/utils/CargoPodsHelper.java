package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CargoPodsHelper {
    public static List<String> hasEverSeenPod = new ArrayList<>();

    public static JSONArray serializeCargo(List<CargoStackAPI> cargoStackList) throws JSONException {
        JSONArray cargoStacks = new JSONArray();
        for (CargoStackAPI cargo : cargoStackList) {
            JSONObject cargoObject = new JSONObject();
            cargoObject.put("quantity", cargo.getSize());
            cargoObject.put("type", cargo.getType());
            if (Objects.equals(cargo.getType().toString(), "RESOURCES")) {
                cargoObject.put("commodityId", cargo.getCommodityId());
            } else if (Objects.equals(cargo.getType().toString(), "WEAPONS")) {
                cargoObject.put("weaponId", cargo.getData().toString());
            } else if (Objects.equals(cargo.getType().toString(), "SPECIAL")) {
                cargoObject.put("specialId", cargo.getSpecialDataIfSpecial().getId());
                cargoObject.put("specialData", cargo.getSpecialDataIfSpecial().getData());
            }
            cargoStacks.put(cargoObject);
        }
        return cargoStacks;
    }

    public static List<SectorEntityToken> getAllCargoPods() {
        List<SectorEntityToken> allCargoPods = new ArrayList<>();

        // Check all star systems
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken entity : system.getAllEntities()) {
                if (Objects.equals(entity.getCustomEntityType(), "cargo_pods")) {
                    CargoAPI cargo = entity.getCargo();
                    allCargoPods.add(entity);
                }
            }
        }

        // Check hyperspace
        for (SectorEntityToken entity : Global.getSector().getHyperspace().getAllEntities()) {
            if (Objects.equals(entity.getCustomEntityType(), "cargo_pods")) {
                CargoAPI cargo = entity.getCargo();
                allCargoPods.add(entity);
            }
        }
        return allCargoPods;
    }

    public static void removeCargoPod(String cargoPodId) {
        SectorEntityToken entity = Global.getSector().getEntityById(cargoPodId);
        entity.getContainingLocation().removeEntity(entity);
    }

    public static void addCargoFromSerialized(JSONArray serializedCargo, SectorEntityToken cargoPod) throws JSONException {
        int i;
        for (i = 0; i < serializedCargo.length(); i++) {
            JSONObject cargoToAdd = serializedCargo.getJSONObject(i);
            if (Objects.equals(cargoToAdd.getString("type"), "RESOURCES")) {
                cargoPod.getCargo().addCommodity(cargoToAdd.getString("commodityId"), cargoToAdd.getInt("quantity"));
            } else if (Objects.equals(cargoToAdd.getString("type"), "WEAPONS")) {
                cargoPod.getCargo().addWeapons(cargoToAdd.getString("weaponId"), cargoToAdd.getInt("quantity"));
            } else if (Objects.equals(cargoToAdd.getString("type"), "SPECIAL")) {
                String specialData;
                try {
                    specialData = cargoToAdd.getString("specialData");
                } catch (NullPointerException e) {
                    specialData = "";
                }
                SpecialItemData specItemData = new SpecialItemData(cargoToAdd.getString("specialId"), specialData);
            }
        }
    }

    public static void updateLocalPods(JSONObject message) throws JSONException {
        JSONArray remoteCargoPods = message.getJSONArray("cargoPods");
        JSONArray allCargoPodsEverSpawnedRemote = message.getJSONArray("allCargoPodsEverSpawned");
        List<String> allCargoPodsEverSpawnedRemoteList = new ArrayList<>();
        List<SectorEntityToken> localCargoPods = CargoPodsHelper.getAllCargoPods();
        List<JSONObject> toUpdate = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        List<JSONObject> toCreate = new ArrayList<>();
        Boolean anythingToDo = false;
        int i;

        for (i = 0; i < allCargoPodsEverSpawnedRemote.length(); i++) {
            allCargoPodsEverSpawnedRemoteList.add(allCargoPodsEverSpawnedRemote.getString(i));
        }
        // Check local pods against remote
        for (SectorEntityToken localPod : localCargoPods) {
            if (!hasEverSeenPod.contains(localPod.getId())) {
                hasEverSeenPod.add(localPod.getId());//add the id of our recently spawned cargopod
            }
            boolean found = false;
            for (i = 0; i < remoteCargoPods.length(); i++) {
                JSONObject remotePod = remoteCargoPods.getJSONObject(i);
                if (Objects.equals(localPod.getId(), remotePod.getString("entityId"))) {
                    found = true;
                    // Compare cargo stacks
                    JSONArray remoteStacks = remotePod.getJSONArray("serializedCargo");
                    JSONArray localStacks = CargoPodsHelper.serializeCargo(localPod.getCargo().getStacksCopy());
                    if (!remoteStacks.toString().equals(localStacks.toString())) {
                        toUpdate.add(remotePod); // Store full remote pod data for upgrade
                        anythingToDo = true;
                    }
                    break;
                }
            }
            if (!found) {//in local but not in remote
                if (allCargoPodsEverSpawnedRemoteList.contains(localPod.getId())) {//meaning the cargo spawn was never spawned in, and should not be removed
                    toRemove.add(localPod.getId());//fixes race condition
                    anythingToDo = true;
                }
            }
        }

        // Check remote pods against local to find new ones
        for (i = 0; i < remoteCargoPods.length(); i++) {
            JSONObject remotePod = remoteCargoPods.getJSONObject(i);
            boolean exists = false;
            for (SectorEntityToken localPod : localCargoPods) {
                if (Objects.equals(localPod.getId(), remotePod.getString("entityId"))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                if (!hasEverSeenPod.contains(remotePod.getString("entityId"))) {//meaning the cargo spawn was spawned in then removed, and should not be recreated
                    toCreate.add(remotePod);
                    anythingToDo = true;
                }
            }
            //process upgrade remove and create on server side to keep track

            if (anythingToDo) {
                for (String cargoPod : toRemove) {
                    CargoPodsHelper.removeCargoPod(cargoPod);
                }


                for (JSONObject newPod : toCreate) {
                    SectorEntityToken cargoPod;
                    try {
                        String systemId = newPod.getString("system");
                        StarSystemAPI system = Global.getSector().getStarSystem(systemId);
                        cargoPod = system.addCustomEntity(newPod.getString("entityId"), message.getString("playerId") + " pods", "cargo_pods", "neutral");
                    } catch (Exception e) {
                        LocationAPI hyperspace = Global.getSector().getHyperspace();
                        cargoPod = hyperspace.addCustomEntity(newPod.getString("entityId"), message.getString("playerId") + " pods", "cargo_pods", "neutral");
                    }
                    cargoPod.setLocation((float) newPod.getDouble("locationX"), (float) newPod.getDouble("locationY"));
                    JSONArray serializedCargo = newPod.getJSONArray("serializedCargo");
                    CargoPodsHelper.addCargoFromSerialized(serializedCargo, cargoPod);
                    hasEverSeenPod.add(cargoPod.getId());
                }

                for (JSONObject updatePod : toUpdate) {

                }
            }
        }
    }
}
