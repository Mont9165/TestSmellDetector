package testsmell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

class FileItemList {
    List<FileItem> files;

    public FileItemList (List<FileItem> files) {
        this.files = files;
    }
}

class FileItem {
    String testFileName;
    String testFilePath;
    String productionFileName;
    String productionFilePath;
    String relativeTestFilePath;
    String relativeProductionFilePath;
    int numberTestMethods;
    List<SmellItem> smells;

    public FileItem(TestFile file, List<SmellItem> smells) {
        testFileName = file.getTestFileName();
        testFilePath = file.getTestFilePath();
        productionFileName = file.getProductionFileName();
        productionFilePath = file.getProductionFilePath();
        relativeTestFilePath = file.getRelativeTestFilePath();
        relativeProductionFilePath = file.getRelativeProductionFilePath();
        numberTestMethods = file.getNumberOfTestMethods();
        this.smells = smells;
    }
}

class SmellItem {
    String smellName;
    String smellParentType;
    String parentName;
    int beginLine;
    int endLine;

    public SmellItem (String smellName, SmellyElement element) {
        this.smellName = smellName;
        parentName = element.getElementName();
        beginLine = element.getBeginLine();
        endLine = element.getEndLine();
        if (element instanceof TestClass) {
            smellParentType = "Class";
        } else{
            smellParentType = "Method";
        }
    }
}

public class SmellRecorder {
    List<FileItem> testFiles;

    public SmellRecorder() {
        testFiles = new ArrayList<>();
    }

    public void addTestFileData(TestFile testFile) {
        if (testFile.getTestSmells() != null) {
            List<SmellItem> smellsInFile = new ArrayList<>();
            for (AbstractSmell smell : testFile.getTestSmells()) {
                if (smell != null && smell.hasSmell()) {
                    for (SmellyElement element : smell.getSmellyElements()) {
                        if (element.isSmelly())
                            smellsInFile.add(new SmellItem(smell.getSmellName(), element));
                    }
                }
            }
            FileItem fileItem = new FileItem(testFile, smellsInFile);
            testFiles.add(fileItem);
        }
    }

    public void recordSmells(String repoName) throws IOException {
        String outputFile = MessageFormat.format("{0}/{1}/{2}.{3}", "results/smells", repoName, "smells_result", "json");
        FileWriter writer = new FileWriter(outputFile,false);
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        gson.toJson(testFiles, writer);
        writer.flush();
        writer.close();
    }
}
