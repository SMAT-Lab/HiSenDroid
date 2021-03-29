package edu.anonymous;

import edu.anonymous.model.BranchUnit;
import edu.anonymous.utils.ApplicationClassFilter;
import edu.psu.cse.siis.coal.lang.ParseException;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.ImmediateBox;
import soot.options.Options;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstrumentAPK {
    public static int hsoStartCountNum = 100001;
    public static int nonHsoStartCountNum = 200001;

    public static void main(String[] args) throws IOException, ParseException, XmlPullParserException {
        String apkPath = args[0];
        String forceAndroidJar = args[1];
        String outputPath = args[6];

        Main.main(args);

        String[] args2 =
                {
                        "-process-dir", apkPath,
                        "-force-android-jar", forceAndroidJar,
                        "-ire",
                        "-pp",
                        "-allow-phantom-refs",
                        "-w",
                        "-p", "cg", "enabled:true"
                };

        G.reset();

        //MethodBodyTransformer methodBodyTransformer = new MethodBodyTransformer();

        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_output_format(Options.output_format_dex);
        Options.v().set_output_dir(outputPath);
        //PackManager.v().getPack("wjtp").add(new Transform("wjtp.MethodBodyTransformer", methodBodyTransformer));

        PackManager.v().getPack("jtp").add(new Transform("jtp.BodyTransformer", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                List<Unit> units = new ArrayList<>();
                for(Unit u : b.getUnits()){
                    units.add(u);
                }
                SootMethod targetMethod = b.getMethod();

                for (int i = 0; i < units.size(); i++) {
                    Unit u = units.get(i);
                    if (u.branches()) {
                        //If-ELSE
                        // Stmt
                        if (u instanceof IfStmt && !ApplicationClassFilter.isClassSystemPackage(targetMethod.toString())) {
                            IfStmt uStmt = (IfStmt) u;

                            for(BranchUnit branchUnit : GlobalRef.branchUnits) {
                                if(!branchUnit.containHSO){
                                    continue;
                                }
                                if (branchUnit.declareMethod.getSignature().equals(targetMethod.getSignature())
                                        && branchUnit.branchInvokeUnit.toString().equals(u.toString())) {
                                    instrumentAPKHSOElla(uStmt, branchUnit, targetMethod);
                                }
                            }
                        }
                    }
                }
            }
        }));

        soot.Main.main(args2);

    }

    private static void instrumentAPKHSOElla(IfStmt u, BranchUnit branchUnit, SootMethod targetMethod ) {
        if(branchUnit.ifBranchHasSensitiveApi){
            ImmediateBox a = new ImmediateBox(IntConstant.v(1));
            ImmediateBox b = new ImmediateBox(IntConstant.v(1));
            soot.jimple.ConditionExpr newExpr = soot.jimple.Jimple.v().newNeExpr(a.getValue(), b.getValue());

            u.setCondition(newExpr);

            SootMethod addCall = Scene.v().getSootClass("com.apposcopy.ella.runtime.Ella").getMethod("void m(int)");
            targetMethod.getActiveBody().getUnits().insertAfter(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(hsoStartCountNum))), u);

            InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(nonHsoStartCountNum)));
            targetMethod.getActiveBody().getUnits().insertBefore(invokeStmt, u.getTarget());

            IfStmt ifNewStmt = Jimple.v().newIfStmt(u.getCondition(), invokeStmt);
            targetMethod.getActiveBody().getUnits().swapWith(u, ifNewStmt);

            hsoStartCountNum++;
            nonHsoStartCountNum++;

        }else if(branchUnit.elseBranchHasSensitiveApi){
            ImmediateBox a = new ImmediateBox(IntConstant.v(0));
            ImmediateBox b = new ImmediateBox(IntConstant.v(1));
            soot.jimple.ConditionExpr newExpr = soot.jimple.Jimple.v().newNeExpr(a.getValue(), b.getValue());

            u.setCondition(newExpr);

            SootMethod addCall = Scene.v().getSootClass("com.apposcopy.ella.runtime.Ella").getMethod("void m(int)");
            targetMethod.getActiveBody().getUnits().insertAfter(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(nonHsoStartCountNum))), u);

            InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(hsoStartCountNum)));
            targetMethod.getActiveBody().getUnits().insertBefore(invokeStmt, u.getTarget());

            IfStmt ifNewStmt = Jimple.v().newIfStmt(u.getCondition(), invokeStmt);
            targetMethod.getActiveBody().getUnits().swapWith(u, ifNewStmt);

            hsoStartCountNum++;
            nonHsoStartCountNum++;
        }
    }

    private static void instrumentAPKRandom(IfStmt u, BranchUnit branchUnit, SootMethod targetMethod ) {
        //get next expresstion of u
        Unit nextUnitInELse = targetMethod.getActiveBody().getUnits().getSuccOf(u);

        if(branchUnit.ifBranchHasSensitiveApi){
            //insert u before u.getTarget(else branch)
            NopStmt nop = Jimple.v().newNopStmt();
            targetMethod.getActiveBody().getUnits().add(nop);
            IfStmt ifStmt = Jimple.v().newIfStmt(reverseCondition((ConditionExpr) u.getCondition()), nop);
            targetMethod.getActiveBody().getUnits().insertBefore(ifStmt, u.getTarget());

            // insert "int tmp = (int)Math.round(Math.random());" before u
            SootMethod toCall1 = Scene.v().getSootClass("java.lang.Math").getMethod("double random()");
            Local tmpRef1 = addDoubleRef(targetMethod.getActiveBody(), "tmpRef1");
            StaticInvokeExpr randomInvoke = Jimple.v().newStaticInvokeExpr(toCall1.makeRef());
            AssignStmt randomInvokeAssign = Jimple.v().newAssignStmt(tmpRef1, randomInvoke);

            SootMethod toCall2 = Scene.v().getSootClass("java.lang.Math").getMethod("long round(double)");
            Local tmpRef2 = addLongRef(targetMethod.getActiveBody(), "tmpRef2");
            StaticInvokeExpr roundInvoke = Jimple.v().newStaticInvokeExpr(toCall2.makeRef(), tmpRef1);
            AssignStmt roundInvokeAssign = Jimple.v().newAssignStmt(tmpRef2, roundInvoke);

            Local tmpRef3 = addIntRef(targetMethod.getActiveBody(), "tmpRef3");
            AssignStmt resultAssign = Jimple.v().newAssignStmt(tmpRef3, tmpRef2);

            targetMethod.getActiveBody().getUnits().insertBefore(randomInvokeAssign, u);
            targetMethod.getActiveBody().getUnits().insertBefore(roundInvokeAssign, u);
            targetMethod.getActiveBody().getUnits().insertBefore(resultAssign, u);

            ImmediateBox a = new ImmediateBox(IntConstant.v(0));
            ImmediateBox b = new ImmediateBox(tmpRef3);
            soot.jimple.ConditionExpr newExpr = soot.jimple.Jimple.v().newNeExpr(a.getValue(), b.getValue());
            IfStmt ifNewStmt = Jimple.v().newIfStmt(newExpr, ifStmt);

            targetMethod.getActiveBody().getUnits().swapWith(u, ifNewStmt);

        }else if(branchUnit.elseBranchHasSensitiveApi){

            //insert u before u.getTarget(if branch)
            NopStmt nop = Jimple.v().newNopStmt();
            targetMethod.getActiveBody().getUnits().add(nop);
            IfStmt ifStmt = Jimple.v().newIfStmt(reverseCondition((ConditionExpr) u.getCondition()), nop);
            targetMethod.getActiveBody().getUnits().insertBefore(ifStmt, nextUnitInELse);

            // insert "int tmp = (int)Math.round(Math.random());" before u
            SootMethod toCall1 = Scene.v().getSootClass("java.lang.Math").getMethod("double random()");
            Local tmpRef1 = addDoubleRef(targetMethod.getActiveBody(), "tmpRef1");
            StaticInvokeExpr randomInvoke = Jimple.v().newStaticInvokeExpr(toCall1.makeRef());
            AssignStmt randomInvokeAssign = Jimple.v().newAssignStmt(tmpRef1, randomInvoke);

            SootMethod toCall2 = Scene.v().getSootClass("java.lang.Math").getMethod("long round(double)");
            Local tmpRef2 = addLongRef(targetMethod.getActiveBody(), "tmpRef2");
            StaticInvokeExpr roundInvoke = Jimple.v().newStaticInvokeExpr(toCall2.makeRef(), tmpRef1);
            AssignStmt roundInvokeAssign = Jimple.v().newAssignStmt(tmpRef2, roundInvoke);

            Local tmpRef3 = addIntRef(targetMethod.getActiveBody(), "tmpRef3");
            AssignStmt resultAssign = Jimple.v().newAssignStmt(tmpRef3, tmpRef2);

            targetMethod.getActiveBody().getUnits().insertBefore(randomInvokeAssign, u);
            targetMethod.getActiveBody().getUnits().insertBefore(roundInvokeAssign, u);
            targetMethod.getActiveBody().getUnits().insertBefore(resultAssign, u);

            ImmediateBox a = new ImmediateBox(IntConstant.v(0));
            ImmediateBox b = new ImmediateBox(tmpRef3);
            soot.jimple.ConditionExpr newExpr = soot.jimple.Jimple.v().newNeExpr(a.getValue(), b.getValue());
            IfStmt ifNewStmt = Jimple.v().newIfStmt(newExpr, u.getTarget());

            targetMethod.getActiveBody().getUnits().swapWith(u, ifNewStmt);

        }

        targetMethod.getActiveBody().validate();
    }


    private static void instrumentElla(IfStmt u, BranchUnit branchUnit, SootMethod targetMethod ) {
        if(branchUnit.ifBranchHasSensitiveApi){

            SootMethod addCall = Scene.v().getSootClass("com.apposcopy.ella.runtime.Ella").getMethod("void m(int)");
            targetMethod.getActiveBody().getUnits().insertAfter(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(hsoStartCountNum))), u);

            InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(nonHsoStartCountNum)));
            targetMethod.getActiveBody().getUnits().insertBefore(invokeStmt, u.getTarget());

            IfStmt ifNewStmt = Jimple.v().newIfStmt(u.getCondition(), invokeStmt);
            targetMethod.getActiveBody().getUnits().swapWith(u, ifNewStmt);

            hsoStartCountNum++;
            nonHsoStartCountNum++;
        }else if(branchUnit.elseBranchHasSensitiveApi){
            SootMethod addCall = Scene.v().getSootClass("com.apposcopy.ella.runtime.Ella").getMethod("void m(int)");
            targetMethod.getActiveBody().getUnits().insertAfter(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(nonHsoStartCountNum))), u);

            InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(addCall.makeRef(), IntConstant.v(hsoStartCountNum)));
            targetMethod.getActiveBody().getUnits().insertBefore(invokeStmt, u.getTarget());

            IfStmt ifNewStmt = Jimple.v().newIfStmt(u.getCondition(), invokeStmt);
            targetMethod.getActiveBody().getUnits().swapWith(u, ifNewStmt);

            hsoStartCountNum++;
            nonHsoStartCountNum++;
        }

        targetMethod.getActiveBody().validate();
    }


    private static Local addTmpRef(Body body, String localName, String className)
    {
        Local tmpRef = Jimple.v().newLocal(localName, RefType.v(className));
        body.getLocals().add(tmpRef);
        return tmpRef;
    }

    private static Local addDoubleRef(Body body, String localName)
    {
        Local tmpRef = Jimple.v().newLocal(localName, DoubleType.v());
        body.getLocals().add(tmpRef);
        return tmpRef;
    }

    private static Local addLongRef(Body body, String localName)
    {
        Local tmpRef = Jimple.v().newLocal(localName, LongType.v());
        body.getLocals().add(tmpRef);
        return tmpRef;
    }

    private static Local addIntRef(Body body, String localName)
    {
        Local tmpRef = Jimple.v().newLocal(localName, IntType.v());
        body.getLocals().add(tmpRef);
        return tmpRef;
    }

    private static Local addTmpString(Body body)
    {
        Local tmpString = Jimple.v().newLocal("tmpString", RefType.v("java.lang.String"));
        body.getLocals().add(tmpString);
        return tmpString;
    }

    private static Local addTagString(Body body)
    {
        Local tmpString = Jimple.v().newLocal("tagString", RefType.v("java.lang.String"));
        body.getLocals().add(tmpString);
        return tmpString;
    }

    /**
     * in bytecode and Jimple the conditions in conditional binary
     * expressions are often reversed
     */
    protected static soot.Value reverseCondition(soot.jimple.ConditionExpr cond) {

        soot.jimple.ConditionExpr newExpr;
        if (cond instanceof soot.jimple.EqExpr) {
            newExpr = soot.jimple.Jimple.v().newNeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.NeExpr) {
            newExpr = soot.jimple.Jimple.v().newEqExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.GtExpr) {
            newExpr = soot.jimple.Jimple.v().newLeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.GeExpr) {
            newExpr = soot.jimple.Jimple.v().newLtExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.LtExpr) {
            newExpr = soot.jimple.Jimple.v().newGeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.LeExpr) {
            newExpr = soot.jimple.Jimple.v().newGtExpr(cond.getOp1(), cond.getOp2());
        }
        else {
            throw new RuntimeException("Unknown Condition Expr");
        }


        newExpr.getOp1Box().addAllTagsOf(cond.getOp1Box());
        newExpr.getOp2Box().addAllTagsOf(cond.getOp2Box());
        return newExpr;
    }
}
