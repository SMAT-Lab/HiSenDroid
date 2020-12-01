/*
 * Copyright (C) 2015 The Pennsylvania State University and the University of Wisconsin
 * Systems and Internet Infrastructure Security Laboratory
 *
 * Author: Damien Octeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.psu.cse.siis.coal;

/**
 * Global constants.
 */
public class Constants {
  public static final String ANY_STRING = "(.*)";
  public static final String ANY_CLASS = "(.*)";
  public static final int ANY_INT = -1;
  public static final int VALUE_LIMIT = 256;
  public static final int INSTANCE_INVOKE_BASE_INDEX = -1;
  public static final String NULL_STRING = "NULL-CONSTANT";

  public static final String CONSTANT = "CONSTANT";
  public static final String CLASS_CONSTANT = "CONSTANT";
  public static final String PARAMETER_REF = "PARAMETER_REF";
  public static final String CALCULATE_EXPR = "CALCULATE_EXPR";
  public static final String IDENTIFY_EXPR = "IDENTIFY_EXPR";
  public static final String INSTANCE_FIELD_REF = "INSTANCE_FIELD_REF";
  public static final String STATIC_FIELD_REF = "INSTANCE_FIELD_REF";
  public static final String INVOKE_EXPR = "INVOKE_EXPR";
  public static final String OTHER = "OTHER";
  public static final String CAST_EXPR = "CAST_EXPR";
  public static final String NEW_EXPR = "NEW_EXPR";
  public static final String SYSTEM = "_SYSTEM_";
  public static final String FILE = "_FILE_";
  public static final String ANDROID_SYSTEM = "_ANDROID_SYSTEM_";
  public static final String ANDROID_DATABASE = "_ANDROID_DATABASE_";
  public static final String ANDROID_ACTIVITY = "_ANDROID_ACTIVITY_";
  public static final String ANDROID_NET = "_ANDROID_NET_";
  public static final String ANDROID_MSG = "_ANDROID_OS_MESSAGE_";
  public static final String ANDROID_CONTENT = "_ANDROID_CONTENT_";
  public static final String DIRECTION = "->";

  public static class DefaultActions {
    public static class Scalar {
      public static final String NULL = "null";
      public static final String REPLACE = "replace";
    }

    public static class Set {
      public static final String ADD = "add";
      public static final String REMOVE = "remove";
      public static final String CLEAR = "clear";
      public static final String ADD_ALL = "addAll";
      public static final String REPLACE_ALL = "replaceAll";
    }

    public static final String COMPOSE = "compose";
  }

  public static class DefaultArgumentTypes {
    public static class Scalar {
      public static final String STRING = "String";
      public static final String CLASS = "Class";
      public static final String INT = "int";
      public static final String CLASS_CONSTANT = "CLASS_CONSTANT";
    }

    public static class Set {
      public static final String STRING = "Set<String>";
      public static final String CLASS = "Set<Class>";
      public static final String INT = "Set<int>";
    }
  }
}
