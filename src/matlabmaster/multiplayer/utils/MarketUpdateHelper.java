package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import matlabmaster.multiplayer.MessageSender;
import matlabmaster.multiplayer.MultiplayerModPlugin;
import matlabmaster.multiplayer.Server;
import matlabmaster.multiplayer.User;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.util.*;
import java.util.List;

public class MarketUpdateHelper {
    public static void handleMarketUpdate(JSONObject data) throws JSONException {
        EconomyAPI economy = Global.getSector().getEconomy();
        JSONArray markets = data.getJSONArray("markets");
        boolean newMarket;
        for (int i = 0; i < markets.length(); i++) {
            JSONObject marketObject = markets.getJSONObject(i);
            MarketAPI market;
            newMarket = false;
            String factionId = "playerA";
            boolean anotherPlayerMarket = false;


            StarSystemAPI systemMarket = Global.getSector().getStarSystem(marketObject.getString("marketSystem"));
            market = economy.getMarket(marketObject.getString("marketId"));
            if (market == null) {
                market = Global.getFactory().createMarket(marketObject.getString("marketId"), marketObject.getString("name"), marketObject.getInt("marketSize"));
                newMarket = true;
            }


            if(Objects.equals(marketObject.getString("ownerFactionId"), "player") && !market.isPlayerOwned()){
                anotherPlayerMarket = true;
            }

            applyConditionsToMarket(marketObject.getJSONArray("conditions"),market);

            //if it is just a planet do not sync its data
            if(marketObject.getBoolean("isPlanetConditionMarketOnly")){
                market.setPlanetConditionMarketOnly(true);
                continue;
            }else {
                market.setPlanetConditionMarketOnly(false);
            }

            SectorEntityToken primaryEntity = Global.getSector().getEntityById(marketObject.getString("primaryEntity"));
            market.setName(marketObject.getString("name"));
            market.setSize(marketObject.getInt("marketSize"));

            //this piece of code is used to "find" the correct faction, if this is our faction, on the other clients string the faction will be something else like playera
            //so when they update it the factionid they will send will be playera,
            //we must keep it at player since this is our market
            if(anotherPlayerMarket) {
                factionId = "playera"; //the market belong to someone else than us
            }else if(market.isPlayerOwned()){
                factionId = "player";//this is our market
            }else{
                factionId = marketObject.getString("ownerFactionId");//this is an ai market
            }
            market.setFactionId(factionId);
            market.setFreePort(marketObject.getBoolean("isFreePort"));
            market.setHasSpaceport(marketObject.getBoolean("hasSpaceport"));
            market.setHasWaystation(marketObject.getBoolean("hasWaystation"));
            market.setSize(marketObject.getInt("marketSize"));
            market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());
            market.setHidden(marketObject.getBoolean("isHidden"));
            if(market.isHidden()){
                market.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
            }else{
                market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
            }
            market.setPrimaryEntity(Global.getSector().getEntityById(marketObject.getString("primaryEntity")));

            JSONArray connectedEntities = marketObject.getJSONArray("connectedEntities");
            for (int j = 0; j < connectedEntities.length(); j++) {
                JSONObject entityObject = connectedEntities.getJSONObject(j);
                SectorEntityToken entity = Global.getSector().getEntityById(entityObject.getString("EntityID"));

                if (entity == null) {
                    BaseThemeGenerator.EntityLocation loc = new BaseThemeGenerator.EntityLocation();
                    loc.type = BaseThemeGenerator.LocationType.STAR_ORBIT;
                    loc.orbit = Global.getFactory().createCircularOrbit(Objects.requireNonNullElse(Global.getSector().getEntityById(entityObject.getString("entityOrbitFocusId")), systemMarket.getCenter()), (float) entityObject.getDouble("orbitAngle"), (float) entityObject.getDouble("orbitRadius"), (float) entityObject.getDouble("orbitPeriod"));
                    BaseThemeGenerator.AddedEntity added = BaseThemeGenerator.addNonSalvageEntity(systemMarket, loc, Entities.MAKESHIFT_STATION, marketObject.getString("ownerFactionId"));
                    added.entity.setName(entityObject.getString("entityName"));
                    added.entity.setId(entityObject.getString("EntityID"));
                    added.entity.getLocation().setX((float) entityObject.getDouble("locationx"));
                    added.entity.getLocation().setY((float) entityObject.getDouble("locationy"));
                    added.entity.setMarket(market);
                    if (market.getPrimaryEntity() == null) {
                        market.setPrimaryEntity(added.entity);
                    } else {
                        market.getConnectedEntities().add(added.entity);
                    }
                } else {
                    entity.setMarket(market);
                    market.getConnectedEntities().add(entity);
                }
            }

            JSONArray industries = marketObject.getJSONArray("industries");
            for (int j = 0; j < industries.length(); j++) {
                JSONObject industryObject = industries.getJSONObject(j);
                market.addIndustry(industryObject.getString("industryId"));
                Industry industry = market.getIndustry(industryObject.getString("industryId"));
                industry.setImproved(industryObject.getBoolean("isImproved"));
                if (industryObject.getBoolean("isDisrupted")) {
                    industry.setDisrupted((float) industryObject.getDouble("distruptedDays"));
                }
                if (!Objects.equals(industryObject.getString("aiCoreId"), "multiplayerRemove")) {
                    industry.setAICoreId(industryObject.getString("aiCoreId"));
                } else {
                    industry.setAICoreId(null);
                }
                if (!Objects.equals(industryObject.getString("specialItemId"), "multiplayerRemove")) {
                    SpecialItemData specialItemData = new SpecialItemData(industryObject.getString("specialItemId"), null);
                    industry.setSpecialItem(specialItemData);
                } else {
                    industry.setSpecialItem(null);
                }
            }

            JSONArray submarkets = marketObject.getJSONArray("subMarkets");
            for (int j = 0; j < submarkets.length(); j++) {
                JSONObject submarket = submarkets.getJSONObject(j);
                market.addSubmarket(submarket.getString("submarketSpecId"));
                SubmarketAPI submarketObject = market.getSubmarket(submarket.getString("submarketSpecId"));
                submarketObject.setFaction(Global.getSector().getFaction(submarket.getString("submarketFaction")));
                JSONArray commodities = submarket.getJSONArray("commodities");
                CargoAPI cargo = submarketObject.getCargo();
                CargoHelper.clearCargo(cargo);
                CargoHelper.addCargoFromSerialized(commodities, cargo);

                JSONArray ships = submarket.getJSONArray("ships");
                cargo.getMothballedShips().clear();
                for (int k = 0; k < ships.length(); k++) {
                    JSONObject jsonShip = ships.getJSONObject(k);
                    FleetMemberAPI fleetMember = FleetHelper.unSerializeFleetMember(jsonShip);
                    cargo.getMothballedShips().addFleetMember(fleetMember);
                }
            }

            if (newMarket) {
                economy.addMarket(market, true);
            }
            economy.forceStockpileUpdate(market);


            //stolen from https://fractalsoftworks.com/forum/index.php?topic=8581.0
            //sometime I get null connected entities, must remove to prevent crash
            market.getConnectedEntities().remove(null);
            for(SectorEntityToken entity : market.getConnectedEntities()){
                entity.setMarket(market);
                entity.setFaction(factionId);
            }


            if(anotherPlayerMarket){
                //add markets if none exists
                //if a player market has no market (no commerce or underground) other players won't be able to trade here
                //this ensure that cross player trade can happen
                if(market.getSubmarket("open_market") == null){
                    market.addSubmarket("open_market");
                    market.getSubmarket("open_market").setFaction(Global.getSector().getFaction("independent"));
                }
                if(market.getSubmarket("black_market") == null){
                    market.addSubmarket("black_market");
                    market.getSubmarket("black_market").setFaction(Global.getSector().getFaction("pirates"));
                }
                if(market.getSubmarket("storage") == null){
                    market.addSubmarket("storage");
                    market.getSubmarket("storage").setFaction(Global.getSector().getFaction("neutral"));
                }
            }
        }
    }

    public static void sendMarketUpdate(List<MarketAPI> marketsToUpdate) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("command", 3);
        message.put("playerId", User.getUserId());

        JSONArray markets = new JSONArray();
        for (MarketAPI market : marketsToUpdate) {
            JSONObject marketJson = serializeMarket(market);
            markets.put(marketJson);
        }
        message.put("markets", markets);

        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        sender.sendMessage(message.toString());
    }

    private static JSONObject serializeMarket(MarketAPI market) throws JSONException {
        JSONObject marketJson = new JSONObject();
        if (market.isPlayerOwned()) {
            marketJson.put("ownerFactionId", "player");
        } else {
            marketJson.put("ownerFactionId", market.getFactionId());
        }
        marketJson.put("marketId", market.getId());
        marketJson.put("name", market.getName());
        marketJson.put("marketSize", market.getSize());
        marketJson.put("isFreePort", market.isFreePort());
        marketJson.put("hasSpaceport", market.hasSpaceport());
        marketJson.put("hasWaystation", market.hasWaystation());
        marketJson.put("primaryEntity", market.getPrimaryEntity().getId());
        marketJson.put("primaryEntityx", market.getLocation().x);
        marketJson.put("primaryEntityy", market.getLocation().y);
        marketJson.put("marketSystem", market.getStarSystem().getId());
        marketJson.put("isHidden", market.isHidden());
        marketJson.put("isPlanetConditionMarketOnly",market.isPlanetConditionMarketOnly());

        JSONArray connectedEntities = new JSONArray();
        Set<SectorEntityToken> connectedClientEntities = market.getConnectedEntities();
        for (SectorEntityToken entity : connectedClientEntities) {
            JSONObject entityObject = new JSONObject();
            entityObject.put("EntityID", entity.getId());
            entityObject.put("locationx", entity.getLocation().x);
            entityObject.put("locationy", entity.getLocation().y);
            entityObject.put("entityName", entity.getName());
            entityObject.put("orbitAngle", entity.getCircularOrbitAngle());
            entityObject.put("orbitPeriod", entity.getCircularOrbitPeriod());
            entityObject.put("orbitRadius", entity.getCircularOrbitRadius());
            entityObject.put("entityOrbitFocusId", entity.getOrbit().getFocus().getId());
            connectedEntities.put(entityObject);
        }
        marketJson.put("connectedEntities", connectedEntities);

        JSONArray conditions = new JSONArray();
        List<MarketConditionAPI> conditionsList = market.getConditions();
        for (MarketConditionAPI condition : conditionsList) {
            if (!Objects.equals(condition.getId(), "pather_cells") && !Objects.equals(condition.getId(), "pirate_activity")) {
                conditions.put(condition.getId());
            }
        }
        marketJson.put("conditions", conditions);

        JSONArray industries = new JSONArray();
        List<Industry> industriesList = market.getIndustries();
        for (Industry industry : industriesList) {
            JSONObject industryObject = new JSONObject();
            industryObject.put("industryId", industry.getId());
            industryObject.put("isImproved", industry.isImproved());
            industryObject.put("isDisrupted", industry.isDisrupted());
            industryObject.put("distruptedDays", industry.getDisruptedDays());
            try {
                industryObject.put("specialItemId", industry.getSpecialItem().getId());
            } catch (Exception e) {
                industryObject.put("specialItemId", "multiplayerRemove");
            }
            industryObject.put("aiCoreId", industry.getAICoreId() != null ? industry.getAICoreId() : "multiplayerRemove");
            industries.put(industryObject);
        }
        marketJson.put("industries", industries);

        JSONArray subMarkets = new JSONArray();
        List<SubmarketAPI> submarketList = market.getSubmarketsCopy();
        for (SubmarketAPI submarket : submarketList) {
            if (Objects.equals(submarket.getSpecId(), "storage")) {
                continue;
            }
            JSONObject submarketObject = new JSONObject();
            submarketObject.put("submarketSpecId", submarket.getSpecId());
            submarketObject.put("submarketFaction", submarket.getFaction().getId());
            CargoAPI cargo = submarket.getCargo();
            JSONArray ships = FleetHelper.serializeFleetShips(cargo.getMothballedShips());
            submarketObject.put("ships", ships);
            List<CargoStackAPI> cargoStacks = cargo.getStacksCopy();
            JSONArray commodities = CargoHelper.serializeCargo(cargoStacks);
            submarketObject.put("commodities", commodities);
            subMarkets.put(submarketObject);
        }
        marketJson.put("subMarkets", subMarkets);

        return marketJson;
    }

    public static void requestMarketUpdate() throws JSONException {
        JSONObject message = new JSONObject();
        message.put("command", 2);
        message.put("playerId", User.getUserId());
        MessageSender sender = MultiplayerModPlugin.getMessageSender();
        sender.sendMessage(message.toString());
    }

    public static void handleMarketUpdateRequest(String playerId) throws JSONException {
        if (!Objects.equals(MultiplayerModPlugin.getMode(), "server")) {
            return;
        }

        EconomyAPI economy = Global.getSector().getEconomy();
        List<MarketAPI> marketsToUpdate = new ArrayList<>();
        List<LocationAPI> systemsWithMarkets = economy.getLocationsWithMarkets();
        for (LocationAPI systemWithMarkets : systemsWithMarkets) {
            marketsToUpdate.addAll(economy.getMarkets(systemWithMarkets));
        }

        JSONObject message = new JSONObject();
        message.put("playerId", "server");
        message.put("command", 3);
        JSONArray markets = new JSONArray();
        for (MarketAPI market : marketsToUpdate) {
            JSONObject marketJson = serializeMarket(market);
            markets.put(marketJson);
        }
        message.put("markets", markets);

        Server serverInstance = (Server) MultiplayerModPlugin.getMessageSender();
        serverInstance.sendTo(playerId, message.toString());
    }

    public static void applyConditionsToMarket(JSONArray conditions,MarketAPI market) throws JSONException {
        // Get current market conditions as a Set for easier comparison
        Set<String> currentConditions = new HashSet<>();
        for (MarketConditionAPI condition : market.getConditions()) {
            currentConditions.add(condition.getId());
        }

        // Get new conditions from JSONArray as a Set
        Set<String> newConditions = new HashSet<>();
        for (int j = 0; j < conditions.length(); j++) {
            String condition = conditions.get(j).toString();
            newConditions.add(condition);
        }

        // Remove conditions that are in current but not in new
        for (String conditionId : currentConditions) {
            if (!newConditions.contains(conditionId)) {
                market.removeCondition(conditionId);
            }
        }

        // Add conditions that are in new but not in current
        for (String conditionId : newConditions) {
            if (!currentConditions.contains(conditionId)) {
                market.addCondition(conditionId);
            }
        }
    }
}