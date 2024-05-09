import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import file_detector.FileWalker;
import file_mapping.MappingDetector;
import file_mapping.MappingResultsWriter;
import file_mapping.MappingTestFile;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import testsmell.*;
import thresholds.DefaultThresholds;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;


public class Main {
    static List<MappingTestFile> testFiles;

    public static void main(String[] args) throws IOException, GitAPIException {
        FileReader csv = new FileReader("input/test_refactor_commits.csv");
        CSVReader csvReader = new CSVReaderBuilder(csv).build();
        List<String[]> records = csvReader.readAll();
        processRecords(records);
    }

    private static void processRecords(List<String[]> records) {
        for (String[] record : records){
            if (!record[0].equals("repository_name")){
                processRecord(record);
            }
        }
    }

    private static void processRecord(String[] record){
        String repoDir = "repo/" + record[0];
        File inputFile = new File(repoDir);
        try {
            Repository repository = openRepository(record[1]+".git", inputFile);
            Git git = new Git(repository);
            processCommit(git, repoDir, record[2]); // collect testsmell of child commit
            processCommit(git, repoDir, record[3]); // collect testsmell of parent commit
        } catch (Exception ignored) {
        }
    }

    private static void processCommit(Git git, String repoDir, String commitID) throws GitAPIException, IOException {
        checkoutRepository(git, commitID);
        collectMethodSmells(repoDir, commitID);
    }

    public static void detectMappings(String projectDir, String repoName) throws IOException {
        File inputFile = new File(projectDir);
        if (!inputFile.exists() || !inputFile.isDirectory()) {
            System.out.println("Please provide a valid path to the project directory");
            return;
        }

        List<File> srcFolder = new ArrayList<>();
        findSrcDirectory(inputFile, srcFolder);

        MappingDetector mappingDetector;
        FileWalker fw = new FileWalker();
        List<Path> files = fw.getJavaTestFiles(projectDir, true);
        testFiles = new ArrayList<>();
        for (File srcFile : srcFolder) {
            for (Path testPath : files) {
                mappingDetector = new MappingDetector();
                String str = srcFile.getAbsolutePath() + "," + testPath.toAbsolutePath();
                testFiles.add(mappingDetector.detectMapping(str));
            }
        }
        MappingResultsWriter resultsWriter = MappingResultsWriter.createResultsWriter(repoName);
        List<String> columnValues;
        for (MappingTestFile testFile : testFiles) {
            columnValues = new ArrayList<>();
            columnValues.add(0, testFile.getTestFilePath());
            columnValues.add(1, testFile.getProductionFilePath());
            resultsWriter.writeLine(columnValues);
        }
    }

    private static void findSrcDirectory(File projectDir, List<File> srcFolder) {
        if (projectDir.isDirectory()) {
            File[] files = projectDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory() && file.getName().equals("src")) {
                        // /src ディレクトリが見つかったら、その中から /main ディレクトリを探す
                        File mainDirectory = new File(file, "main");
                        if (mainDirectory.exists() && mainDirectory.isDirectory()) {
                            srcFolder.add(new File(mainDirectory.getAbsolutePath()));
                        }
                    }
                    // サブディレクトリに再帰的に探索
                    if (file.isDirectory()) {
                        findSrcDirectory(file, srcFolder);
                    }
                }
            }
        }
    }


    public static void detectSmells(String repoName) throws IOException, OutOfMemoryError {
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
//        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//        Date date;
        SmellRecorder smellRecorder = new SmellRecorder();
        for (TestFile file : testFiles) {
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


    static JsonObject readJson(FileReader jsonReader) {
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(jsonReader, JsonElement.class);
        return element.getAsJsonObject();
    }

    private static Repository openRepository(String repositoryUrl, File inputFile) throws IOException, GitAPIException {
        try {
            return Git.open(inputFile).getRepository();
        } catch (Exception e) {
            FileUtils.deleteDirectory(inputFile);
            System.out.println("Clone Repository");
            cloneRepository(repositoryUrl, inputFile.toString());
            return Git.open(inputFile).getRepository();
        }
    }

    private static void checkoutRepository(Git git, String commitID) throws GitAPIException {
        CheckoutCommand checkout = git.checkout();
        checkout.setName(commitID);
        checkout.call();
    }

    private static void cloneRepository(String repositoryUrl, String targetDirectory) throws GitAPIException {
        Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(Paths.get(targetDirectory).toFile())
                .call();
    }

    private static void collectMethodSmells(String repoDir, String commitID) throws IOException {
        String repoName = repoDir.substring(repoDir.lastIndexOf("repo/") + 5) + "/" + commitID;
        Files.createDirectories(Paths.get("results/mappings/" + repoName));
        Files.createDirectories(Paths.get("results/smells/" + repoName));

        try {
            detectMappings(repoDir, repoName);
            detectSmells(repoName);
        } catch (Exception ignored) {
        }
    }

}