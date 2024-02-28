package process_testsmell;

import java.util.List;
import java.util.Map;

public class TestSmellsDiff {
    String repositoryName;
    String date;
    String URL;
    String author;
    String commitMessage;
    Map<String, List<String>> changeFileAndMethod;

    public TestSmellsDiff(String repositoryName, String date, String URL, String author, String commitMessage, Map<String, List<String>> changeFileAndMethod) {
        this.repositoryName = repositoryName;
        this.date = date;
        this.URL = URL;
        this.author = author;
        this.commitMessage = commitMessage;
        this.changeFileAndMethod = changeFileAndMethod;
    }

}
