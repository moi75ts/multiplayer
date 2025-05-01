package matlabmaster.multiplayer.fastUpdates;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import matlabmaster.multiplayer.MessageSender;
import matlabmaster.multiplayer.MultiplayerModPlugin;
import matlabmaster.multiplayer.Server;
import matlabmaster.multiplayer.User;
import matlabmaster.multiplayer.utils.CargoHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

public class CargoPodsSync {
    public static JSONObject getCargoPodsCheckMessage() throws JSONException {
        List<SectorEntityToken> allCargoPods = CargoHelper.getAllCargoPods();
        JSONArray cargoPods = new JSONArray();
        for (SectorEntityToken cargoPod : allCargoPods) {
            JSONObject cargoPodObject = new JSONObject();
            cargoPodObject.put("entityId", cargoPod.getId());
            cargoPodObject.put("locationX", cargoPod.getLocation().x);
            cargoPodObject.put("locationY", cargoPod.getLocation().y);
            try {
                cargoPodObject.put("system", cargoPod.getStarSystem().getId());
            } catch (NullPointerException e) {
                cargoPodObject.put("isInHyperspace", cargoPod.isInHyperspace());
            }
            JSONArray serializedCargo = CargoHelper.serializeCargo(cargoPod.getCargo().getStacksCopy());
            cargoPodObject.put("serializedCargo", serializedCargo);
            cargoPods.put(cargoPodObject);
        }
        JSONObject message = new JSONObject();
        message.put("allCargoPodsEverSpawned", CargoHelper.hasEverSeenPod);
        message.put("command", 7);
        message.put("playerId", User.getUserId());
        message.put("cargoPods", cargoPods);
        return message;
    }


    public static void cargoPodsCheck() throws JSONException {
        JSONObject message = getCargoPodsCheckMessage();
        if (!Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
            MessageSender sender = MultiplayerModPlugin.getMessageSender();
            sender.sendMessage(message.toString());
        } else {
            compareCargoPods(message);
        }
    }

    public static void compareCargoPods(JSONObject message) throws JSONException {
        CargoHelper.updateLocalPods(message);
        JSONObject responseMessage = getCargoPodsCheckMessage();
        Server server = (Server) MultiplayerModPlugin.getMessageSender();
        server.sendToEveryoneBut(message.getString("playerId"), responseMessage.toString());
    }
}