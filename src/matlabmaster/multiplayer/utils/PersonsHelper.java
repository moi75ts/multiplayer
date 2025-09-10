package matlabmaster.multiplayer.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PersonsHelper {
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
        JSONArray skillsArray = new JSONArray();
        List<MutableCharacterStatsAPI.SkillLevelAPI> skills = person.getStats().getSkillsCopy();
        for (MutableCharacterStatsAPI.SkillLevelAPI skill : skills) {
            JSONObject skillObject = new JSONObject();
            skillObject.put("skillId", skill.getSkill().getId());
            skillObject.put("skillLevel", skill.getLevel());
            skillsArray.put(skillObject);
        }
        serializedPerson.put("skills", skillsArray);
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
     * from the {@link ImportantPeopleAPI} or creating a new one if no person with the specified ID exists.
     * The method populates the person's attributes, including name, personality, rank, post, portrait,
     * level, and skills, based on the provided JSON data.
     *
     * @param serializedPerson the JSON object containing the serialized person data
     * @return a {@link PersonAPI} instance with attributes set according to the JSON data
     * @throws JSONException if the JSON object is malformed or missing required fields
     */
    public static PersonAPI unSerializePerson(JSONObject serializedPerson) throws JSONException {
        ImportantPeopleAPI allPeoples = Global.getSector().getImportantPeople();
        PersonAPI person;

        // Check if a person with the given ID already exists
        if (allPeoples.getPerson(serializedPerson.getString("personId")) == null) {
            person = Global.getFactory().createPerson();
            person.setId(serializedPerson.getString("personId"));
        } else {
            person = allPeoples.getPerson(serializedPerson.getString("personId"));
        }

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
        person.setPortraitSprite(serializedPerson.getString("sprite"));
        person.getStats().setLevel(serializedPerson.getInt("level"));
        if(serializedPerson.getBoolean("isAiCore")){
            person.setAICoreId(serializedPerson.getString("aiCoreId"));
        }

        // Set skills from JSON array
        JSONArray skillsArray = serializedPerson.getJSONArray("skills");
        for (int i = 0; i < skillsArray.length(); i++) {
            JSONObject skillObject = skillsArray.getJSONObject(i);
            person.getStats().setSkillLevel(skillObject.getString("skillId"), skillObject.getInt("skillLevel"));
        }

        allPeoples.addPerson(person); //add to important people for easy retrieval
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

    public static boolean personExists(String personId){
        return Global.getSector().getImportantPeople().getPerson(personId) != null;
    }
}
