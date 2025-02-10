import file_detector.FileWalker;
import file_mapping.MappingResultsWriter;
import file_mapping.MappingTestFile;
import file_mapping.MappingDetector;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import testsmell.*;
import thresholds.DefaultThresholds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.api.CheckoutCommand;


public class Main {
    static List<MappingTestFile> testFiles;

    public static void detectMappings(String projectDir, String srcDir, String repoName) throws IOException {
        File srcFolder = new File(projectDir);

        if(!srcFolder.exists() || !srcFolder.isDirectory()) {
            System.out.println("Please provide a valid path to the source directory");
            return;
        }

        MappingDetector mappingDetector;
        FileWalker fw = new FileWalker();
        List<Path> files = fw.getJavaTestFiles(projectDir, true);
        testFiles = new ArrayList<>();
        for (Path testPath : files) {
            mappingDetector = new MappingDetector();
            String str =  srcFolder.getAbsolutePath()+","+testPath.toAbsolutePath();
            testFiles.add(mappingDetector.detectMapping(str));
        }

        System.out.println("Saving results. Total lines:" + testFiles.size());
        MappingResultsWriter resultsWriter = MappingResultsWriter.createResultsWriter(repoName);
        List<String> columnValues;
        for (MappingTestFile testFile : testFiles) {
            columnValues = new ArrayList<>();
            columnValues.add(0, testFile.getTestFilePath());
            columnValues.add(1, testFile.getProductionFilePath());
            resultsWriter.writeLine(columnValues);
        }

        System.out.println("Test File Mapping Completed!");
    }

    public static void detectSmells(String repoName) throws IOException {
        TestSmellDetector testSmellDetector = new TestSmellDetector(new DefaultThresholds());
        String inputFile = MessageFormat.format("{0}/{1}/{2}.{3}", "results/mappings", repoName, "mapping", "csv");

        /*
          Read the input file and build the TestFile objects
         */
        BufferedReader in = new BufferedReader(new FileReader(inputFile));
        String str;

        String[] lineItem;
        TestFile testFile;
        List<TestFile> testFiles = new ArrayList<>();
        while ((str = in.readLine()) != null) {
            // use comma as separator
            lineItem = str.split(",");
//            System.out.println("line: " + lineItem[0] + " - " + lineItem[1]);
            //check if the test file has an associated production file
            if (lineItem.length == 2) {
                testFile = new TestFile(lineItem[0], lineItem[1], "");
            } else {
                testFile = new TestFile(lineItem[0], lineItem[1], lineItem[2]);
            }

            testFiles.add(testFile);
        }

        /*
          Initialize the output file - Create the output file and add the column names
         */
        ResultsWriter resultsWriter = ResultsWriter.createResultsWriter(repoName);
        List<String> columnNames;
        List<String> columnValues;

        columnNames = testSmellDetector.getTestSmellNames();
        columnNames.add(0, "App");
        columnNames.add(1, "TestClass");
        columnNames.add(2, "TestFilePath");
        columnNames.add(3, "ProductionFilePath");
        columnNames.add(4, "RelativeTestFilePath");
        columnNames.add(5, "RelativeProductionFilePath");
        columnNames.add(6, "NumberOfMethods");

        resultsWriter.writeColumnName(columnNames);

        /*
          Iterate through all test files to detect smells and then write the output
        */
        TestFile tempFile;
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date;
        SmellRecorder smellRecorder = new SmellRecorder();
        for (TestFile file : testFiles) {
            date = new Date();
            System.out.println(dateFormat.format(date) + " Processing: " + file.getTestFilePath());
            System.out.println("Processing: " + file.getTestFilePath());

            //detect smells
            tempFile = testSmellDetector.detectSmells(file);
            smellRecorder.addTestFileData(file);

            //write output
            columnValues = new ArrayList<>();
            columnValues.add(file.getApp());
            columnValues.add(file.getTestFileName());
            columnValues.add(file.getTestFilePath());
            columnValues.add(file.getProductionFilePath());
            columnValues.add(file.getRelativeTestFilePath());
            columnValues.add(file.getRelativeProductionFilePath());
            columnValues.add(String.valueOf(file.getNumberOfTestMethods()));
            for (AbstractSmell smell : tempFile.getTestSmells()) {
                try {
                    columnValues.add(String.valueOf(smell.getNumberOfSmellyTests()));
                } catch (NullPointerException e) {
                    columnValues.add("");
                }
            }
            resultsWriter.writeLine(columnValues);
        }
        smellRecorder.recordSmells(repoName);
        System.out.println("Smell Detection Finished");
    }

    public static void main(String[] args) throws IOException, GitAPIException {
        if (args == null || args.length == 0) {
            System.out.println("Please provide the commit URL.");
            return;
        }

        String commitURL = args[0];
        String[] split = commitURL.split("/commit/");
        String repositoryURL = split[0];
        String commitID = split[1];

        String repositoryOwnerAndName = repositoryURL.replace("https://github.com/", "");
        String repositoryOwnerAndNameAndCommit = repositoryOwnerAndName + "/" + commitID;

        Path projectDir = Paths.get("repos", repositoryOwnerAndName);
        Path mappingResultsDir = Paths.get("results/mappings", repositoryOwnerAndNameAndCommit);
        Path smellResultsDir = Paths.get("results/smells", repositoryOwnerAndNameAndCommit);

        Files.createDirectories(mappingResultsDir);
        Files.createDirectories(smellResultsDir);

        Repository repository = openRepository(projectDir.toFile(), repositoryURL);
        Git git = new Git(repository);
        checkoutRepository(git, commitID);

        detectMappings(projectDir.toString(), "", repositoryOwnerAndNameAndCommit);
        detectSmells(repositoryOwnerAndNameAndCommit);
    }

    private static Repository openRepository(File inputFile, String repositoryURL) throws IOException, GitAPIException {
        try {
            return Git.open(inputFile).getRepository();
        } catch (Exception e) {
            FileUtils.deleteDirectory(inputFile);
            System.out.println("Clone Repository");
            cloneRepository(inputFile, repositoryURL);
            return Git.open(inputFile).getRepository();
        }
    }

    private static void cloneRepository(File targetDirectory,String repositoryURL) throws GitAPIException {
        System.out.println();
        Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(Paths.get(targetDirectory.toURI()).toFile())
                .call();
    }

    private static void checkoutRepository(Git git, String commitID) throws GitAPIException {
        CheckoutCommand checkout = git.checkout();
        checkout.setName(commitID);
        checkout.call();
    }
}
