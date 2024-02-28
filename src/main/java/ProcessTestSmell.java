import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import static java.lang.System.exit;

public class ProcessTestSmell {
    public static void main(String[] args) throws IOException {

        FileReader jsonReader = new FileReader("input/change_methods.json");
        FileReader csv = new FileReader("input/commits_list.csv");
        CSVReader csvReader = new CSVReaderBuilder(csv).build();
        List<String[]> records = csvReader.readAll();
        List<String[]> output;

        JsonObject jsonObject = Main.readJson(jsonReader);
        processJsonObject(records, jsonObject);
    }

    private static void processJsonObject(List<String[]> records, JsonObject jsonObject) throws FileNotFoundException {
        for (Map.Entry<String, JsonElement> repoEntry : jsonObject.entrySet()) {
            String repoDir = repoEntry.getKey();

            JsonObject commitData = repoEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> commitEntry : commitData.entrySet()) {
                String commitID = commitEntry.getKey();
                processCommitTestSmell(repoDir, commitID, records);
            }
        }
    }

    private static void processCommitTestSmell(String repoDir, String commitID, List<String[]> records) throws FileNotFoundException {
        String repoName = repoDir.substring(repoDir.lastIndexOf("repo/") + 5);
        String[] repoInfo = readRecords(repoName, commitID, records);

        JsonObject commitTestSmell = processTestSmellJson(repoName, commitID);
        JsonObject parentTestSmell = processTestSmellJson(repoName, repoInfo[5]);

        System.out.println(commitID);

    }

    private static JsonObject processTestSmellJson(String repoName, String commitID) throws FileNotFoundException {
        String fileName = MessageFormat.format("{0}/{1}/{2}/{3}", "results/smells", repoName, commitID, "smells_result.json");
        FileReader fileReader = new FileReader(fileName);
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(fileReader, JsonElement.class);
        JsonObject jsonObject = element.getAsJsonObject();
        return jsonObject;
    }

    private static String[] readRecords(String repoName, String commitID, List<String[]> records) {
        String[] repoInfo = new String[6];

        for (String[] record : records){
            if (!record[0].equals("repository_name")){
                if (record[0].equals(repoName) && record[2].equals(commitID)){
                    repoInfo[0] = record[0];    // repoName
                    repoInfo[1] = record[6];    // date
                    repoInfo[2] = record[7];    // author
                    repoInfo[3] = record[4];    // url
                    repoInfo[4] = record[5];    // message
                    repoInfo[5] = record[3];    // parent commit id
                    break;
                }
            }
        }
        return repoInfo;
    }

    private static void compareTestSmells(){

    }
}
