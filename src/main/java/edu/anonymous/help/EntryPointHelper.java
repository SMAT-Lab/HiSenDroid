package edu.anonymous.help;

import edu.anonymous.GlobalRef;
import edu.anonymous.retarget.DummyMainGenerator;
import edu.anonymous.retarget.SootSetup;
import edu.anonymous.utils.ApplicationClassFilter;
import edu.anonymous.utils.ExtractApkInfoUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.resources.LayoutFileParser;
import soot.util.Chain;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EntryPointHelper {

    /**
     * calculate Entry Point in the given APK file.
     */
    public static void calculateEntryPoint(String apkPath, String forceAndroidJar) throws IOException, XmlPullParserException {
        DummyMainGenerator dummyMainGenerator = new DummyMainGenerator(apkPath);
        InfoflowAndroidConfiguration config = dummyMainGenerator.config;
        // sunxiaobiu: 14/10/19 you can modify your own config by changing "config" parameters
        config.getAnalysisFileConfig().setTargetAPKFile(apkPath);
        config.getAnalysisFileConfig().setAndroidPlatformDir(forceAndroidJar);
        //config.getCallbackConfig().setCallbackAnalyzer(InfoflowAndroidConfiguration.CallbackAnalyzer.Fast);
        //config.getCallbackConfig().setEnableCallbacks(false);
        config.setWriteOutputFiles(true);

        SootSetup.initializeSoot(config, forceAndroidJar);

        if (! new File(GlobalRef.SOOTOUTPUT).exists())
        {
            File sootOutput = new File(GlobalRef.SOOTOUTPUT);
            sootOutput.mkdirs();
        }

        try
        {
            FileUtils.cleanDirectory(new File(GlobalRef.SOOTOUTPUT));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        //collect all Dynamic loaded Fragments
        //collectDynamicFragments();

        ExtractApkInfoUtil.extractApkInfo(apkPath);
        GlobalRef.clsPath = forceAndroidJar;

        if (config.getCallbackConfig().getEnableCallbacks()) {
            dummyMainGenerator.parseAppResources();
            LayoutFileParser lfp = dummyMainGenerator.createLayoutFileParser();
            switch (config.getCallbackConfig().getCallbackAnalyzer()) {
                case Fast:
                    dummyMainGenerator.calculateCallbackMethodsFast(lfp, null);
                    break;
                case Default:
                    dummyMainGenerator.calculateCallbackMethods(lfp, null);
                    break;
                default:
                    throw new RuntimeException("Unknown callback analyzer");
            }

        } else {
            // Create the new iteration of the main method
            dummyMainGenerator.createMainMethod(null);
            dummyMainGenerator.constructCallgraphInternal();
        }

//        System.out.println("----------callbackMethods----------");
//        dummyMainGenerator.callbackMethods.forEach(callbackMethod ->{
//            System.out.println(callbackMethod.getO2().getTargetMethod());
//        });

        SootClass originSootClass = Scene.v().getSootClass("dummyMainClass");
        if(CollectionUtils.isNotEmpty(originSootClass.getMethods())){
            for(int i = 0; i < originSootClass.getMethods().size(); i++){
                if(originSootClass.getMethods().get(i).getName().equals("dummyMainMethod")){
                    originSootClass.getMethods().get(i).setName("main");
                    GlobalRef.dummyMainMethod = originSootClass.getMethods().get(i);
                    GlobalRef.dummyMainClass = originSootClass;
                }
            }
        }

        PackManager.v().writeOutput();
    }

    public static void collectDynamicFragments(){
        Set<SootClass> dynamicFragment = new HashSet<>();
        Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
        for (Iterator<SootClass> iter = applicationClasses.snapshotIterator(); iter.hasNext(); ) {
            SootClass sootClass = iter.next();

            if(!sootClass.isInterface()){
                List<SootClass> extendedClasses = Scene.v().getActiveHierarchy().getSuperclassesOf(sootClass);
                for (SootClass sc : extendedClasses) {
                    if(sc.getName().contains("Fragment")){
                        dynamicFragment.add(sootClass);
                    }
                }
            }

            // We copy the list of methods to emulate a snapshot iterator which
            // doesn't exist for methods in Soot
            List<SootMethod> methodCopyList = new ArrayList<>(sootClass.getMethods());
            for (SootMethod sootMethod : methodCopyList) {
                if (sootMethod.isConcrete()) {
                    final Body body = sootMethod.retrieveActiveBody();
                    final LocalGenerator lg = new LocalGenerator(body);

                    for (Iterator<Unit> unitIter = body.getUnits().snapshotIterator(); unitIter.hasNext(); ) {
                        Stmt stmt = (Stmt) unitIter.next();

                        if (stmt.containsInvokeExpr()) {
                            SootMethod callee = stmt.getInvokeExpr().getMethod();

                            // For Messenger.send(), we directly call the respective handler
                            if (callee ==  Scene.v().grabMethod("<android.support.v4.app.FragmentTransaction: android.support.v4.app.FragmentTransaction add(int,android.support.v4.app.Fragment)>")) {

                                SootClass fragmentClass = Scene.v().getSootClass(stmt.getInvokeExpr().getArgBox(1).getValue().getType().toString());
                                if(ApplicationClassFilter.isApplicationClass(fragmentClass)){
                                    //System.out.println(callee);
                                    dynamicFragment.add(Scene.v().getSootClass(stmt.getInvokeExpr().getArgBox(1).getValue().getType().toString()));
                                }
                            }
                            if (null != callee && null != callee.getSignature() && callee.getSignature().contains("Fragment")) {

                                SootClass fragmentClass = sootClass;
                                if(ApplicationClassFilter.isApplicationClass(fragmentClass)){
                                    //System.out.println(callee);
                                    dynamicFragment.add(fragmentClass);
                                }
                            }
                        }
                    }
                }
            }
        }
        GlobalRef.dynamicFragment.addAll(dynamicFragment);
    }


}
