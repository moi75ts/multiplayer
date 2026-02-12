package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.campaign.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public class CargoSerializer {

    public static JSONObject serializeCargoToMap(List<CargoStackAPI> cargoStackList) throws JSONException {
        JSONObject cargoMap = new JSONObject();
        for (CargoStackAPI cargo : cargoStackList) {
            JSONObject cargoObject = new JSONObject();
            float size = cargo.getSize();
            cargoObject.put("quantity", size);
            cargoObject.put("type", cargo.getType().toString());

            String key = "";
            if (cargo.isCommodityStack()) {
                key = cargo.getCommodityId();
                cargoObject.put("commodityId", key);
            } else if (cargo.isWeaponStack()) {
                key = cargo.getWeaponSpecIfWeapon().getWeaponId();
                cargoObject.put("weaponId", key);
            } else if (cargo.isSpecialStack()) {
                key = cargo.getSpecialDataIfSpecial().getId() + "_" + cargo.getSpecialDataIfSpecial().getData();
                cargoObject.put("specialId", cargo.getSpecialDataIfSpecial().getId());
                cargoObject.put("specialData", cargo.getSpecialDataIfSpecial().getData());
            } else if (cargo.isFighterWingStack()) {
                key = cargo.getFighterWingSpecIfWing().getId();
                cargoObject.put("fighterId", key);
            }

            // If key is empty (shouldn't happen), fallback to hash
            if (key.isEmpty()) key = "unknown_" + cargo.hashCode();
            cargoMap.put(key, cargoObject);
        }
        return cargoMap;
    }

    public static void addCargoFromMappedSerialized(JSONObject serializedCargo, CargoAPI cargo) throws JSONException {
        Iterator<?> keys = serializedCargo.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            JSONObject data = serializedCargo.getJSONObject(key);
            int qty = data.getInt("quantity");
            String type = data.getString("type");

            if (type.equals("RESOURCES")) {
                cargo.addCommodity(data.getString("commodityId"), qty);
            } else if (type.equals("WEAPONS")) {
                cargo.addWeapons(data.getString("weaponId"), qty);
            } else if (type.equals("FIGHTER_CHIP")) {
                cargo.addFighters(data.getString("fighterId"), qty);
            } else if (type.equals("SPECIAL")) {
                SpecialItemData spec = new SpecialItemData(data.getString("specialId"), data.optString("specialData", null));
                cargo.addSpecial(spec, qty);
            }
        }
    }

    public static void patchCargo(CargoAPI cargo, JSONObject diff) throws JSONException {
        Iterator<?> keys = diff.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object delta = diff.get(key);

            if (delta instanceof JSONObject obj && obj.has("action")) {
                String action = obj.getString("action");

                // For REMOVED, we just clear the stack
                if ("REMOVED".equals(action)) {
                    removeStackByKey(cargo, key);
                    continue;
                }

                // For ADDED or UPDATE, we need the inner data
                JSONObject itemData = obj.getJSONObject("value");
                float targetQty = (float) itemData.getDouble("quantity");
                String type = itemData.getString("type");

                syncItemStack(cargo, key, itemData, type, targetQty);
            }
        }
    }

    private static void syncItemStack(CargoAPI cargo, String key, JSONObject data, String type, float targetQty) throws JSONException {
        float currentQty = getQuantityByKey(cargo, key, type);
        float diff = targetQty - currentQty;

        if (diff == 0) return;

        if (type.equals("RESOURCES")) {
            if (diff > 0) cargo.addCommodity(data.getString("commodityId"), diff);
            else cargo.removeCommodity(data.getString("commodityId"), -diff);
        } else if (type.equals("WEAPONS")) {
            if (diff > 0) cargo.addWeapons(data.getString("weaponId"), (int)diff);
            else cargo.removeWeapons(data.getString("weaponId"), (int)-diff);
        } else if (type.equals("FIGHTER_CHIP")) {
            if (diff > 0) cargo.addFighters(data.getString("fighterId"), (int)diff);
            else cargo.removeFighters(data.getString("fighterId"), (int)-diff);
        } else if (type.equals("SPECIAL")) {
            SpecialItemData spec = new SpecialItemData(data.getString("specialId"), data.optString("specialData", null));
            if (diff > 0) cargo.addSpecial(spec, diff);
            else cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, spec, -diff);
        }
    }

    private static float getQuantityByKey(CargoAPI cargo, String key, String type) {
        if (type.equals("RESOURCES")) return cargo.getCommodityQuantity(key);
        if (type.equals("WEAPONS")) return cargo.getQuantity(CargoAPI.CargoItemType.WEAPONS, key);
        if (type.equals("FIGHTER_CHIP")) return cargo.getQuantity(CargoAPI.CargoItemType.FIGHTER_CHIP, key);
        // Special items need exact match
        return cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, key);
    }

    private static void removeStackByKey(CargoAPI cargo, String key) {
        // Find the specific stack and set to 0 to trigger removal
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            String stackKey = "";
            if (stack.isCommodityStack()) stackKey = stack.getCommodityId();
            else if (stack.isWeaponStack()) stackKey = stack.getWeaponSpecIfWeapon().getWeaponId();
            else if (stack.isFighterWingStack()) stackKey = stack.getFighterWingSpecIfWing().getId();
            else if (stack.isSpecialStack()) stackKey = stack.getSpecialDataIfSpecial().getId() + "_" + stack.getSpecialDataIfSpecial().getData();

            if (key.equals(stackKey)) {
                stack.setSize(0);
            }
        }
        cargo.removeEmptyStacks();
    }

    public static void clearCargo(CargoAPI cargo) {
        List<CargoStackAPI> cargoStacks = cargo.getStacksCopy();
        for (CargoStackAPI cargoStack : cargoStacks) {
            cargoStack.setSize(0);
        }
        cargo.removeEmptyStacks();
    }
}
