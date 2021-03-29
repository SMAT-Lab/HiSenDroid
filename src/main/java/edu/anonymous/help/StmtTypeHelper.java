package edu.anonymous.help;

import soot.Unit;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;

public class StmtTypeHelper {

    public static boolean isReturnStmt(Unit unit){
        return unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt;
    }
}
