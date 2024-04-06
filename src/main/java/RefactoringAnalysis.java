import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class RefactoringAnalysis {
    public static void main(String[] args) throws Exception {
        FileReader csv = new FileReader("input/commits_list.csv");
        CSVReader csvReader = new CSVReaderBuilder(csv).build();
        List<String[]> records = csvReader.readAll();
        for (String[] record : records){
            if (!record[0].equals("repository_name")){
                System.out.println(record[0]);
                getRefactoring(record[0], record[2], record[3]);
//                System.exit(1);
            }
        }
    }
    private static void getRefactoring(String repo, String commitId, String parentCommitId) throws Exception {
        GitService gitService = new GitServiceImpl();
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
        Repository repository = gitService.cloneIfNotExists(
                "repo/" + repo,
                "https://github.com/" + repo + ".git");

        miner.detectBetweenCommits(repository, commitId, parentCommitId,
        new RefactoringHandler() {
            @Override
            public void handle(String commitId, List<Refactoring> refactorings){
                System.out.println("Refactorings at " + commitId);
                for (Refactoring ref : refactorings) {
                    System.out.println(ref.toString());
                }
            }
        });
    }

}
