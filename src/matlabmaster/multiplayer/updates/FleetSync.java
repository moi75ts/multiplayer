package matlabmaster.multiplayer.updates;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import matlabmaster.multiplayer.client.Client;
import matlabmaster.multiplayer.utils.FleetHelper;
import matlabmaster.multiplayer.utils.FleetSerializer;
import matlabmaster.multiplayer.utils.JsonDiffUtility;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FleetSync
{
    private JSONObject lastTickFleet = new JSONObject();
    private JSONObject lastTickGlobalFleet = new JSONObject();

    public void sendOwnFleetUpdate(Client client) throws JSONException {
        JSONObject newFleet = FleetSerializer.serializeFleet(Global.getSector().getPlayerFleet());
        JSONObject diffs = JsonDiffUtility.getDifferences(lastTickFleet,newFleet);
        lastTickFleet = newFleet;
        if (diffs.length() > 0) {
            JSONObject packet = new JSONObject();
            packet.put("commandId","playerFleetUpdate");
            packet.put("fleetId", newFleet.getString("id")); // Root ID
            packet.put("changes", diffs);
            client.send(String.valueOf(packet));
        }
    }

    public void handleRemoteFleetUpdate(JSONObject fleetDiffs) throws JSONException {
        FleetSerializer.applyFleetDiff((CampaignFleetAPI) Global.getSector().getEntityById(fleetDiffs.getString("fleetId")),fleetDiffs.getJSONObject("changes"));
    }

    public void sendGlobalFleetsUpdate(Client client) throws JSONException {
        JSONArray snapshot = FleetHelper.getFleetsSnapshot();
        JSONObject currentGlobalFleets = new JSONObject();

        // 1. Convert the Array snapshot to a Map keyed by Fleet ID
        // Structure: { "fleet_id_1": { ...data... }, "fleet_id_2": { ...data... } }
        for (int i = 0; i < snapshot.length(); i++) {
            JSONObject fleet = snapshot.getJSONObject(i);
            currentGlobalFleets.put(fleet.getString("id"), fleet);
        }

        // 2. Diff the two Maps
        // Your JsonDiffUtility will automatically generate:
        // - "ADDED" actions for new keys (new fleets)
        // - "REMOVED" actions for missing keys (despawned fleets)
        // - Nested updates for existing fleets
        JSONObject diffs = JsonDiffUtility.getDifferences(lastTickGlobalFleet, currentGlobalFleets);

        // 3. Update local state
        lastTickGlobalFleet = currentGlobalFleets;

        // 4. Send Packet
        if (diffs.length() > 0) {
            JSONObject packet = new JSONObject();
            packet.put("commandId", "globalFleetsUpdate");
            packet.put("updates", diffs);
            client.send(packet.toString());
        }
    }
}
