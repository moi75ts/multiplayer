package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
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
        Object sectorAlreadyExist = null;
        sectorAlreadyExist = sector.getStarSystem(data.getString("systemID"));
        if (sectorAlreadyExist != null) {
            return;
        }
        System.out.println(data.getString("type"));
        StarSystemAPI newSystem = Global.getSector().createStarSystem(data.getString("systemID"));
        newSystem.setAge(StarAge.valueOf(data.getString("age")));
        newSystem.setType(StarSystemGenerator.StarSystemType.valueOf(data.getString("type")));
        newSystem.getLocation().setX((float) data.getDouble("locationx"));
        newSystem.getLocation().setY((float) data.getDouble("locationy"));
        System.out.println(data.getString("constellation"));
        if (data.getString("constellation") != null) {
            Constellation constellation = new Constellation(Constellation.ConstellationType.valueOf(data.getString("constellationType")), StarAge.valueOf(data.getString("age")));
            constellation.setNameOverride(data.getString("constellation"));
            newSystem.setConstellation(constellation);
        }
        int i;
        JSONArray planets = data.getJSONArray("planets");
        for (i = 0; i <= planets.length() - 1; i++) {
            createPlanet(planets.getJSONObject(i), newSystem);
        }
        if(newSystem.getCenter() == null){

        }
        newSystem.autogenerateHyperspaceJumpPoints();
    }

    public static void createPlanet(JSONObject data, StarSystemAPI system) throws JSONException {
        PlanetAPI planet = system.addPlanet(data.getString("planetid"),
                null,
                data.getString("name"),
                data.getString("type"),
                (float) data.getDouble("orbitAngle"),
                (float) data.getDouble("radius"),
                (float) data.getDouble("orbitRadius"),
                (float) data.getDouble("orbitPeriod"));

        if (data.getBoolean("isStar")) {
            //TODO 100f is placeholder
            system.initStar(planet.getId(), planet.getTypeId(), planet.getRadius(), 100f);
        }
        planet.setLocation((float) data.getDouble("locationx"), (float) data.getDouble("locationy"));
        if(data.getDouble("locationx") == data.getDouble("locationy") && data.getDouble("locationy") == 0){
            system.setCenter(planet);
        }
    }
}
