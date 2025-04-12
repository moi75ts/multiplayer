package matlabmaster.multiplayer.SlowUpdates;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CargoPodsCheck {
    public static void CargoPodsCheck(){
        List<CargoAPI> allCargo = new ArrayList<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            // Check all entities in the system
            for (SectorEntityToken entity : system.getAllEntities()) {
                // Look for entities with cargo (e.g., debris, wrecks, or dropped containers)
                if (Objects.equals(entity.getCustomEntityType(), "cargo_pods")) {
                    CargoAPI cargo = entity.getCargo();
                    if (cargo != null && !cargo.isEmpty()) {
                        System.out.println(entity.getId());
                        allCargo.add(cargo);
                    }
                }
            }
        }
        System.out.println(allCargo);
    }
}
