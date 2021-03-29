package edu.anonymous;

import edu.anonymous.utils.ApplicationClassFilter;
import soot.*;
import soot.jimple.Jimple;
import soot.jimple.SpecialInvokeExpr;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class EmulatorDetector extends SceneTransformer {

    private String apkFileLocation = null;

    public EmulatorDetector(String apkFileLocation)
    {
        this.apkFileLocation = apkFileLocation;
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();

        for (Iterator<SootClass> iter = applicationClasses.snapshotIterator(); iter.hasNext();) {
            SootClass sootClass = iter.next();

            if (!sootClass.isConcrete()) {
                continue;
            }

            if(!ApplicationClassFilter.isApplicationClass(sootClass)){
                continue;
            }

            List<SootMethod> methodCopyList = new ArrayList<>(sootClass.getMethods());
            methodCopyList.stream().filter(methodCopy -> {
                return methodCopy.isConcrete() && methodCopy.hasActiveBody();
            }).forEach(methodCopy -> {
                if(methodCopy.getName().equals("onCreate") && !methodCopy.getName().contains("com.example.emulatorexample")){
                    System.out.println("-----Insert Emulator Detection Code in : "+methodCopy);

                    AtomicReference<SootMethod> sootCall = new AtomicReference<>();
                    Scene.v().getApplicationClasses().forEach(a->{
                        if(a.getName().contains("com.example.emulatorexample.MainActivity")){
                            a.getMethods().forEach(m->{
                                if(m.getSignature().contains("<com.example.emulatorexample.MainActivity: void hasAntiEmulatorOperation()>")){
                                    sootCall.set(m);
                                }
                            });
                        }
                    });

                    Local l = Jimple.v().newLocal("__throwee" , RefType.v("java.lang.NullPointerException"));

                    SpecialInvokeExpr virtInvExpr = Jimple.v().newSpecialInvokeExpr(l, sootCall.get().makeRef());
                    Unit u2 = Jimple.v().newInvokeStmt(virtInvExpr);
                    methodCopy.getActiveBody().getUnits().insertBefore(u2, methodCopy.getActiveBody().getUnits().getFirst());
                }
            });
        }
    }
}
