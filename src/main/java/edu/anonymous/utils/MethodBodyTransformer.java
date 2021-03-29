package edu.anonymous.utils;

import edu.anonymous.GlobalRef;
import edu.anonymous.graph.Visualizer;
import edu.anonymous.model.BranchUnit;
import soot.*;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.internal.ImmediateBox;
import soot.toolkits.graph.*;
import soot.util.Chain;

import java.util.*;

public class MethodBodyTransformer extends SceneTransformer {
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        Chain<SootClass> sootClasses = Scene.v().getApplicationClasses();

        for (Iterator<SootClass> iter = sootClasses.snapshotIterator(); iter.hasNext(); ) {
            SootClass sc = iter.next();

            List<SootMethod> sootMethods = sc.getMethods();

            for (int i = 0; i < sootMethods.size(); i++) {
                SootMethod sm = sootMethods.get(i);

                // If this method is part of the Android framework, we don't need to transform it
                if (sm.isConcrete()
                        && !ApplicationClassFilter.isClassInSystemPackage(sm.getDeclaringClass().getName())
                        && !sm.getSignature().contains("dummyMainClass")) {
                    triggerHSO(sm);
                }
            }
        }
    }

    private static void triggerHSO(SootMethod targetMethod) {
        if(!targetMethod.hasActiveBody()){
            return;
        }
        Body body = targetMethod.getActiveBody();
        List<Unit> units = new ArrayList<>();
        for(Unit u : body.getUnits()){
            units.add(u);
        }

        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (u.branches()) {
                //If-ELSE
                // Stmt
                if (u instanceof IfStmt) {
                    IfStmt uStmt = (IfStmt) u;

                    for(BranchUnit branchUnit : GlobalRef.branchUnits) {
                        if(!branchUnit.containHSO){
                            continue;
                        }
                        if (branchUnit.declareMethod.getSignature().equals(targetMethod.getSignature())
                                && branchUnit.branchInvokeUnit.toString().equals(u.toString())) {
                            instrumentAPK(uStmt, branchUnit);
                        }
                    }
                }
            }
        }
    }

    private static void instrumentAPK(IfStmt u, BranchUnit branchUnit) {
        if(branchUnit.ifBranchHasSensitiveApi){
            ImmediateBox a = new ImmediateBox(IntConstant.v(0));
            ImmediateBox b = new ImmediateBox(IntConstant.v(1));
            soot.jimple.ConditionExpr newExpr = soot.jimple.Jimple.v().newNeExpr(a.getValue(), b.getValue());

            u.setCondition(newExpr);
        }else{
            ImmediateBox a = new ImmediateBox(IntConstant.v(1));
            ImmediateBox b = new ImmediateBox(IntConstant.v(1));
            soot.jimple.ConditionExpr newExpr = soot.jimple.Jimple.v().newNeExpr(a.getValue(), b.getValue());

            u.setCondition(newExpr);
        }
    }

    public void transformStmtInfo(SootMethod sm) {
        try {
            Body body = sm.retrieveActiveBody();

            UnitGraph ug = new ClassicCompleteUnitGraph(body);
            Visualizer visualizer = new Visualizer(sm.getSignature());
            visualizer.addUnitGraph(ug);
            if (visualizer.branchFlag) {
                body.getTraps().clear();
                visualizer.transform2DFSBody(ug, body);
            }

            body.getUnits().forEach(unit -> {
                if (null == unit || unit.getDefBoxes() == null) {
                    System.out.println("unit:" + unit.toString() + sm.getSignature());
                }
            });
            //visualizer.draw();
        } catch (NullPointerException npe) {
            System.out.println("No body for method " + sm.getSignature());
        } catch (Exception ex) {
            System.out.println("No body for method " + sm.getSignature());
        }
    }

}
