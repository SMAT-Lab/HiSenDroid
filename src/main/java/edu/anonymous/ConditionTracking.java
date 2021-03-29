package edu.anonymous;

import soot.Body;
import soot.BodyTransformer;
import soot.Unit;
import soot.jimple.ConditionExpr;
import soot.jimple.IfStmt;

import java.util.Iterator;
import java.util.Map;

public class ConditionTracking extends BodyTransformer {
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {

        Iterator<Unit> i = body.getUnits().iterator();
        while (i.hasNext()) {
            Unit u = i.next();
            if (u instanceof IfStmt) {
                ConditionExpr expr = (ConditionExpr) ((IfStmt) u).getCondition();
                System.out.println(expr.toString());
            }
        }
    }
}
