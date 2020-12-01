package edu.monash.utils;

import java.util.HashSet;
import java.util.Set;

public class CollectionHelper {

    public static <T> Set<T> getUniqueElements(Set<T> set1, Set<T> set2){
        // Prepare a union
        Set<T> union = new HashSet<>();
        union.addAll(set1);
        union.addAll(set2);
        // Prepare an intersection
        Set<T> intersection = new HashSet<T>(set1);
        intersection.retainAll(set2);
        // Subtract the intersection from the union
        union.removeAll(intersection);

        return union;
    }

    public static void main(String[] args) {
        Set<String> set1 = new HashSet<>();
        set1.add("1");
        set1.add("2");
        set1.add("3");
        set1.add("4");
        set1.add("5");

        Set<String> set2 = new HashSet<>();
        set2.add("1");
        set2.add("2");
        set2.add("6");
        set2.add("7");
        set2.add("8");
//        Set<String> res = getUniqueElements(set1, set2);
//        System.out.println(res);

        Set<String> intersection1 = new HashSet<String>(set1);
        intersection1.retainAll(set2);
        System.out.println(intersection1);
        System.out.println(set1);
        System.out.println(set2);
    }

}
