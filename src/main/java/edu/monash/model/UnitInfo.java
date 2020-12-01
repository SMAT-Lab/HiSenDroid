package edu.monash.model;

import soot.Unit;

public class UnitInfo {

    private Unit unit;

    private boolean isSensitive;

    private String declareMethod;

    public UnitInfo(Unit unit, String declareMethod){
        this.unit = unit;
        this.declareMethod = declareMethod;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public String getDeclareMethod() {
        return declareMethod;
    }

    public void setDeclareMethod(String declareMethod) {
        this.declareMethod = declareMethod;
    }

    public boolean isSensitive() {
        return isSensitive;
    }

    public void setSensitive(boolean sensitive) {
        isSensitive = sensitive;
    }
}
