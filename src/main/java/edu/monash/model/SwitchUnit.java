package edu.monash.model;

import soot.SootMethod;
import soot.Unit;

import java.util.ArrayList;
import java.util.List;

public class SwitchUnit {

    public SootMethod declareMethod;

    public List<String> branchInvokeCondition = new ArrayList<>();

    public List<UnitInfo> invokeMethods = new ArrayList<>();
}
