package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import matlabmaster.multiplayer.MultiplayerLog;
import org.json.JSONException;
import org.json.JSONObject;

public class WorldSerializer {
    public static JSONObject serializeOrbit(SectorEntityToken entityToken) throws JSONException {
        JSONObject serializedOrbit = new JSONObject();
        serializedOrbit.put("id", entityToken.getId());
        serializedOrbit.put("type",entityToken.getName());
        serializedOrbit.put("orbitFocusId",entityToken.getOrbitFocus().getId());
        serializedOrbit.put("circularOrbitAngle",entityToken.getCircularOrbitAngle()); //what angle is it currently at
        serializedOrbit.put("circularOrbitRadius",entityToken.getCircularOrbitRadius()); //how far away is it orbiting
        serializedOrbit.put("circularOrbitPeriod",entityToken.getCircularOrbitPeriod());//how fast does it go around the focus
        return serializedOrbit;
    }

    public static void unSerializeOrbit(JSONObject orbit) throws JSONException {
        SectorEntityToken entity = Global.getSector().getEntityById(orbit.getString("id"));
        SectorEntityToken orbitFocus = Global.getSector().getEntityById(orbit.getString("orbitFocusId"));
        MultiplayerLog.log().warn(orbit.getString("type"));
        entity.setCircularOrbit(orbitFocus,((float) orbit.getDouble("circularOrbitAngle")),((float) orbit.getDouble("circularOrbitRadius")),((float) orbit.getDouble("circularOrbitPeriod")));
    }
}
