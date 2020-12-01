package edu.monash;

import com.opencsv.CSVWriter;
import edu.monash.help.EntryPointHelper;
import edu.monash.model.BranchUnit;
import edu.monash.model.UnitInfo;
import edu.monash.utils.ApplicationClassFilter;
import edu.monash.utils.CollectionHelper;
import edu.monash.utils.FileHelper;
import edu.monash.utils.cgHelper;
import edu.psu.cse.siis.coal.*;
import edu.psu.cse.siis.coal.arguments.ArgumentValueManager;
import edu.psu.cse.siis.coal.lang.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.jimple.IfStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException, ParseException, XmlPullParserException {

        String apkPath = args[0];
        String forceAndroidJar = args[1];
        String psCoutPath = args[2];

        long startTime = System.currentTimeMillis();
        System.out.println("==>START TIME:" + startTime);

        //Collect pscoutAPIs
        collectPscoutAPIs(psCoutPath);
        long pscoutTime = System.currentTimeMillis();
        System.out.println("==>AFTER PSCOUT TIME:" + pscoutTime);

        //init
        JimpleBasedInterproceduralCFG iCfg = initialize(apkPath, forceAndroidJar);
        long initTime = System.currentTimeMillis();
        System.out.println("==>AFTER INIT TIME:" + initTime);

        /**
         * Hidden senditive behavior analysis
         * Step 1:Extract IF_ELSE branch invocation expressions separately
         * Step 2:Mark the hidden sensitive branches
         * Step 3:Trigger Condition Back Track
         */
        hiddenSensitiveBranchAnalysis(iCfg);

        long afterHSBAnalysisTime = System.currentTimeMillis();
        System.out.println("==>ATFER HSB ANALYSIS TIME:" + afterHSBAnalysisTime);

        //Print Hidden Sensitive Branch Info on console
        printHiddenSensitiveBranchInfo();

        //output triggerConditions and sensitive APIs to csv
        //outputHSO2csv(triggerAPICsvPath, hsoAPICsvPath);

        //Generate a new SourceSinkFile include hidden sensitive APIs
        //String destinationPath = generateSourceSinkFile(apkPath);

        //data flow analysis
        //String destinationPath = "res/SourcesAndSinks.txt";
        //InfoflowResults infoflowResults = dataFlowAnalysis(apkPath, forceAndroidJar, destinationPath);

        //output source -> sinks And trigger conditions to csv
        //outputResult(source2SinkCsvPath, triggerAPICsvPath, infoflowResults);

        long finalTime = System.currentTimeMillis();
        System.out.println("==>FINAL TIME:" + finalTime);

    }

    private static void outputHSO2csv(String triggerAPICsvPath, String hsoAPICsvPath) throws IOException {
        CSVWriter hsoAPICsvPathWriter = new CSVWriter(new FileWriter(hsoAPICsvPath, true));
        CSVWriter triggerAPICsvPathWriter = new CSVWriter(new FileWriter(triggerAPICsvPath, true));

        for (BranchUnit branchUnit : GlobalRef.branchUnits) {
            if (!branchUnit.containHSO) {
                continue;
            }

            //Append HSO APIs to csv
            if (CollectionUtils.isNotEmpty(branchUnit.uniqueSensitiveAPIs)) {
                hsoAPICsvPathWriter.writeNext(batchRegularMatchAPI(branchUnit.uniqueSensitiveAPIs).toArray(new String[0]));
            }

            //Append trigger condition to csv
            Set<String> branchConditionSet = branchUnit.branchConditionValues.stream().map(branchCondition -> {
                return convert2PatternMethod(branchCondition);
            }).filter(bc -> {
                return ApplicationClassFilter.isClassSystemPackage(bc);
            }).collect(Collectors.toSet());
            triggerAPICsvPathWriter.writeNext(batchRegularMatchAPI(branchConditionSet).toArray(new String[0]));
        }

        hsoAPICsvPathWriter.close();
        triggerAPICsvPathWriter.close();
    }

    private static void outputResult(String source2SinkCsvPath, String triggerAPICsvPath, InfoflowResults infoflowResults) throws IOException {
        CSVWriter source2SinkCsvPathWriter = new CSVWriter(new FileWriter(source2SinkCsvPath, true));
        //CSVWriter triggerAPICsvPathWriter = new CSVWriter(new FileWriter(triggerAPICsvPath, true));

        for (ResultSinkInfo sink : infoflowResults.getResults().keySet()) {
            for (ResultSourceInfo source : infoflowResults.getResults().get(sink))
                GlobalRef.source2sink.put(regularMatchAPI(source.getStmt().toString()), regularMatchAPI(sink.getStmt().toString()));
        }

        for (BranchUnit branchUnit : GlobalRef.branchUnits) {
            if (!branchUnit.containHSO) {
                continue;
            }

            for (String sourceAPI : branchUnit.uniqueSensitiveAPIs) {
                if (null != GlobalRef.source2sink.get(sourceAPI)) {
                    branchUnit.hasHSDataLeaks = true;
                    //Append source and sink to csv
                    List<String> item = new ArrayList<>();
                    item.add(sourceAPI);
                    item.add(GlobalRef.source2sink.get(sourceAPI));
                    source2SinkCsvPathWriter.writeNext(item.toArray(new String[0]));
                }
            }

//            if(branchUnit.hasHSDataLeaks){
//                //Append trigger condition to csv
//                triggerAPICsvPathWriter.writeNext(batchRegularMatchAPI(branchUnit.branchConditionValues).toArray(new String[0]));
//            }
        }

        source2SinkCsvPathWriter.close();
        //triggerAPICsvPathWriter.close();
    }

    private static void collectPscoutAPIs(String psCoutPath) throws IOException {
        List<String> pscoutAPIs = FileHelper.readFile(psCoutPath);
        Map<String, String> loopMap = pscoutAPIs.stream().collect(Collectors.toMap(k -> k, Function.identity()));
        GlobalRef.pscoutAPIMap = loopMap;
    }

    private static void hiddenSensitiveBranchAnalysis(JimpleBasedInterproceduralCFG iCfg) {
        Scene.v().getApplicationClasses().forEach(aClass -> {

            aClass.getMethods().stream().filter(am -> {
                return am.isConcrete()
                        && !ApplicationClassFilter.isClassInSystemPackage(am.getDeclaringClass().getName())
                        && !am.getSignature().contains("dummyMainClass");
            }).forEach(targetMethod -> {
                DirectedGraph<Unit> ug = iCfg.getOrCreateUnitGraph(targetMethod.retrieveActiveBody());
                Iterator<Unit> uit = ug.iterator();
                List<Unit> units = new ArrayList<>();
                uit.forEachRemaining(units::add);

                for (int i = 0; i < units.size(); i++) {
                    Unit u = units.get(i);
                    SootMethod method = AnalysisParameters.v().getIcfg().getMethodOf(u);
                    if (method == null) {
                        //System.out.println("-----error---"+targetMethod.getSignature());
                        continue;
                    }
                    if (u.branches()) {
                        //If-ELSE Stmt
                        if (u instanceof IfStmt) {
                            IfStmt uStmt = (IfStmt) u;
                            BranchUnit branchUnit = new BranchUnit();
                            branchUnit.declareMethod = targetMethod;
                            branchUnit.branchInvokeUnit = u;

                            //Extract IF_ELSE branch invocation expressions separately
                            extractBranchAPIs(ug, u, method, branchUnit);

                            //Mark the markBranchDifference
                            markBranchDifference(branchUnit);

                            //Trigger Condition Back Track
                            if (triggerConditionBackTrack(ug, u, uStmt, branchUnit)) continue;

                            //Mark the hidden sensitive branches
                            markHSB(branchUnit);

                            GlobalRef.branchUnits.add(branchUnit);
                        }
                    }
                }
            });
        });
    }

    private static JimpleBasedInterproceduralCFG initialize(String apkPath, String forceAndroidJar) throws FileNotFoundException, ParseException {
        //calculate EntryPoint to generate dummyMainMethod
        init(apkPath, forceAndroidJar);
        System.out.println(apkPath);

        // Initialize Soot
        SetupApplication analyser = new SetupApplication(forceAndroidJar, apkPath);
        analyser.constructCallgraph();

        //Initialize COAL Model
        String[] coalArgs = {
                "-model", GlobalRef.coalModelPath,
                "-cp", forceAndroidJar,
                "-input", GlobalRef.SOOTOUTPUT
        };
        DefaultCommandLineParser parser = new DefaultCommandLineParser();
        DefaultCommandLineArguments commandLineArguments = parser.parseCommandLine(coalArgs, DefaultCommandLineArguments.class);
        Model.loadModel(commandLineArguments.getModel());

        JimpleBasedInterproceduralCFG iCfg = new PropagationIcfg();
        AnalysisParameters.v().setIcfg(iCfg);
        ArgumentValueManager.v().registerDefaultArgumentValueAnalyses();

        GlobalRef.iCfg = iCfg;
        return iCfg;
    }

    private static void printHiddenSensitiveBranchInfo() {
        for (BranchUnit branchUnit : GlobalRef.branchUnits) {
            if (!branchUnit.containHSO) {
                continue;
            }

            System.out.println("------------------------------Sensitive BranchInvokeMethods----------------------------------");
            System.out.println("Declare Method:" + branchUnit.declareMethod.getSignature());
            String branchInvokeUnit = branchUnit.branchInvokeUnit.toString();
            System.out.println("If Statement:" + branchInvokeUnit);
            String lineSeparator = System.getProperty("line.separator");
            System.out.println(lineSeparator);

            if (CollectionUtils.isNotEmpty(branchUnit.branchConditionValues)) {
                branchUnit.branchConditionValues.forEach(branchConditionValue -> {
                    System.out.println("Trigger Condition Block:" + branchConditionValue);
                });
            }

            System.out.println(lineSeparator);

            System.out.println("if{");
            if (null != branchUnit.ifBranchInvokeMethods && !branchUnit.ifBranchInvokeMethods.isEmpty()) {
                branchUnit.ifBranchInvokeMethods.forEach(iu -> {
                    if (iu.isSensitive()) {
                        System.out.println("[IF-SENSITIVE]" + iu.getUnit().toString() + " --> " + iu.getDeclareMethod().toString());
                    } else {
                        System.out.println("[IF]" + iu.getUnit().toString() + " --> " + iu.getDeclareMethod().toString());
                    }
                });
            }

            System.out.println("}else{");

            if (null != branchUnit.elseBranchInvokeMethods && !branchUnit.elseBranchInvokeMethods.isEmpty()) {
                branchUnit.elseBranchInvokeMethods.forEach(eu -> {
                    if (eu.isSensitive()) {
                        System.out.println("[ELSE-SENSITIVE]" + eu.getUnit().toString() + " --> " + eu.getDeclareMethod().toString());
                    } else {
                        System.out.println("[ELSE]" + eu.getUnit().toString() + " --> " + eu.getDeclareMethod().toString());
                    }
                });
            }
            System.out.println("}");

        }
    }

    private static String generateSourceSinkFile(String apkPath) throws IOException {
        Pattern pattern = Pattern.compile("(<.*>)", Pattern.DOTALL);
        String fileName = FilenameUtils.removeExtension(apkPath).substring(apkPath.lastIndexOf("/") + 1);
        String sourcesAndSinksFile = "res/SourcesAndSinks.txt";
        String destinationPath = "res/SourcesAndSinks" + fileName + ".txt";
        Files.copy(Paths.get(sourcesAndSinksFile), Paths.get(destinationPath), StandardCopyOption.REPLACE_EXISTING);

        if (CollectionUtils.isNotEmpty(GlobalRef.hiddenSensitiveAPIs)) {
            for (String s : GlobalRef.hiddenSensitiveAPIs) {
                try {
                    Matcher matcher = pattern.matcher(s);
                    if (matcher.find()) {
                        String invokeExpr = matcher.group(1);
                        Files.write(Paths.get(destinationPath), (invokeExpr + " -> _SOURCE_" + "\n").getBytes(), StandardOpenOption.APPEND);
                    }
                } catch (IOException e) {
                    //exception handling left as an exercise for the reader
                }
            }
        }
        return destinationPath;
    }

    public static InfoflowResults dataFlowAnalysis(String apkPath, String forceAndroidJar, String destinationPath) throws IOException, XmlPullParserException {
        SetupApplication setupApplication = new SetupApplication(forceAndroidJar, apkPath);
        setupApplication.getConfig().setImplicitFlowMode(InfoflowConfiguration.ImplicitFlowMode.NoImplicitFlows);
        setupApplication.getConfig().getSourceSinkConfig().setLayoutMatchingMode(InfoflowAndroidConfiguration.LayoutMatchingMode.MatchSensitiveOnly);
        InfoflowResults infoflowResults = setupApplication.runInfoflow(destinationPath);
        //System.out.println(infoflowResults);
        if (CollectionUtils.isNotEmpty(infoflowResults.getResultSet())) {
            infoflowResults.getResultSet().forEach(dataLeak -> {
                System.out.println("------------------------------Data Leaks----------------------------------");
                System.out.println("[SOURCE]" + dataLeak.getSource());
                System.out.println("[SINK]" + dataLeak.getSink());
            });
        }
        return infoflowResults;
    }

    private static List<String> set2List(Set<String> s) {
        int n = s.size();
        List<String> aList = new ArrayList<String>(n);
        for (String x : s)
            aList.add(x);
        return aList;
    }

    // Generic function to convert set to list
    public static <T> List<T> convertToList(Set<T> set) {
        return new ArrayList<>(set);
    }

    private static boolean isSensitiveAPI(Unit branchUnit) {
        Pattern pattern = Pattern.compile("(<.*>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(branchUnit.toString());
        if (matcher.find()) {
            String invokeExpr = matcher.group(1);
            return GlobalRef.pscoutAPIMap.containsKey(invokeExpr);
        }
        return false;
    }

    private static String regularMatchAPI(String input) {
        Pattern pattern = Pattern.compile("(<.*>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static List<String> regularMatchParams(String input) {
        String s = "";
        Pattern pattern = Pattern.compile("(\\(\\$.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            s = matcher.group(1);
        }

        s = s.replaceAll("\\(", "");
        s = s.replaceAll("\\)", "");
        return Arrays.asList(s.split(","));
    }

    private static List<String> batchRegularMatchAPI(List<String> input) {
        List<String> regularAPIs = new ArrayList<>();
        if (CollectionUtils.isEmpty(input)) {
            return new ArrayList<>();
        }
        input.forEach(item -> {
            regularAPIs.add(regularMatchAPI(item));
        });

        return regularAPIs.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static List<String> batchRegularMatchAPI(Set<String> input) {
        List<String> regularAPIs = new ArrayList<>();
        if (CollectionUtils.isEmpty(input)) {
            return new ArrayList<>();
        }
        input.forEach(item -> {
            regularAPIs.add(regularMatchAPI(item));
        });

        return regularAPIs.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static void extractBranchAPIs(DirectedGraph<Unit> ug, Unit u, SootMethod method, BranchUnit branchUnit) {
        List<Unit> list = ug.getSuccsOf(u);
        List<Unit> realList = list.stream().filter(s -> {
            return !s.toString().contains("@caughtexception");
        }).collect(Collectors.toList());

        if (realList.size() == 1) {
            //If Branch
            Stmt ifStmt = (Stmt) realList.get(0);
            List<UnitInfo> ifUnits = getAllBranchStmts(ug, method, ifStmt);
            branchUnit.ifBranchInvokeMethods = ifUnits;
            branchUnit.ifBranchInvokeMethodList = ifUnits.stream().map(unit -> {
                return unit.getUnit().toString();
            }).collect(Collectors.toList());
        } else if (realList.size() > 1) {
            //If Branch
            Stmt ifStmt = (Stmt) realList.get(0);
            List<UnitInfo> ifUnits = getAllBranchStmts(ug, method, ifStmt);
            branchUnit.ifBranchInvokeMethods = ifUnits;
            branchUnit.ifBranchInvokeMethodList = ifUnits.stream().map(unit -> {
                return unit.getUnit().toString();
            }).collect(Collectors.toList());

            //ELSE Branch
            Stmt elseStmt = (Stmt) realList.get(1);
            List<UnitInfo> elseUnits = getAllBranchStmts(ug, method, elseStmt);
            branchUnit.elseBranchInvokeMethods = elseUnits;
            branchUnit.elseBranchInvokeMethodList = elseUnits.stream().map(unit -> {
                return unit.getUnit().toString();
            }).collect(Collectors.toList());
        }
    }

    public static void copy(File file, File destFile) {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            bos = new BufferedOutputStream(new FileOutputStream(destFile));
            byte[] bys = new byte[1024];
            int len = 0;
            while ((len = bis.read(bys)) != -1) {
                bos.write(bys, 0, len);

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static List<UnitInfo> getAllBranchStmts(DirectedGraph<Unit> ug, SootMethod method, Stmt originStmt) {
        List<Stmt> allElseBranchStmt = findBranchStmts(originStmt, ug, new HashSet<>());
        List<UnitInfo> allUnits = new ArrayList<>();
        for (Stmt elseBranchStmt : allElseBranchStmt) {
            if (elseBranchStmt.containsInvokeExpr() && !ApplicationClassFilter.isClassSystemPackage(convert2PatternMethod(elseBranchStmt.toString()))
            ) {
                allUnits.addAll(cgHelper.collectInvokeAPIs(elseBranchStmt, method));
            } else {
                UnitInfo unitInfo = new UnitInfo(elseBranchStmt, method.getSignature());
                allUnits.add(unitInfo);
            }
        }
        return allUnits;
    }

    private static List<Stmt> findBranchStmts(Stmt originStmt, DirectedGraph<Unit> ug, Set<Unit> visitedNodes) {
        List<Stmt> branchStmts = new ArrayList<>();

        Stmt currentStmt = originStmt;
        if(visitedNodes.contains(currentStmt)){
            return branchStmts;
        }
        visitedNodes.add(currentStmt);

        List<Unit> nodes = ug.getSuccsOf(currentStmt);

        List<Unit> realNodes = nodes.stream().filter(s -> {
            return !s.toString().contains("@caughtexception");
        }).collect(Collectors.toList());

        if (realNodes.size() == 0) {
            branchStmts.add(currentStmt);
            return branchStmts;
        }
        if (realNodes.size() == 1) {
            branchStmts.add(currentStmt);
            branchStmts.addAll(findBranchStmts((Stmt) realNodes.get(0), ug, visitedNodes));
        }
        return branchStmts;
    }

    public static void init(String apkPath, String forceAndroidJar) {
        try {
            EntryPointHelper.calculateEntryPoint(apkPath, forceAndroidJar);
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
            System.out.println("==>calculateEntryPoint error:" + e);
        }
    }

    private static void specifyResult(BranchUnit branchUnit, Object o) {
        String oStr = SpecifyString(branchUnit.declareMethod, o);
        branchUnit.branchConditionValues.add(oStr);
    }

    private static String SpecifyString(SootMethod declareMethod, Object o) {
        String oStr = o.toString();
        if (oStr.startsWith(Constants.CONSTANT) && !oStr.contains(Constants.DIRECTION)) {
            oStr = oStr + Constants.DIRECTION + declareMethod.getSignature();
        }

        return oStr;
    }

    private static void markBranchDifference(BranchUnit branchUnit) {
        Set<String> sensitiveAPIsInIfBranch = new HashSet<>();
        Set<String> sensitiveAPIsInElseBranch = new HashSet<>();

        if (CollectionUtils.isNotEmpty(branchUnit.ifBranchInvokeMethods)) {
            branchUnit.ifBranchInvokeMethods.forEach(ifunit -> {
                if (isSensitiveAPI(ifunit.getUnit())) {
                    ifunit.setSensitive(true);
                    branchUnit.ifBranchHasSensitiveApi = true;
                    sensitiveAPIsInIfBranch.add(regularMatchAPI(ifunit.getUnit().toString()));
                }
            });
        }

        if (CollectionUtils.isNotEmpty(branchUnit.elseBranchInvokeMethods)) {
            branchUnit.elseBranchInvokeMethods.forEach(elseunit -> {
                if (isSensitiveAPI(elseunit.getUnit())) {
                    elseunit.setSensitive(true);
                    branchUnit.elseBranchHasSensitiveApi = true;
                    sensitiveAPIsInElseBranch.add(regularMatchAPI(elseunit.getUnit().toString()));
                }
            });
        }


        Set<String> uniqueSensitiveAPIs = CollectionHelper.getUniqueElements(sensitiveAPIsInIfBranch, sensitiveAPIsInElseBranch);
        if (CollectionUtils.isNotEmpty(uniqueSensitiveAPIs)) {
            branchUnit.uniqueSensitiveAPIs.addAll(uniqueSensitiveAPIs);
            GlobalRef.hiddenSensitiveAPIs.addAll(uniqueSensitiveAPIs);
        }


        Set<String> ifBranchInvokeMethodSet = branchUnit.ifBranchInvokeMethodList.stream().filter(ifBranchInvokeMethod -> {
            return contain2PatternMethod(ifBranchInvokeMethod);
        }).map(item -> {
            return convert2PatternMethod(item);
        }).collect(Collectors.toSet());

        Set<String> elseBranchInvokeMethodSet = branchUnit.elseBranchInvokeMethodList.stream().filter(elseBranchInvokeMethod -> {
            return contain2PatternMethod(elseBranchInvokeMethod);
        }).map(item -> {
            return convert2PatternMethod(item);
        }).collect(Collectors.toSet());

        Set<String> intersection = new HashSet<String>(ifBranchInvokeMethodSet);
        intersection.retainAll(elseBranchInvokeMethodSet);
        Set<String> filterIntersection = intersection.stream().filter(item -> {
            return !item.startsWith("<java.lang");
        }).collect(Collectors.toSet());
        boolean isBranchDifferent = CollectionUtils.isEmpty(filterIntersection);


        branchUnit.isBranchDifferent = (branchUnit.ifBranchHasSensitiveApi || branchUnit.elseBranchHasSensitiveApi)
                && CollectionUtils.isNotEmpty(uniqueSensitiveAPIs);
    }

    /**
     * 1.APIs in trigger condition is not relavent to the APIs in branches
     * 2.These exist differences between IF and ELSE branch
     * 3.Trigger condition contains system APIs/variables
     *
     * @param branchUnit
     */
    private static void markHSB(BranchUnit branchUnit) {
        if (!branchUnit.isBranchDifferent) {
            return;
        }

        Set<String> sensitiveAPIsInIfBranch = new HashSet<>();
        Set<String> sensitiveAPIsInElseBranch = new HashSet<>();

        if (CollectionUtils.isNotEmpty(branchUnit.ifBranchInvokeMethods)) {
            branchUnit.ifBranchInvokeMethods.forEach(ifunit -> {
                if (isSensitiveAPI(ifunit.getUnit())) {
                    ifunit.setSensitive(true);
                    branchUnit.ifBranchHasSensitiveApi = true;
                    sensitiveAPIsInIfBranch.add(regularMatchAPI(ifunit.getUnit().toString()));
                }
            });
        }

        if (CollectionUtils.isNotEmpty(branchUnit.elseBranchInvokeMethods)) {
            branchUnit.elseBranchInvokeMethods.forEach(elseunit -> {
                if (isSensitiveAPI(elseunit.getUnit())) {
                    elseunit.setSensitive(true);
                    branchUnit.elseBranchHasSensitiveApi = true;
                    sensitiveAPIsInElseBranch.add(regularMatchAPI(elseunit.getUnit().toString()));
                }
            });
        }

        branchUnit.sensitiveAPIsInIfBranch.addAll(sensitiveAPIsInIfBranch);
        branchUnit.sensitiveAPIsInelseBranch.addAll(sensitiveAPIsInElseBranch);

        /**
         * calculate variables
         */
        String tmp = getSubUtilSimple(branchUnit.branchInvokeUnit.toString(), "if(.*?)goto ");
        Set<String> vars = variablePattern(tmp);
        branchUnit.variableInTrigger.addAll(vars);
        if (containsKeyword(branchUnit.branchConditionValues.get(0), branchUnit.variableInTrigger)) {
            branchUnit.variableInTrigger.addAll(varPattern(branchUnit.branchConditionValues.get(0)));
        }

        for (UnitInfo ifStmt : branchUnit.ifBranchInvokeMethods) {
            if (ifStmt.getDeclareMethod().toString().contains(branchUnit.declareMethod.toString())) {
                branchUnit.variableInIfBranch.addAll(variablePattern(ifStmt.getUnit().toString()));
            }
        }

        for (UnitInfo elseStmt : branchUnit.elseBranchInvokeMethods) {
            if (elseStmt.getDeclareMethod().toString().contains(branchUnit.declareMethod.toString())) {
                branchUnit.variableInElseBranch.addAll(variablePattern(elseStmt.getUnit().toString()));
            }
        }

        /**
         * mark common cases
         */
        markCommonCases(branchUnit);

        /**
         *  markDataDependency
         */
        markDataDependency(branchUnit);

        /**
         * 3.Trigger condition contains system APIs/variables
         */
        markTCSystemAPI(branchUnit);

        /**
         * markConditionBranchDiff
         */
        markConditionBranchDiff(branchUnit);

        /**
         * mark branch HSO
         */
        isBrancnHSO(branchUnit);

    }

    public static void isBrancnHSO(BranchUnit branchUnit) {
        //判断if分支是否满足hso条件
        if (!branchUnit.ifHasDataDependency && branchUnit.ifBranchHasSensitiveApi && branchUnit.tcContainsSys && !branchUnit.triggerBranchHasSameAPIInIf) {
            branchUnit.ifBranchIsHSO = true;
        }

        //判断else分支是否满足hso条件
        if (!branchUnit.elseHasDataDependency && branchUnit.elseBranchHasSensitiveApi && branchUnit.tcContainsSys && !branchUnit.triggerBranchHasSameAPIInElse) {
            branchUnit.elseBranchIsHSO = true;
        }

        branchUnit.containHSO = (branchUnit.ifBranchIsHSO || branchUnit.elseBranchIsHSO);
        //branchUnit.containHSO = (branchUnit.ifBranchIsHSO || branchUnit.elseBranchIsHSO) && !branchUnit.hasCommonCaese;
        //branchUnit.containHSO = branchUnit.tcContainsSys;
    }

    public static void markConditionBranchDiff(BranchUnit branchUnit) {
        Set<String> branchConditionSet = branchUnit.triggerConditions.stream().map(branchCondition -> {
            return getSubUtilSimple(branchCondition, "(<.*:)");
        }).filter(bc -> {
            return ApplicationClassFilter.isClassSystemPackage(bc);
        }).collect(Collectors.toSet());

        Set<String> ifBranchInvokeMethodSet = branchUnit.ifBranchInvokeMethodList.stream().map(ifBranchInvokeMethod -> {
            return getSubUtilSimple(ifBranchInvokeMethod, "(<.*:)");
        }).filter(bc -> {
            return ApplicationClassFilter.isClassSystemPackage(bc);
        }).collect(Collectors.toSet());

        Set<String> elseBranchInvokeMethodSet = branchUnit.elseBranchInvokeMethodList.stream().map(ifBranchInvokeMethod -> {
            return getSubUtilSimple(ifBranchInvokeMethod, "(<.*:)");
        }).filter(bc -> {
            return ApplicationClassFilter.isClassSystemPackage(bc);
        }).collect(Collectors.toSet());

        Set<String> intersection1 = new HashSet<String>(branchConditionSet);
        intersection1.retainAll(ifBranchInvokeMethodSet);
        Set<String> filterIntersection1 = intersection1.stream().filter(item -> {
            return !item.startsWith("<java.lang");
        }).collect(Collectors.toSet());

        Set<String> intersection2 = new HashSet<String>(branchConditionSet);
        intersection2.retainAll(elseBranchInvokeMethodSet);
        Set<String> filterIntersection2 = intersection2.stream().filter(item -> {
            return !item.startsWith("<java.lang");
        }).collect(Collectors.toSet());
        ;

        if (CollectionUtils.isNotEmpty(filterIntersection1)) {
            branchUnit.triggerBranchHasSameAPIInIf = true;
        }

        if (CollectionUtils.isNotEmpty(filterIntersection2)) {
            branchUnit.triggerBranchHasSameAPIInElse = true;
        }
    }

    public static void markTCSystemAPI(BranchUnit branchUnit) {
        Set<String> triggerConditions = new HashSet<>();
        for(String item : branchUnit.branchConditionValues){
            String api = getSubUtilSimple(item, "(<.*>)");
            if(api.contains("currentTimeMillis")){
                triggerConditions.add(api);
            }
            if (
                !api.startsWith("<java.lang")&&
                    !api.startsWith("<java.util.List") && !api.startsWith("<java.util.ArrayList")
                            && !api.startsWith("<java.util.Map") && !api.startsWith("<java.util.concurrent.")
                            && !api.startsWith("<java.util.Arrays") && !api.startsWith("<java.util.HashMap")
                            && !api.startsWith("<java.util.Iterator:") && !api.startsWith("<java.util.Collections:")
                            && !api.startsWith("<java.util.HashSet:") && !api.startsWith("<java.util.LinkedList:")
                            && !api.startsWith("<java.util.Hashtable:") && !api.startsWith("<java.util.Set:")
                            && !api.startsWith("<java.util.LinkedHashMap:") && !api.startsWith("<java.util.BitSet:")
                            && !api.startsWith("<java.util.Vector") && !api.startsWith("<java.util.Queue:")
                            && !api.startsWith("<android.util.SparseArray:")
                            && !api.startsWith("<android.util.Pair:")
                            && !api.startsWith("<java.text.NumberFormat")
                            && !api.startsWith("<java.text.SimpleDateFormat:")
                            && !api.startsWith("<android.text.TextUtils: boolean equals")
                            && !api.startsWith("<android.util.Log:") && !api.startsWith("<android.text.TextUtils: boolean isEmpty")
                            && !api.contains("Exception:")
                            && !api.contains("<init>")
                            && !api.contains("<android.util.SparseArray:")
                            && !api.contains("<java.util.Stack:")
                            && !api.contains("<java.util.regex.Matcher: boolean matches()>")
                            && !api.contains("<com.google.gson.")
                            && !api.contains("<java.util.regex.")
                            && !api.contains("<android.util.Base64")
                            && !api.contains("<java.util.Collection:")
                            && !api.contains("<android.util.FloatMath")
                            && !api.contains("<java.util.SortedMap:")
                            && !api.contains("<android.util.ArrayMap:")
            ){
                triggerConditions.add(api);
            }
        }

        branchUnit.triggerConditions = triggerConditions;

        if (CollectionUtils.isEmpty(triggerConditions)) {
            branchUnit.tcContainsSys = false;
        }else{
            for (String tc : triggerConditions) {
                if (ApplicationClassFilter.isClassSystemPackage(tc)) {
                    branchUnit.tcContainsSys = true;
                }
            }
        }
    }

    public static void markDataDependency(BranchUnit branchUnit) {
        Set<String> intersection1 = new HashSet<>(branchUnit.variableInTrigger);
        intersection1.retainAll(branchUnit.variableInIfBranch);
        boolean ifHasDataDependency = CollectionUtils.isNotEmpty(intersection1);

        Set<String> intersection2 = new HashSet<>(branchUnit.variableInTrigger);
        intersection2.retainAll(branchUnit.variableInElseBranch);
        boolean elseHasDataDependency = CollectionUtils.isNotEmpty(intersection2);

        branchUnit.ifHasDataDependency = ifHasDataDependency;
        branchUnit.elseHasDataDependency = elseHasDataDependency;

    }

    public static void markCommonCases(BranchUnit branchUnit) {
        Set<String> sensitiveAPIs = new HashSet<>();
        sensitiveAPIs.addAll(branchUnit.sensitiveAPIsInIfBranch);
        sensitiveAPIs.addAll(branchUnit.sensitiveAPIsInelseBranch);
        Set<String> triggerAPIs = new HashSet<>();
        triggerAPIs.addAll(branchUnit.branchConditionValues);

        List<String> commonCases = branchUnit.branchConditionValues.stream().filter(item -> {
            String tcbAPI = "";
            Pattern pattern = Pattern.compile("(<.*?>)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(item);
            if (matcher.find()) {
                tcbAPI = matcher.group(1);
            }
            boolean excepptionCases = false;
            excepptionCases = (checkSpecificAPI("<java.util.Locale", triggerAPIs) || checkSpecificAPI("location", triggerAPIs) || checkSpecificAPI("Address", triggerAPIs) || checkSpecificAPI("getSimCountryIso", triggerAPIs)
                    || checkSpecificAPI("Calendar", triggerAPIs) || checkSpecificAPI("Date", triggerAPIs) || checkSpecificAPI("time", triggerAPIs)) && !(checkSpecificAPI("android.permission.ACCESS_FINE_LOCATION", triggerAPIs) || checkSpecificAPI("android.permission.ACCESS_COARSE_LOCATION", triggerAPIs) || checkSpecificAPI("<android.content.Context: java.lang.Object getSystemService(java.lang.String)>(\"location\")", triggerAPIs) || checkSpecificAPI("<android.location.LocationManager: android.location.Location getLastKnownLocation", triggerAPIs))
            ;
            if (excepptionCases) {
                return false;
            } else {

                return
                        tcbAPI.contains("permission") || tcbAPI.contains("Permission") || item.contains("android.permission.")
                                || (tcbAPI.contains("getSystemService"))
                                || tcbAPI.contains("<android.os.Build$VERSION: int SDK_INT>")
                                || tcbAPI.contains("<android.os.Build$VERSION: java.lang.String SDK>")
                                || tcbAPI.startsWith("<com.google.android.gms.gcm.GoogleCloudMessaging:")
                                || tcbAPI.startsWith("<com.google.android.gms.gcm.a:")
                                || tcbAPI.startsWith("<android.os.Looper")
                                || (checkSpecificAPI("<android.location.LocationManager: android.location.Location getLastKnownLocation", triggerAPIs) && checkSpecificAPI("<android.location.LocationManager: boolean isProviderEnabled(java.lang.String)>", sensitiveAPIs))

                                || tcbAPI.startsWith("<android.content.Intent: java.lang.String getAction()>")
                                || tcbAPI.startsWith("<android.content.Intent:")
                                || tcbAPI.startsWith("<android.content.SharedPreferences")
                                //file
                                || tcbAPI.startsWith("<java.io.File: boolean exists()>")
                                || (tcbAPI.startsWith("<android.os.Environment: java.io.File getExternalStorageDirectory()>") && checkSpecificAPI("<android.media.RingtoneManager: void setActualDefaultRingtoneUri(android.content.Context,int,android.net.Uri)>", sensitiveAPIs) || checkSpecificAPI("<java.net.URL: java.net.URLConnection openConnection()>", sensitiveAPIs) || checkSpecificAPI("<android.app.DownloadManager: long enqueue(android.app.DownloadManager$Request)>", sensitiveAPIs))
                                || (tcbAPI.startsWith("<android.content.Context: java.io.File getExternalCacheDir()>") && checkSpecificAPI("<java.net.URL: java.io.InputStream openStream()>", sensitiveAPIs))
                                || (tcbAPI.startsWith("<java.io.File: long length()>") && checkSpecificAPI("<java.net.URL: java.net.URLConnection openConnection()>", sensitiveAPIs) || checkSpecificAPI("<android.webkit.WebView: void loadUrl", sensitiveAPIs) || checkSpecificAPI("<java.net.HttpURLConnection: void connect()>", sensitiveAPIs) || checkSpecificAPI("<org.apache.http.impl.client.DefaultHttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest)>", sensitiveAPIs))
                                //user input
                                || tcbAPI.contains("<android.view.MenuItem: int getItemId()>") || tcbAPI.contains("<android.widget.Gallery: int getSelectedItemPosition()>") || tcbAPI.contains("<android.widget.Button: java.lang.CharSequence getText()>") || tcbAPI.contains("<android.widget.EditText: android.text.Editable getText()>")
                                || (tcbAPI.contains("<android.view.KeyEvent: int getKeyCode()>") && checkSpecificAPI("<android.webkit.WebView: void goBack()>", sensitiveAPIs))
                                //UI模块
                                || tcbAPI.contains("<android.view.View: int getId()>")
                                || (tcbAPI.startsWith("<android.view.") && checkSpecificAPI("<android.webkit.WebView:", sensitiveAPIs) || checkSpecificAPI("<android.net.ConnectivityManager:", sensitiveAPIs) || checkSpecificAPI("<android.media.MediaPlayer:", sensitiveAPIs) || checkSpecificAPI("<android.nfc.NfcAdapter:", sensitiveAPIs) || checkSpecificAPI("<android.widget.VideoView:", sensitiveAPIs) || checkSpecificAPI("<android.media.AsyncPlayer:", sensitiveAPIs))
                                || tcbAPI.contains("<android.app.ProgressDialog: android.view.Window getWindow()>")
                                || (tcbAPI.contains("<android.webkit.WebView: boolean canGoForward()>") && (checkSpecificAPI("<android.webkit.WebView: void goForward()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.webkit.WebView: boolean canGoBack()>") && (checkSpecificAPI("<android.webkit.WebView: void goBack()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.webkit.WebView: android.webkit.WebBackForwardList copyBackForwardList()>") && checkSpecificAPI("<android.webkit.WebView:", sensitiveAPIs))
                                || (tcbAPI.contains("<android.widget.PopupWindow: boolean isShowing()>") && checkSpecificAPI("<android.webkit.WebView: void loadUrl(java.lang.String)>", sensitiveAPIs))
                                || (checkSpecificAPI("removeJavascriptInterface", sensitiveAPIs))
                                || (tcbAPI.contains("<android.webkit.WebViewProvider: android.webkit.WebSettings getSettings()>") && checkSpecificAPI("<android.webkit.WebView: void loadUrl(java.lang.String)>", sensitiveAPIs))

//                        //WakeLock
                                || (tcbAPI.contains("android.app.Service: boolean stopSelfResult") && checkSpecificAPI("android.os.PowerManager$WakeLock: void release()", sensitiveAPIs))
                                || (checkSpecificAPI("android.os.PowerManager$WakeLock: void acquire", sensitiveAPIs) && checkSpecificAPI("android.os.PowerManager$WakeLock: void release", sensitiveAPIs))
                                || (tcbAPI.contains("<android.os.PowerManager$WakeLock: boolean isHeld()>"))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager$MulticastLock: boolean isHeld()>") && (checkSpecificAPI("<android.net.wifi.WifiManager$MulticastLock: void release()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager$MulticastLock: boolean isHeld()>") && (checkSpecificAPI("<android.net.wifi.WifiManager$MulticastLock: void acquire()>", sensitiveAPIs)))

//                        //other
                                || (tcbAPI.contains("vibrate") && checkSpecificAPI("android.os.Vibrator: void vibrate", sensitiveAPIs))
                                || (item.contains("<android.content.res.Resources: java.lang.String getString(int)>") && (checkSpecificAPI("<android.webkit.WebView: void loadUrl(java.lang.String)>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.app.Notification: android.widget.RemoteViews contentView>") && (checkSpecificAPI("<android.app.NotificationManager: void notify(", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.app.KeyguardManager: boolean inKeyguardRestrictedInputMode()>") && (checkSpecificAPI("<android.app.KeyguardManager$KeyguardLock: void disableKeyguard()>", sensitiveAPIs) || checkSpecificAPI("<android.media.MediaPlayer: void pause()>", sensitiveAPIs) || checkSpecificAPI("<android.app.KeyguardManager$KeyguardLock: void reenableKeyguard()>", sensitiveAPIs)))
                                || tcbAPI.contains("<android.provider.Settings: boolean canDrawOverlays(android.content.Context)>")
                                || (tcbAPI.contains("<android.util.DisplayMetrics: int densityDpi>"))
                                || (tcbAPI.contains("<android.bluetooth.IBluetooth: boolean isEnabled()>") && (checkSpecificAPI("<android.bluetooth.BluetoothAdapter: boolean enable()>", sensitiveAPIs) || checkSpecificAPI("<android.bluetooth.BluetoothAdapter: boolean disable()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.os.PowerManager: boolean isScreenOn()>") && (checkSpecificAPI("<android.os.PowerManager$WakeLock: void acquire(long)>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.os.Looper: android.os.Looper myLooper()>") && (checkSpecificAPI("<android.os.Looper: void loop()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<java.io.BufferedReader: java.lang.String readLine()>") && (checkSpecificAPI("<android.net.wifi.WifiManager: android.net.wifi.WifiInfo getConnectionInfo()>", sensitiveAPIs)))

                                //media
                                || (tcbAPI.contains("<android.media.MediaPlayer: boolean isPlaying()>"))

                                //telephony
                                || (tcbAPI.contains("<android.telephony.TelephonyManager: int getSimState()>") && (checkSpecificAPI("<android.telephony.TelephonyManager:", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.telephony.TelephonyManager: java.lang.String getSimOperator()>") && (checkSpecificAPI("<android.telephony.TelephonyManager:", sensitiveAPIs)))

                                //database
                                || (tcbAPI.contains("<android.database."))
                                //network
                                || (checkSpecificAPI("<java.net.URL: java.net.URLConnection openConnection", branchUnit.sensitiveAPIsInIfBranch) && checkSpecificAPI("<java.net.URL: java.net.URLConnection openConnection", branchUnit.sensitiveAPIsInelseBranch) )
                                || (tcbAPI.contains("android.net.NetworkInfo: boolean isConnected()") && (checkSpecificAPI("connect()", sensitiveAPIs) || checkSpecificAPI("openConnection()", sensitiveAPIs) || checkSpecificAPI("android.net.NetworkInfo getActiveNetworkInfo()", sensitiveAPIs) || checkSpecificAPI("<android.net.wifi.WifiManager: android.net.wifi.WifiInfo getConnectionInfo()", sensitiveAPIs) || checkSpecificAPI("<android.webkit.WebView: void loadUrl", sensitiveAPIs)))
                                || (tcbAPI.contains("android.net.NetworkInfo: boolean isConnected()") && (checkSpecificAPI("<android.widget.VideoView: void resume()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.NetworkInfo: boolean isAvailable()>") && (checkSpecificAPI("connect()", sensitiveAPIs) || checkSpecificAPI("openConnection()", sensitiveAPIs) || checkSpecificAPI("android.net.NetworkInfo getActiveNetworkInfo()", sensitiveAPIs) || checkSpecificAPI("<android.net.wifi.WifiManager: android.net.wifi.WifiInfo getConnectionInfo()", sensitiveAPIs) || checkSpecificAPI("<android.webkit.WebView: void loadUrl", sensitiveAPIs)))
                                || (tcbAPI.contains("android.webkit.URLUtil: boolean isHttpUrl") && (checkSpecificAPI("<android.webkit.WebView: void loadUrl", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.ConnectivityManager: android.net.NetworkInfo getActiveNetworkInfo()>") && (checkSpecificAPI("<android.net.wifi.WifiManager: boolean isWifiEnabled()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<java.net.URL: java.net.URLConnection openConnection()>") && (checkSpecificAPI("<java.net.URLConnection: java.io.InputStream getInputStream()>", sensitiveAPIs) || checkSpecificAPI("<android.webkit.WebView: void loadUrl", sensitiveAPIs) || checkSpecificAPI("<java.net.HttpURLConnection: void connect()", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager: boolean isWifiEnabled()>") && (checkSpecificAPI("<android.net.wifi.WifiManager:", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager$WifiLock: boolean isHeld()>") && (checkSpecificAPI("<android.net.wifi.WifiManager$WifiLock:", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager$MulticastLock: boolean isHeld()>") && (checkSpecificAPI("<android.net.wifi.WifiManager$WifiLock:", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.webkit.WebView: java.lang.String getUrl()>") && checkSpecificAPI("<android.webkit.WebView:", sensitiveAPIs))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager: int getWifiState()>") && checkSpecificAPI("<android.net.wifi.WifiManager:", sensitiveAPIs))
                                || (tcbAPI.contains("<android.net.NetworkInfo: boolean isConnected()>") && checkSpecificAPI("<android.os.Looper: void loop()>", sensitiveAPIs))
                                || (tcbAPI.contains("<android.content.pm.ApplicationInfo: int uid>") && checkSpecificAPI("<android.net.wifi.WifiManager: android.net.wifi.WifiInfo getConnectionInfo()>", sensitiveAPIs))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager: android.net.wifi.WifiInfo getConnectionInfo()>") && (checkSpecificAPI("<android.net.wifi.WifiManager: int getWifiState()>", sensitiveAPIs) || checkSpecificAPI("<android.net.wifi.WifiManager: int getWifiState()>", sensitiveAPIs) || checkSpecificAPI("<android.net.wifi.WifiManager: boolean isWifiEnabled()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager: java.util.List getConfiguredNetworks()>") && (checkSpecificAPI("<android.net.wifi.WifiManager: boolean saveConfiguration()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager: boolean isScanAlwaysAvailable()>()") && (checkSpecificAPI("<android.net.wifi.WifiManager: boolean startScan()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.NetworkInfo$State: android.net.NetworkInfo$State CONNECTED>") && (checkSpecificAPI("<android.net.ConnectivityManager: android.net.NetworkInfo getNetworkInfo", sensitiveAPIs) || checkSpecificAPI("<android.net.wifi.WifiManager: android.net.wifi.WifiInfo getConnectionInfo()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.wifi.WifiManager: boolean isScanAlwaysAvailable()>") && (checkSpecificAPI("<android.net.wifi.WifiManager: boolean startScan()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<java.net.ServerSocket: boolean isClosed()>") && (checkSpecificAPI("<java.net.ServerSocket: void bind(java.net.SocketAddress)>", sensitiveAPIs)))
                                || (tcbAPI.contains("<java.net.URLConnection: java.io.InputStream getInputStream()>()") && (checkSpecificAPI("<android.os.Looper: void loop()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<java.net.URLConnection: java.io.InputStream getInputStream()>()") && (checkSpecificAPI("<android.os.Looper: void loop()>", sensitiveAPIs)))
                                || (tcbAPI.contains("<android.net.NetworkInfo: int getType()>") && (checkSpecificAPI("<org.apache.http.impl.client.DefaultHttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest)>", sensitiveAPIs)))
                        ;
            }
        }).collect(Collectors.toList());
        boolean hasCommonCases = CollectionUtils.isNotEmpty(commonCases);
        if (hasCommonCases) {
            branchUnit.hasCommonCaese = true;
        }
    }


    public static Set<String> varPattern(String s) {
        Pattern pattern1 = Pattern.compile("(=)(.*)", Pattern.DOTALL);
        Matcher matcher1 = pattern1.matcher(s);
        if (matcher1.find()) {
            s = matcher1.group();
        }

        Set<String> variables = new HashSet<>();
        Pattern pattern = Pattern.compile("(\\$[a-z][0-9]+)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(s);
        while (matcher.find()) {
            variables.add(matcher.group());
        }

        return variables;
    }

    public static boolean containsKeyword(String s, Set<String> set) {
        for (String item : set) {
            if (s.contains(item)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> variablePattern(String s) {
        Set<String> variables = new HashSet<>();
        Pattern pattern = Pattern.compile("(\\$[a-z][0-9]+)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(s);
        while (matcher.find()) {
            variables.add(matcher.group());
        }
        return variables;
    }

    public static String getSubUtilSimple(String soap, String rgex) {
        Pattern pattern = Pattern.compile(rgex);// 匹配的模式
        Matcher m = pattern.matcher(soap);
        while (m.find()) {
            return m.group(1);
        }
        return "";
    }

    public static boolean checkSpecificAPI(String s, Set<String> uniqueSensitiveAPIs) {
        if (CollectionUtils.isEmpty(uniqueSensitiveAPIs)) {
            return false;
        }

        AtomicBoolean flag = new AtomicBoolean(false);
        uniqueSensitiveAPIs.forEach(a -> {
            if (a.toLowerCase().contains(s.toLowerCase())) {
                flag.set(true);
            }
        });

        return flag.get();
    }

    private static String convert2Pattern(String s) {
        Pattern pattern = Pattern.compile("(<.*:)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            return matcher.group(1).substring(0, matcher.group(1).length() - 1);

        }
        return s.substring(0, s.length() - 1);
    }

    private static String convert2PatternMethod(String s) {
        Pattern pattern = Pattern.compile("(<.*>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            return matcher.group(1);

        }
        return s;
    }

    private static boolean contain2PatternMethod(String s) {
        Pattern pattern = Pattern.compile("(<.*>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            return true;

        }
        return false;
    }

    private static boolean triggerConditionBackTrack(DirectedGraph<Unit> ug, Unit u, IfStmt uStmt, BranchUnit branchUnit) {
        if (branchUnit.isBranchDifferent) {
            try {
                ifConditionPropogationAnalysis(ug, u, uStmt, branchUnit);
            } catch (StackOverflowError e) {
                System.out.println("stackoverflow:" + u.toString() +"; target:"+branchUnit.declareMethod.toString());
                return true;
            } catch (NullPointerException e) {
                System.out.println("NPE:" + u.toString()+"; target:"+branchUnit.declareMethod.toString());
                return true;
            } catch (Exception e) {
                System.out.println("Exception:" + u.toString()+"; target:"+branchUnit.declareMethod.toString());
                return true;
            }
        }
        return false;
    }

    private static void ifConditionPropogationAnalysis(DirectedGraph<Unit> ug, Unit u, IfStmt uStmt, BranchUnit branchUnit) {
        for (ValueBox valueBox : uStmt.getCondition().getUseBoxes()) {
            if (valueBox.getValue() instanceof Local) {
                List<Object> result = ArgumentValueManager
                        .v()
                        .getArgumentValueAnalysis(Constants.DefaultArgumentTypes.Scalar.CLASS)
                        .computeVariableValues(valueBox.getValue(), (Stmt) ug.getPredsOf(u).get(0));
                for (Object o : result) {
                    specifyResult(branchUnit, o);
                }
            }
//            else if (valueBox.getValue() instanceof IntConstant) {
//                List<Object> result = ArgumentValueManager
//                        .v()
//                        .getArgumentValueAnalysis(Constants.DefaultArgumentTypes.Scalar.INT)
//                        .computeVariableValues(valueBox.getValue(), (Stmt) ug.getPredsOf(u).get(0));
//                for (Object o : result) {
//                    specifyResult(branchUnit, o);
//                }
//            } else if (valueBox.getValue() instanceof StringConstant) {
//                List<Object> result = ArgumentValueManager
//                        .v()
//                        .getArgumentValueAnalysis(Constants.DefaultArgumentTypes.Scalar.STRING)
//                        .computeVariableValues(valueBox.getValue(), (Stmt) ug.getPredsOf(u).get(0));
//                for (Object o : result) {
//                    specifyResult(branchUnit, o);
//                }
//            }
        }
    }

}
