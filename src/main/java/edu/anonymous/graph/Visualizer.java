package edu.anonymous.graph;

import edu.anonymous.help.StmtTypeHelper;
import edu.anonymous.utils.MagDigest;
import org.graphstream.graph.*;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.LinLog;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.view.Viewer;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.HashMap;
import java.util.Iterator;

public class Visualizer {
    public static Visualizer instance;
//    public static Visualizer v() {
//        if(instance == null)
//            instance = new Visualizer();
//        return instance;
//    }

    public Graph graph;
    public SpriteManager sman;
    public Viewer viewer;
    public HashMap<String, Unit> nodeIdUnitMap;
    public boolean branchFlag;

    public Visualizer(String graphId){
        init(graphId);
    }

    public void init(String graphId){
        if (graph != null)
            graph.clear();
        if(viewer != null)
            viewer.close();

        branchFlag = false;
        nodeIdUnitMap = new HashMap();
        graph = new SingleGraph(graphId);
        sman = new SpriteManager(graph);
        graph.addAttribute("ui.quality");
        graph.addAttribute("ui.antialias");
        String GCSS = "";
        GCSS += "node {\n" +
                "\ttext-background-mode: rounded-box;\n" +
//                "text-background-color: white;\n" +
                "\tsize: 30px, 30px;\n" +
                "\ttext-size: 20;\n" +
                "\tshape: diamond;\n" +
                "}\n";
        GCSS += "node.branch {\n" +
                "\tfill-color: blue;\n" +
                "}\n";
        GCSS += "node.terminal {\n" +
                "\tfill-color: rgb(00,74,65);\n" +
                "}\n";
        GCSS += "edge.icfg {\n" +
                "\tfill-color: red;\n" +
                "}\n";

        graph.setAttribute("ui.stylesheet", GCSS);
    }

    public void addUnitGraph(UnitGraph unitGraph){
        try {
            int index = 0;
            for(Iterator<Unit> it = unitGraph.iterator(); it.hasNext();){
                index += 1;
                Unit unit = it.next();
                String aid = getID(unit);
                Node aNode = graph.addNode(getID(unit));
                nodeIdUnitMap.put(aid, unit);
                String label = unit.toString();
                if (unit instanceof JAssignStmt){
                    JAssignStmt jAssignStmt = (JAssignStmt) unit;
                    if(jAssignStmt.containsInvokeExpr() && jAssignStmt.getInvokeExpr() instanceof JVirtualInvokeExpr){
                        label = jAssignStmt.getLeftOp().toString() + "=";
                        JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr) jAssignStmt.getInvokeExpr();
                        label += jVirtualInvokeExpr.getBase() + "."+jVirtualInvokeExpr.getMethod().getName();
                        if(jVirtualInvokeExpr.getArgCount() > 0){
                            label += "(";
                            for(Value v : jVirtualInvokeExpr.getArgs()){
                                label +=v.toString() + ",";
                            }
                            label = label.substring(0, label.length()-1);
                            label += ")";
                        }
                    }
                }
                if (unit instanceof JInvokeStmt){
                    JInvokeStmt jInvokeStmt = (JInvokeStmt) unit;
                    if(jInvokeStmt.containsInvokeExpr() && jInvokeStmt.getInvokeExpr() instanceof JVirtualInvokeExpr){
                        JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr) jInvokeStmt.getInvokeExpr();
                        label = jVirtualInvokeExpr.getBase() + "."+jVirtualInvokeExpr.getMethod().getName();
                        if(jVirtualInvokeExpr.getArgCount() > 0){
                            label += "(";
                            for(Value v : jVirtualInvokeExpr.getArgs()){
                                label +=v.toString() + ",";
                            }
                            label = label.substring(0, label.length()-1);
                            label += ")";
                        }
                    }
                }
                label = label.replace("java.lang.", "").replace("java.io.","");
                // TODO: simplify it to line number
                //label = String.valueOf(index);
                aNode.setAttribute("ui.label", label);
                if(unit instanceof JIfStmt){
                    branchFlag = true;
                    aNode.addAttribute("ui.class", "branch");
                }
            }
            for(Unit unit : unitGraph.getHeads()){
                Node aNode = graph.getNode(getID(unit));
                aNode.addAttribute("ui.class", "terminal");
            }
            for(Unit unit : unitGraph.getTails()){
                Node aNode = graph.getNode(getID(unit));
                aNode.addAttribute("ui.class", "terminal");
            }
            for(Iterator<Unit> it = unitGraph.iterator(); it.hasNext();){
                Unit unit = it.next();
                for(Unit suc : unitGraph.getSuccsOf(unit)) {
                    String aid = getID(unit);
                    String bid = getID(suc);
                    graph.addEdge(aid+bid, aid, bid, true);
                }
            }
//        Unit header = unitGraph.getHeads().get(0);
//        String hid = getID(header);
//        Node head = graph.getNode(hid);
//        int x = 50, y = 2000;
//        int c = 1;
//        Random random = new Random();
//        for(Iterator<Node> it = head.getBreadthFirstIterator(true); it.hasNext();){
//            Node node = it.next();
//            node.setAttribute("xyz", x, y, 0);
//            x += 50 + random.nextInt(50)-25;
//            y -= c * 50 + random.nextInt(50);
//            c *= -1;
//        }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void transform2DFSBody(UnitGraph unitGraph, Body body){
        body.getUnits().clear();

