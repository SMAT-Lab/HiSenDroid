package edu.anonymous.utils;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CsvHelper {

    public static void main(String[] args) throws IOException, CsvValidationException {
        //csv file containing data
        String strFile = "/Users/xsun0035/workspace/HSO-related/goodware_common_cases/triggerCondition.csv";
        FileWriter csvWriter = new FileWriter("/Users/xsun0035/workspace/HSO-related/goodware_common_cases/triggerCondition_.csv");

        CSVReader reader = new CSVReader(new FileReader(strFile));
        String[] nextLine;
        Set<String> allData = new HashSet<>();
        List<List<String>> itemSets = new ArrayList<>();
        int lineNumber = 0;
        while ((nextLine = reader.readNext()) != null) {
            lineNumber++;
            System.out.println("Line # " + lineNumber);

            List<String> itemSet = new ArrayList<>();
            for (String s : Arrays.asList(nextLine)) {
                //Pattern pattern = Pattern.compile("([a-zA-Z.]+)\\(", Pattern.DOTALL);
                //Pattern pattern = Pattern.compile("(<[a-zA-Z.]+)", Pattern.DOTALL);
//                Matcher matcher = pattern.matcher(s);
//                if (matcher.find()) {
//                    String className = matcher.group(1);
//                    itemSet.add(className);
//                }
                String newString = s.replaceAll("\"", "\'");
                itemSet.add("\""+newString+"\"");
            }

            itemSets.add(itemSet);
            allData.addAll(itemSet);
        }

        List<List<String>> itemSetResults = new ArrayList<>();
        List<String> allInvokeExprList = set2List(allData);
        List<String> allInvokeExprListNotEmpty = allInvokeExprList.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toList());
        for (List<String> itemSet : itemSets) {
            String[] itemSetArray = new String[allInvokeExprListNotEmpty.size()];
            Arrays.fill(itemSetArray, "0");

            if (CollectionUtils.isEmpty(itemSet)) {
                continue;
            }
            for (int i = 0; i < allInvokeExprListNotEmpty.size(); i++) {
                boolean match = false;
                for (String item : itemSet) {
                    if (allInvokeExprListNotEmpty.get(i).equals(item)) {
                        match = true;
                    }
                }
                if (match) {
                    itemSetArray[i] = "1";
                }
            }

            List<String> itemSetResult = Arrays.asList(itemSetArray);
            itemSetResults.add(itemSetResult);
        }

        for (String x : allInvokeExprListNotEmpty) {
            csvWriter.append(x);
            csvWriter.append(",");
        }

        csvWriter.append("\n");

        for (List<String> itemSet : itemSetResults) {
            if (CollectionUtils.isNotEmpty(itemSet)) {
                csvWriter.append(String.join(",", itemSet));
                csvWriter.append("\n");
            }
        }

        csvWriter.flush();
        csvWriter.close();

    }

    private static List<String> set2List(Set<String> s) {
        int n = s.size();
        List<String> aList = new ArrayList<String>(n);
        for (String x : s)
            aList.add(x);
        return aList;
    }

}
