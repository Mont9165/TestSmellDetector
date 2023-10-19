import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import file_detector.FileWalker;
import file_mapping.MappingDetector;
import file_mapping.MappingResultsWriter;
import file_mapping.MappingTestFile;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
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


public class Main {
    static List<MappingTestFile> testFiles;

    public static void detectMappings(String projectDir, String repoName) throws IOException {
        File inputFile = new File(projectDir);
        if (!inputFile.exists() || !inputFile.isDirectory()) {
            System.out.println("Please provide a valid path to the project directory");
            return;
        }

        List<File> srcFolder = new ArrayList<>();
        findSrcDirectory(inputFile, srcFolder);

//        if(!Objects.equals(srcDir, "")){
//            srcFolder = new File(srcDir);
//        } else {
//            srcFolder = new File(inputFile, "src/main");
//        }
//        if(!srcFolder.exists() || !srcFolder.isDirectory()) {
//            System.out.println("Please provide a valid path to the source directory");
//            return;
//        }


        MappingDetector mappingDetector;
        FileWalker fw = new FileWalker();
        List<Path> files = fw.getJavaTestFiles(projectDir, true);
        testFiles = new ArrayList<>();
//        TODO confirm this output
        for (File srcFile : srcFolder) {
            for (Path testPath : files) {
                mappingDetector = new MappingDetector();
                String str = srcFile.getAbsolutePath() + "," + testPath.toAbsolutePath();
                testFiles.add(mappingDetector.detectMapping(str));
            }
        }


        System.out.println("Saving results. Total lines:" + testFiles.size());
        MappingResultsWriter resultsWriter = MappingResultsWriter.createResultsWriter(repoName);
        List<String> columnValues;
        for (int i = 0; i < testFiles.size(); i++) {
            columnValues = new ArrayList<>();
            columnValues.add(0, testFiles.get(i).getTestFilePath());
            columnValues.add(1, testFiles.get(i).getProductionFilePath());
            resultsWriter.writeLine(columnValues);
        }

        System.out.println("Test File Mapping Completed!");
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
//                            System.out.println("Found /src/main directory at: " + mainDirectory.getAbsolutePath());
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
//        TODO　git clone. checkout, createDirectoies change, results/smellから特定のメソッドのスメルを取り出す

        FileReader jsonReader = new FileReader("input/change_methods.json");
        FileReader csv = new FileReader("input/commits_list.csv");
        CSVReader csvReader = new CSVReaderBuilder(csv).build();
        List<String[]> records = csvReader.readAll();

        JsonObject jsonObject = readJson(jsonReader);
        processJson(jsonObject, records);
    }

    private static void processJson(JsonObject jsonObject, List<String[]> records) {
        List<String> processCommitList = new ArrayList<>();

        for (Map.Entry<String, JsonElement> repoEntry : jsonObject.entrySet()) {
            String repoDir = repoEntry.getKey();

            JsonObject commitData = repoEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> commitEntry : commitData.entrySet()) {

                try {
                    processCommitEntry(repoDir, records, processCommitList, commitEntry);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static void processCommitEntry(String repoDir, List<String[]> records, List<String> processCommitList, Map.Entry<String, JsonElement> commitEntry) throws Exception {
        String commitID = commitEntry.getKey();
        String[] result = readCommitRecord(repoDir, records, commitID); // 0:repo_name

        List<String> commitList = new ArrayList<>();
        commitList.add(commitID);   // commit ID
        commitList.add(result[1]);  // parent commit ID

        File inputFile = new File(repoDir);
        Repository repository = openRepository(result[0], inputFile);
        Git git = new Git(repository);

        //TODO commitId & parentId の差分を出す

        for (String commit : commitList) {
            if (!processCommitList.contains(commit)) {
                checkoutRepository(git, commit);
                collectMethodSmells(repoDir, commit);
                processCommitList.add(commit);
            }
        }

    }

    private static JsonObject readJson(FileReader jsonReader) {
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(jsonReader, JsonElement.class);
        JsonObject jsonObject = element.getAsJsonObject();
        return jsonObject;
    }

    private static Repository openRepository(String repositoryUrl, File inputFile) throws IOException, GitAPIException {
        try {
            Repository repository = Git.open(inputFile).getRepository();
            return repository;
        } catch (Exception e) {
            System.out.println("Clone Repository");

            cloneRepository(repositoryUrl, inputFile.toString());
            Repository repository = Git.open(inputFile).getRepository();
            return repository;
        }
    }

    private static String[] readCommitRecord(String repoName, List<String[]> records, String commitID) {
        String[] result = new String[2];

        String repo = repoName.substring(repoName.lastIndexOf("repo/") + 5);
        for (String[] record : records) {
            if (!record[0].equals("repository_name")) {
                if (record[1].contains(repo) && record[2].equals(commitID)) {
                    result[0] = record[1] + ".git"; // repository url
                    result[1] = record[3];  // parent commit id
                    break;
                }
            }
        }
        return result;
    }

    private static void checkoutRepository(Git git, String commitID) throws GitAPIException {
        CheckoutCommand checkout = git.checkout();
        checkout.setName(commitID);
        checkout.call();
//        System.out.println("checkout");
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

        detectMappings(repoDir, repoName);
        detectSmells(repoName);
    }

}
