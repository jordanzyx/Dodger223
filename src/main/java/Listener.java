import com.google.gson.Gson;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.HashMap;

import static spark.Spark.*;

/**
 * Created By: Jordan M
 * Description: Listener for our dodger game's global leaderboard
 */
public class Listener {

    //Where we store their scores at runtime
    private HashMap<String,Integer> nameToScore = new HashMap<>();

    //Where we store their scores persistantly
    private MongoCollection<Document> collection;

    /**
     * Start up our global listener for requests
     * @param args not used
     */
    public static void main(String[] args) {
        Listener listener = new Listener(); //create our object
    }

    /**
     * Prepare listener object to take in information to our system
     */
    public Listener() {
        //Connect to database
        MongoDatabase database = MongoClients.create().getDatabase("local");

        //Get the collection
        collection = database.getCollection("dodgerScores");

        port(4567); //decide what port we are going to listen on

        //Set up the post request
        post("/api/store",(request, response) -> {
            String json = request.body(); //get the json
            Score score = new Gson().fromJson(json,Score.class); //convert json into score

            return "";
        });

        //Set up the get request
        get("/api/lb",((request, response) -> {
            String json = request.body(); //get the json


            return null;
        }));
    }

    /**
     * Score class to use for wrapping json information regarding new scores coming into our leaderboard
     */
    static class Score {
        /**
         * Fields for this class
         */
        private String name;
        private int score;

        /**
         * Create a score entry
         * @param name of the person with this score
         * @param score of this user
         */
        public Score(String name, int score) {
            this.name = name;
            this.score = score;
        }

        /**
         * @return name
         */
        public String getName() {
            return name;
        }

        /**
         * @return score
         */
        public int getScore() {
            return score;
        }

        /**
         * Set the new name
         * @param name of score
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Set the new score
         * @param score to set
         */
        public void setScore(int score) {
            this.score = score;
        }
    }


}
