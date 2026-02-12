package matlabmaster.multiplayer.updates;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import matlabmaster.multiplayer.client.Client;
import matlabmaster.multiplayer.utils.FleetSerializer;
import matlabmaster.multiplayer.utils.JsonDiffUtility;
import org.json.JSONException;
import org.json.JSONObject;

public class FleetSync
{
    private JSONObject lastTickFleet = new JSONObject();
    private JSONObject diffs = new JSONObject();

    public void sendOwnFleetUpdate(Client client) throws JSONException {
        JSONObject newFleet = FleetSerializer.serializeFleet(Global.getSector().getPlayerFleet());
        diffs = JsonDiffUtility.getDifferences(lastTickFleet,newFleet);
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
}
