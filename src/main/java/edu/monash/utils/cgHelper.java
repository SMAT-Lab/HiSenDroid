package edu.monash.utils;

import edu.monash.model.UnitInfo;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

public class cgHelper {

    public static List<UnitInfo> collectInvokeAPIs(Unit u, SootMethod declareMethod) {
        List<UnitInfo> callees = new ArrayList<>();
        callees.add(new UnitInfo(u, declareMethod.getSignature()));

        for (Iterator<Edge> edgeIt = Scene.v().getCallGraph().edgesOutOf(u); edgeIt.hasNext(); ) {
            Edge e = edgeIt.next();
            SootMethod callee = e.getTgt().method();
            if (callee.hasActiveBody()) {
                callee.getActiveBody().getUnits().stream().forEach(eu -> {
                    Stmt uStmt = (Stmt) eu;
                    if (uStmt.containsInvokeExpr()) {
                        callees.add(new UnitInfo(eu, callee.getSignature()));
                    }
                });
            }
        }

        return callees;
    }

    public static Set<Object> collectInvokeAPIs(Unit u) {
        Set<Object> callees = new HashSet<>();
        callees.add(u.toString());

        for (Iterator<Edge> edgeIt = Scene.v().getCallGraph().edgesOutOf(u); edgeIt.hasNext(); ) {
            Edge e = edgeIt.next();
            SootMethod callee = e.getTgt().method();
            if (callee.hasActiveBody()) {
                callee.getActiveBody().getUnits().stream().forEach(eu -> {
                    Stmt uStmt = (Stmt) eu;
                    if (uStmt.containsInvokeExpr()) {
                        callees.add(uStmt.toString());
                    }
                });
            }
        }

        return callees;
    }
}
