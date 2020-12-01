package edu.monash;

import soot.*;
import soot.jimple.JimpleBody;
import soot.options.Options;
import soot.toolkits.graph.DirectedGraph;
import soot.util.cfgcmd.AltClassLoader;
import soot.util.cfgcmd.CFGGraphType;
import soot.util.cfgcmd.CFGIntermediateRep;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;

import java.util.Map;

public class CFGGenerator extends BodyTransformer {
    private static final String altClassPathOptionName = "alt-class-path";
    private static final String graphTypeOptionName = "graph-type";
    private static final String defaultGraph = "BriefUnitGraph";
    private static final String irOptionName = "ir";
    private static final String defaultIR = "jimple";
    private static final String multipageOptionName = "multipages";
    private static final String briefLabelOptionName = "brief";

    private CFGGraphType graphtype;
    private CFGIntermediateRep ir;
    private CFGToDotGraph drawer;

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        initialize(options);
        System.out.println(options);
        System.out.println(b);
        Body body = ir.getBody((JimpleBody) b);
        System.out.println(body);
        print_cfg(body);

    }

    public static void main(String[] args) {
        CFGGenerator viewer = new CFGGenerator();
        Transform printTransform = new Transform("jtp.printcfg", viewer);
        printTransform.setDeclaredOptions("enabled " + altClassPathOptionName + ' ' + graphTypeOptionName + ' '
                + irOptionName + ' ' + multipageOptionName + ' ' + briefLabelOptionName + ' ');
        printTransform.setDefaultOptions("enabled " + altClassPathOptionName + ": " + graphTypeOptionName + ':'
                + defaultGraph + ' ' + irOptionName + ':' + defaultIR + ' ' + multipageOptionName + ":false " + ' '
                + briefLabelOptionName + ":false ");
        PackManager.v().getPack("jtp").add(printTransform);
        //args = viewer.parse_options(args);
//        String[] soot_args = new String[3];
//        //soot_args[0] = "-cp";
//        soot_args[0] = "--soot-classpath";
//        soot_args[1] = "/Users/xsun0035/workspace/monash/android-platforms/android-17/android.jar";
//        soot_args[2] = "/Users/xsun0035/workspace/monash/HSO/sootOutput/";

        String[] args2 =
                {
                        "-force-android-jar", "/Users/xsun0035/workspace/monash/android-platforms/android-17/android.jar",
                        "-process-dir", "/Users/xsun0035/Desktop/HSO/EmulatorExample.apk",
                        "-ire",
                        "-pp",
                        "-keep-line-number",
                        "-allow-phantom-refs",
                        "-w",
                        "-p", "cg", "enabled:true",
                        "-p", "wjtp.rdc", "enabled:true",
                        "-src-prec", "apk",
                        "-process-multiple-dex" , "true",
                };

        Options.v().setPhaseOption("cg.spark", "on");
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_prepend_classpath(true);
        Options.v().set_soot_classpath("/Users/xsun0035/workspace/monash/android-platforms/android-17/android.jar");
        Options.v().set_whole_program(true);

        soot.Main.main(args2);

    }

    private void initialize(Map<String, String> options) {
        if (drawer == null) {
            drawer = new CFGToDotGraph();
            drawer.setBriefLabels(PhaseOptions.getBoolean(options, briefLabelOptionName));
            drawer.setOnePage(!PhaseOptions.getBoolean(options, multipageOptionName));
            drawer.setUnexceptionalControlFlowAttr("color", "black");
            drawer.setExceptionalControlFlowAttr("color", "red");
            drawer.setExceptionEdgeAttr("color", "lightgray");
            drawer.setShowExceptions(Options.v().show_exception_dests());
            ir = CFGIntermediateRep.getIR(PhaseOptions.getString(options, irOptionName));
            graphtype = CFGGraphType.getGraphType(PhaseOptions.getString(options, graphTypeOptionName));

            AltClassLoader.v().setAltClassPath(PhaseOptions.getString(options, altClassPathOptionName));
            AltClassLoader.v().setAltClasses(
                    new String[] { "soot.toolkits.graph.ArrayRefBlockGraph", "soot.toolkits.graph.Block",
                            "soot.toolkits.graph.Block$AllMapTo", "soot.toolkits.graph.BlockGraph",
                            "soot.toolkits.graph.BriefBlockGraph", "soot.toolkits.graph.BriefUnitGraph",
                            "soot.toolkits.graph.CompleteBlockGraph", "soot.toolkits.graph.CompleteUnitGraph",
                            "soot.toolkits.graph.TrapUnitGraph", "soot.toolkits.graph.UnitGraph",
                            "soot.toolkits.graph.ZonedBlockGraph", });
        }
    }

    protected void print_cfg(Body body) {
        DirectedGraph<Unit> graph = graphtype.buildGraph(body);
        System.out.println(graph);
        DotGraph canvas = graphtype.drawGraph(drawer, graph, body);
        //GenerateFlow gen = new GenerateFlow((UnitGraph) graph);

        String methodname = body.getMethod().getSubSignature();
        String classname = body.getMethod().getDeclaringClass().getName().replaceAll("\\$", "\\.");
        String filename = soot.SourceLocator.v().getOutputDir();
        if (filename.length() > 0) {
            filename = filename + java.io.File.separator;
        }
        filename = filename + classname + " " + methodname.replace(java.io.File.separatorChar, '.') + DotGraph.DOT_EXTENSION;

        G.v().out.println("Generate dot file in " + filename);
        canvas.plot(filename);
    }

}
