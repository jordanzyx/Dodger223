import com.google.gson.Gson;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static spark.Spark.*;

/**
 * Created By: Jordan M
 * Description: Listener for our dodger game's global leaderboard
 */
public class Listener {

    //Where we store their scores at runtime
    private HashMap<String,Integer> nameToScore = new HashMap<>();

    //Where we store their scores persistently
    private MongoCollection<Document> collection;

    /**
     * Create a service so we can on a loop save all our scores to the database
     */
    private ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

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

        //Load in all values from our database
        collection.find().forEach(doc -> {
            Score found = deserialize(doc);
            nameToScore.put(found.getName(),found.getScore());
        });

        //Setup timed storing of information we have locally every 10 seconds while running
        service.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                //Synchronize to make sure we don't read while being written to
                synchronized (nameToScore){
                    List<WriteModel<Document>> toInsert = new ArrayList<>(); //create a list of models to insert
                    toInsert.add(new DeleteManyModel<>(new Document())); //delete all the documents that are there before
                    nameToScore.forEach((key,val) -> {
                        Score score = new Score(key,val); //create score
                        toInsert.add(new InsertOneModel<>(serialize(score))); //add
                    });
                    if(!toInsert.isEmpty())collection.bulkWrite(toInsert);
                }
            }
        },10,10, TimeUnit.SECONDS);

        //Set up the post request
        post("/api/store",(request, response) -> {
            String json = request.body(); //get the json
            Score score = new Gson().fromJson(json,Score.class); //convert json into score

            return "";
        });

        //Get spot
        get("/api/spot",(((request, response) -> {
            String json = request.body(); //get the json
            Document doc = Document.parse(json); //create a quick doc
            int score = doc.getInteger("score");
            Document item = new Document("above",getSpotsAbove(score));
            return item.toJson();
        })));

        //Set up the get request
        get("/api/lb",((request, response) -> {
            String json = request.body(); //get the json
            Document doc = Document.parse(json); //create a quick doc
            if(!doc.containsKey("lb"))return null;
            Document list = new Document();
            list.put("lb",getTop10());
            return list.toJson();
        }));

        //Get a players highest score
        get("/api/usr",(((request, response) -> {
            String json = request.body(); //get the json
            Document doc = Document.parse(json); //create a quick doc
            if(!doc.containsKey("usr")){
                response.status(404);
                return response;
            }

            //Get the username to find the highest score for
            String usr = doc.getString("usr");

            if(nameToScore.containsKey(usr)){
                response.status(404);
                return response;
            }

            //Create a score to return
            Score score = new Score(usr,nameToScore.get(usr));

            return new Gson().toJson(score,Score.class);
        })));
    }

    private List<String> getTop10(){
        List<String> scores = new ArrayList<>(); //scores to return
        TreeSet<Map.Entry<String,Integer>> set = new TreeSet<>(new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return Integer.compare(o1.getValue(),o2.getValue());
            }
        });
        set.addAll(nameToScore.entrySet());
        set.stream().limit(10).forEach(entry -> {
            Score score = new Score(entry.getKey(),entry.getValue());
            String json = new Gson().toJson(score,Score.class);
            scores.add(json);
        });

        return scores;
    }

    private int getSpotsAbove(int score){
        int above = 0;

        for (Map.Entry<String, Integer> entry : nameToScore.entrySet()){
            if(entry.getValue() > score)above++;
        }

        return above;
    }

    /**
     * Turn a score into a document for us to store
     * @param score to insert
     * @return document form
     */
    private Document serialize(Score score){
        Document doc = new Document();
        doc.put("_id",score.getName());
        doc.put("score",score.getScore());
        return doc;
    }

    /**
     * Deserialize our document into a Score object
     * @param doc with info
     * @return Score object
     */
    private Score deserialize(Document doc){
        String name = doc.getString("_id");
        int score = doc.getInteger("score");
        return new Score(name,score);
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
