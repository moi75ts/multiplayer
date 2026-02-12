package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PersonsSerializer {
    public static JSONObject serializePerson(PersonAPI person) throws JSONException {
        JSONObject serializedPerson = new JSONObject();
        serializedPerson.put("personId", person.getId());
        serializedPerson.put("personalityId", person.getPersonalityAPI().getId());
        serializedPerson.put("firstName", person.getName().getFirst());
        serializedPerson.put("lastName", person.getName().getLast());
        serializedPerson.put("gender", person.getName().getGender());
        serializedPerson.put("rank", person.getRank());
        serializedPerson.put("post", person.getPostId());
        serializedPerson.put("sprite", person.getPortraitSprite());
        serializedPerson.put("level", person.getStats().getLevel());
        serializedPerson.put("isAiCore",person.isAICore());
        serializedPerson.put("aiCoreId",person.getAICoreId());
        JSONObject skillsMap = new JSONObject();
        List<MutableCharacterStatsAPI.SkillLevelAPI> skills = person.getStats().getSkillsCopy();
        for (MutableCharacterStatsAPI.SkillLevelAPI skill : skills) {
            // Only serialize if level > 0 to keep the JSON lean
            if (skill.getLevel() > 0) {
                skillsMap.put(skill.getSkill().getId(), skill.getLevel());
            }
        }
        serializedPerson.put("skills", skillsMap);
        return serializedPerson;
    }

    public static JSONArray serializePersons(List<PersonAPI> persons) throws JSONException {
        JSONArray personsArray = new JSONArray();
        for (PersonAPI person : persons) {
            personsArray.put(serializePerson(person));
        }
        return personsArray;
    }


    /**
     * Deserializes a JSON object into a {@link PersonAPI} instance, either retrieving an existing person
     * The method populates the person's attributes, including name, personality, rank, post, portrait,
     * level, and skills, based on the provided JSON data.
     *
     * @param serializedPerson the JSON object containing the serialized person data
     * @return a {@link PersonAPI} instance with attributes set according to the JSON data
     * @throws JSONException if the JSON object is malformed or missing required fields
     */
    public static PersonAPI unSerializePerson(JSONObject serializedPerson) throws JSONException {
        PersonAPI person;

        person = Global.getFactory().createPerson();
        person.setId(serializedPerson.getString("personId"));

        // Set person attributes from JSON data
        person.setPersonality(serializedPerson.getString("personalityId"));
        FullName name = new FullName(
                serializedPerson.getString("firstName"),
                serializedPerson.getString("lastName"),
                FullName.Gender.valueOf(serializedPerson.getString("gender"))
        );
        person.setName(name);
        person.setRankId(serializedPerson.getString("rank"));
        try{
            person.setPostId(serializedPerson.getString("post"));
        }catch (Exception e){
        }
        try {
            person.setPortraitSprite(serializedPerson.getString("sprite"));
        }catch (Exception e){
            //not every person has a sprite
        }

        person.getStats().setLevel(serializedPerson.getInt("level"));
        if(serializedPerson.getBoolean("isAiCore")){
            person.setAICoreId(serializedPerson.getString("aiCoreId"));
        }

        JSONObject skillsMap = serializedPerson.getJSONObject("skills");
        Iterator<?> skillKeys = skillsMap.keys();
        while (skillKeys.hasNext()) {
            String skillId = (String) skillKeys.next();
            float level = (float) skillsMap.getDouble(skillId);
            person.getStats().setSkillLevel(skillId, level);
        }
        return person;
    }

    public static List<PersonAPI> unSerializePersons(JSONArray serializedPersons) throws JSONException {
        List<PersonAPI> persons = new ArrayList<>();
        for (int i = 0; i < serializedPersons.length(); i++) {
            persons.add(unSerializePerson(serializedPersons.getJSONObject(i)));
        }
        return persons;
    }

    public static List<PersonAPI> extractPersonsFromOfficers(List<OfficerDataAPI> officers) {
        List<PersonAPI> persons = new ArrayList<>();
        for (OfficerDataAPI officer : officers) {
            persons.add(officer.getPerson());
        }
        return persons;
    }

    public static JSONObject serializePersonsToMap(List<PersonAPI> persons) throws JSONException {
        JSONObject personsMap = new JSONObject();
        for (PersonAPI person : persons) {
            if (person == null) continue;
            personsMap.put(person.getId(), serializePerson(person));
        }
        return personsMap;
    }

    public static void unSerializePersonsFromMap(JSONObject serializedMap) throws JSONException {
        Iterator<?> keys = serializedMap.keys();
        while (keys.hasNext()) {
            String personId = (String) keys.next();
            unSerializePerson(serializedMap.getJSONObject(personId));
        }
    }

    public static void patchPerson(PersonAPI person, JSONObject diff) throws JSONException {
        // 1. Handle Simple Properties
        if (diff.has("firstName") || diff.has("lastName") || diff.has("gender")) {
            String first = diff.has("firstName") ? diff.getJSONObject("firstName").getString("value") : person.getName().getFirst();
            String last = diff.has("lastName") ? diff.getJSONObject("lastName").getString("value") : person.getName().getLast();
            FullName.Gender gender = diff.has("gender") ?
                    FullName.Gender.valueOf(diff.getJSONObject("gender").getString("value")) : person.getName().getGender();
            person.setName(new FullName(first, last, gender));
        }

        if (diff.has("personalityId")) {
            person.setPersonality(diff.getJSONObject("personalityId").getString("value"));
        }
        if (diff.has("rank")) {
            person.setRankId(diff.getJSONObject("rank").getString("value"));
        }
        if (diff.has("post")) {
            person.setPostId(diff.getJSONObject("post").getString("value"));
        }
        if (diff.has("sprite")) {
            person.setPortraitSprite(diff.getJSONObject("sprite").getString("value"));
        }
        if (diff.has("level")) {
            person.getStats().setLevel(diff.getJSONObject("level").getInt("value"));
        }

        // 2. Handle Skills (Nested JSON Object)
        if (diff.has("skills")) {
            JSONObject skillsDiff = diff.getJSONObject("skills");
            Iterator<?> skillIds = skillsDiff.keys();

            while (skillIds.hasNext()) {
                String skillId = (String) skillIds.next();
                Object delta = skillsDiff.get(skillId);

                if (delta instanceof JSONObject obj && obj.has("action")) {
                    String action = obj.getString("action");
                    if ("REMOVED".equals(action)) {
                        person.getStats().setSkillLevel(skillId, 0);
                    } else {
                        // ADDED or UPDATE
                        float level = ((Number) obj.get("value")).floatValue();
                        person.getStats().setSkillLevel(skillId, level);
                    }
                }
            }
        }
    }
}
