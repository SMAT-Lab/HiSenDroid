package edu.monash.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileHelper {

    public static List<String> readFilesByLine(String filePath) throws IOException {
        Set<String> pscoutLines = new HashSet<>();
        File folder = new File(filePath);
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles) {
            if (file.isFile()) {
                //System.out.println(file.getName());
                Path path = Paths.get(filePath + file.getName());
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                pscoutLines.addAll(lines);
            }
        }

        return convertToList(pscoutLines);
    }

    public static List<String> readFile(String filePath) throws IOException {
        Set<String> pscoutLines = new HashSet<>();

        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        pscoutLines.addAll(lines);
        return convertToList(pscoutLines);
    }

    // Generic function to convert set to list
    public static <T> List<T> convertToList(Set<T> set)
    {
        return new ArrayList<>(set);
    }

    public static <T> Set<T> convertListToSet(List<T> list)
    {
        // create an empty set
        Set<T> set = new HashSet<>();

        // Add each element of list into the set
        for (T t : list)
            set.add(t);

        // return the set
        return set;
    }
}
