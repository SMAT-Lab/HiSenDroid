package edu.monash.retarget;


import edu.psu.cse.siis.coal.AnalysisParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.cfg.LibraryClassPatcher;
import soot.options.Options;

import java.io.File;
import java.util.Collections;

import static soot.SootClass.HIERARCHY;

public class SootSetup {

    private static final Logger logger = LoggerFactory.getLogger(SootSetup.class);

    /**
     * Initializes soot for running the soot-based phases of the application
     * metadata analysis
     */
    public static void initializeSoot(InfoflowAndroidConfiguration config, String forceAndroidJar) {
        logger.info("Initializing Soot...");

        final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
        final String apkFileLocation = config.getAnalysisFileConfig().getTargetAPKFile();

        // Clean up any old Soot instance we may have
        G.reset();

        Scene.v().addBasicClass("java.io.FileSystem",HIERARCHY);
        Scene.v().addBasicClass("com.example.androidthings.bluetooth.audio.A2dpSinkActivity", SootClass.BODIES);
        config.setWriteOutputFiles(true);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_soot_classpath(forceAndroidJar);
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_allow_phantom_elms(true);
        if (config.getWriteOutputFiles())
            Options.v().set_output_format(Options.output_format_class);
        else
            Options.v().set_output_format(Options.output_format_none);
        Options.v().set_whole_program(true);
        Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
        Options.v().set_force_android_jar(forceAndroidJar);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_keep_line_number(true);
        Options.v().set_keep_offset(false);
        Options.v().set_throw_analysis(Options.throw_analysis_dalvik);
        Options.v().set_process_multiple_dex(config.getMergeDexFiles());
        Options.v().set_ignore_resolution_errors(true);

        Options.v().set_soot_classpath(getClasspath(config));
        Main.v().autoSetOptions();
        configureCallgraph(config);

        // Load whatever we need
        logger.info("Loading dex files...");
        Scene.v().loadNecessaryClasses();

        // Make sure that we have valid Jimple bodies
        PackManager.v().getPack("wjpp").apply();

        // Patch the callgraph to support additional edges. We do this now,
        // because during callback discovery, the context-insensitive callgraph
        // algorithm would flood us with invalid edges.
        LibraryClassPatcher patcher = new LibraryClassPatcher();
        patcher.patchLibraries();
    }

    /**
     * Builds the classpath for this analysis
     *
     * @return The classpath to be used for the taint analysis
     */
    public static String getClasspath(InfoflowAndroidConfiguration config) {
        final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
        final String additionalClasspath = config.getAnalysisFileConfig().getAdditionalClasspath();

        String classpath = androidJar;
        if (additionalClasspath != null && !additionalClasspath.isEmpty())
            classpath += File.pathSeparator + additionalClasspath;
        logger.debug("soot classpath: " + classpath);
        return classpath;
    }

    /**
     * Configures the callgraph options for Soot according to FlowDroid's settings
     */
    public static void configureCallgraph(InfoflowAndroidConfiguration config) {
        // Configure the callgraph algorithm
        switch (config.getCallgraphAlgorithm()) {
            case AutomaticSelection:
            case SPARK:
                Options.v().setPhaseOption("cg.spark", "on");
                break;
            case GEOM:
                Options.v().setPhaseOption("cg.spark", "on");
                AbstractInfoflow.setGeomPtaSpecificOptions();
                break;
            case CHA:
                Options.v().setPhaseOption("cg.cha", "on");
                break;
            case RTA:
                Options.v().setPhaseOption("cg.spark", "on");
                Options.v().setPhaseOption("cg.spark", "rta:true");
                Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
                break;
            case VTA:
                Options.v().setPhaseOption("cg.spark", "on");
                Options.v().setPhaseOption("cg.spark", "vta:true");
                break;
            default:
                throw new RuntimeException("Invalid callgraph algorithm");
        }
        if (config.getEnableReflection())
            Options.v().setPhaseOption("cg", "types-for-invoke:true");
    }
}
