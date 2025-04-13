package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.*;
import com.fs.starfarer.campaign.BaseLocation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

public class CreateSystem {
    public static void createSystem(JSONObject data) throws JSONException {
        if (Objects.equals(data.getString("systemID"), "deep space")) {
            return;
        }
        SectorAPI sector = Global.getSector();
        Object sectorAlreadyExist = sector.getStarSystem(data.getString("systemID"));
        if (sectorAlreadyExist != null) {
            return;
        }
        StarSystemAPI newSystem = Global.getSector().createStarSystem(data.getString("systemID"));
        newSystem.setAge(StarAge.valueOf(data.getString("age")));
        newSystem.setType(StarSystemGenerator.StarSystemType.valueOf(data.getString("type")));
        newSystem.getLocation().setX((float) data.getDouble("locationx"));
        newSystem.getLocation().setY((float) data.getDouble("locationy"));
        Constellation constellation = new Constellation(
                Constellation.ConstellationType.valueOf(data.getString("constellationType")),
                StarAge.valueOf(data.getString("age"))
        );
        String constellationName = data.getString("constellation");
        constellation.setNameOverride(constellationName != null ? constellationName : "Unnamed Constellation");
        NameGenData namepick = new NameGenData("name", "name2");
        ProcgenUsedNames.NamePick namePick = new ProcgenUsedNames.NamePick(namepick, "name", "name");
        constellation.setNamePick(namePick);
        newSystem.setConstellation(constellation);

        JSONArray planets = data.getJSONArray("planets");
        for (int i = 0; i < planets.length(); i++) {
            createPlanet(planets.getJSONObject(i), newSystem);
        }
        newSystem.autogenerateHyperspaceJumpPoints(true, true, true);
        if (!Objects.equals(data.getString("type"), "NEBULA")) {

        }
    }

    public static void createPlanet(JSONObject data, StarSystemAPI system) throws JSONException {
        SectorEntityToken orbitFocus;
        try {
            orbitFocus = system.getEntityById(data.getString("orbitFocusId"));
        }catch (Exception e){
            orbitFocus = system.addCustomEntity(null, "System Center", "stable_location", null);
            orbitFocus.setLocation(0, 0);
            system.setCenter(orbitFocus);
        }
        if (data.getBoolean("isStar")) {
            if (system.getStar() == null) {
                system.initStar(data.getString("planetid"),
                        data.getString("type"),
                        (float) data.getDouble("radius"),
                        (float) data.getDouble("hyperspaceLocationX"),
                        (float) data.getDouble("hyperspaceLocationY"),
                        (float) data.getDouble("coronaSize"));

                system.getStar().setLocation((float) data.getDouble("locationx"), (float) data.getDouble("locationy"));
                system.getStar().setName(data.getString("name"));
                system.setCenter(system.getStar());
            } else if (system.getSecondary() == null) {
                PlanetAPI planet = system.addPlanet(data.getString("planetid"),
                        orbitFocus,
                        data.getString("name"),
                        data.getString("type"),
                        (float) data.getDouble("orbitAngle"),
                        (float) data.getDouble("radius"),
                        (float) data.getDouble("orbitRadius"),
                        (float) data.getDouble("orbitPeriod"));

                planet.setLocation((float) data.getDouble("locationx"), (float) data.getDouble("locationy"));
                system.setSecondary(planet);
            } else if (system.getTertiary() == null) {
                PlanetAPI planet = system.addPlanet(data.getString("planetid"),
                        orbitFocus,  //
                        data.getString("name"),
                        data.getString("type"),
                        (float) data.getDouble("orbitAngle"),
                        (float) data.getDouble("radius"),
                        (float) data.getDouble("orbitRadius"),
                        (float) data.getDouble("orbitPeriod"));

                planet.setLocation((float) data.getDouble("locationx"), (float) data.getDouble("locationy"));
                system.setTertiary(planet);
            }
        } else {
            // For non-star planets, ensure they orbit the system's center (star)

            PlanetAPI planet = system.addPlanet(data.getString("planetid"),
                    orbitFocus,  // Set focus to the center (star or custom entity)
                    data.getString("name"),
                    data.getString("type"),
                    (float) data.getDouble("orbitAngle"),
                    (float) data.getDouble("radius"),
                    (float) data.getDouble("orbitRadius"),
                    (float) data.getDouble("orbitPeriod"));

            planet.setLocation((float) data.getDouble("locationx"), (float) data.getDouble("locationy"));
        }
    }
}