        Unit header = unitGraph.getHeads().get(0);
        String hid = getID(header);
        Node head = graph.getNode(hid);
        Unit returnUnit = null;
        for (Iterator<Node> it = head.getDepthFirstIterator(true); it.hasNext(); ) {
            Node node = it.next();
            Unit unit = nodeIdUnitMap.get(node.getId());
            if(null == unit){
                continue;
            }
            if (StmtTypeHelper.isReturnStmt(unit)) {
                returnUnit = unit;
            }
            if (!(StmtTypeHelper.isReturnStmt(unit)) && !(unit instanceof IfStmt) && !(unit instanceof GotoStmt && ((GotoStmt) unit).getTarget() == null)) {
                body.getUnits().add(unit);
            }
        }
        if (null != returnUnit) {
            body.getUnits().add(returnUnit);
        }
    }

    public void addCallGraph(CallGraph callGraph){
        for(Iterator<soot.jimple.toolkits.callgraph.Edge> it = callGraph.iterator(); it.hasNext(); ){
            try {
                soot.jimple.toolkits.callgraph.Edge sEdge = it.next();

                String aid = getID(sEdge.srcUnit());
                if (hasNode(aid)) {
                    SootMethod sm = sEdge.getTgt().method();
                    UnitGraph unitGraph = new CompleteUnitGraph(sm.getActiveBody());
                    for (Unit unit : unitGraph.getHeads()) {
                        String bid = getID(unit);
                        if (hasNode(bid)) {
                            Edge e = graph.addEdge(aid + bid, aid, bid, true);
                            e.addAttribute("ui.class", "icfg");
                        }
                    }
                    for (Unit unit : unitGraph.getTails()) {
                        String bid = getID(unit);
                        Unit suc = sEdge.getSrc().method().getActiveBody().getUnits().getSuccOf(sEdge.srcUnit());
                        String cid = getID(suc);
                        if (hasNode(bid) && hasNode(cid)) {
                            Edge e = graph.addEdge(bid + cid, bid, cid, true);
                            e.addAttribute("ui.class", "icfg");
                        }
                    }
                }
            }
            catch (Exception exc){

            }
        }

//        for(Iterator<Unit> it = unitGraph.iterator(); it.hasNext();){
//            Unit unit = it.next();
//            String aid = getID(unit);
//            Node aNode = graph.addNode(getID(unit));
//            aNode.setAttribute("ui.label", unit.toString());
//            if(unit instanceof JIfStmt)
//                aNode.addAttribute("ui.class", "branch");
//        }
//        for(Unit unit : unitGraph.getHeads()){
//            Node aNode = graph.getNode(getID(unit));
//            aNode.addAttribute("ui.class", "terminal");
//        }
//        for(Unit unit : unitGraph.getTails()){
//            Node aNode = graph.getNode(getID(unit));
//            aNode.addAttribute("ui.class", "terminal");
//        }
//        for(Iterator<Unit> it = unitGraph.iterator(); it.hasNext();){
//            Unit unit = it.next();
//            for(Unit suc : unitGraph.getSuccsOf(unit)) {
//                String aid = getID(unit);
//                String bid = getID(suc);
//                graph.addEdge(aid+bid, aid, bid, true);
//            }
//        }
    }

    private boolean hasNode(String id){
        try {
            graph.getNode(id);
            return true;
        }catch (Exception e){

        }
        return false;
    }

    public void draw(){
        if(viewer != null)
            viewer.close();
        viewer = graph.display();
//        View view = viewer.getDefaultView();
        Layout layout = new LinLog();
        viewer.enableAutoLayout(layout);
//        layout.setStabilizationLimit(10);
    }

    public void close(){
        if (viewer != null)
            viewer.close();
        if (graph != null)
            graph.clear();
        graph = null;
        sman = null;
    }

    private String getID(Unit a){
        String id = Integer.toString(System.identityHashCode(a));
//        String newId = "";
//        for(int i=0; i< id.length(); i++){
//            newId += (char)(97+id.codePointAt(i)-"0".codePointAt(0));
//        }
//        return newId;
        return MagDigest.sha256(id);
    }

    private String getSHA256ID(Unit unit){
        return MagDigest.sha256(unit.toString());
    }


}
