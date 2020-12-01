package edu.monash.model;

import soot.SootMethod;
import soot.Unit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BranchUnit {

    /**
     * The declare method of HSO
     */
    public SootMethod declareMethod;

    /**
     * The specific branch unit of HSO
     */
    public Unit branchInvokeUnit;

    /**
     * The stmt LIST<UnitInfo> in IF branch of HSO
     */
    public List<UnitInfo> ifBranchInvokeMethods = new ArrayList<>();

    /**
     * The stmt LIST<String> in IF branch of HSO
     */
    public List<String> ifBranchInvokeMethodList = new ArrayList<>();

    /**
     * Sensitive Flag of IF branch in HSO
     */
    public boolean ifBranchHasSensitiveApi;

    /**
     * The stmt LIST<UnitInfo> in ELSE branch of HSO
     */
    public List<UnitInfo> elseBranchInvokeMethods = new ArrayList<>();

    /**
     * The stmt LIST<String> in ELSE branch of HSO
     */
    public List<String> elseBranchInvokeMethodList = new ArrayList<>();

    /**
     * Sensitive Flag of ELSE branch in HSO
     */
    public boolean elseBranchHasSensitiveApi;

    /**
     * Trigger condition API list
     */
    public List<String> branchConditionValues = new ArrayList<>();

    /**
     * Hidden sensitive behaviors[Store API]
     */
    public Set<String> sensitiveAPIsInIfBranch = new HashSet<>();

    public Set<String> sensitiveAPIsInelseBranch = new HashSet<>();

    public Set<String> uniqueSensitiveAPIs = new HashSet<>();

    public boolean containHSO;

    public boolean hasHSDataLeaks;

    public boolean isBranchDifferent;

    public boolean ifHasDataDependency;

    public boolean elseHasDataDependency;

    public boolean ifBranchIsHSO = false;

    public boolean elseBranchIsHSO = false;

    public boolean isBranchHSO = false;

    /**
     * variables
     */
    public Set<String> variableInTrigger = new HashSet<>();

    public Set<String> variableInIfBranch = new HashSet<>();

    public Set<String> variableInElseBranch = new HashSet<>();

    /**
     * CommonCaese
     */
    public boolean hasCommonCaese = false;

    /**
     * Trigger condition Contains System API
     */
    public boolean tcContainsSys = false;

    /**
     * triggerConditions APIs
     */
    public Set<String> triggerConditions = new HashSet<>();

    /**
     * branch - condition diff
     */
    public boolean triggerBranchHasSameAPIInIf = false;

    public boolean triggerBranchHasSameAPIInElse = false;

}
