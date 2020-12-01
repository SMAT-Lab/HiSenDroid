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
package edu.psu.cse.siis.coal.arguments;

import edu.psu.cse.siis.coal.arguments.LanguageConstraints.Call;
import edu.psu.cse.siis.coal.arguments.MethodReturnValueAnalysis;
import edu.psu.cse.siis.coal.arguments.SourceDescriptor;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A {@link MethodReturnValueAnalysis} for COAL sources.
 */
public class SourceMethodReturnValueAnalysis extends MethodReturnValueAnalysis {

  @Override
  public List<Object> computeMethodReturnValues(Call call) {
    Stmt stmt = call.stmt;
    if (!stmt.containsInvokeExpr() || !(stmt.getInvokeExpr() instanceof InstanceInvokeExpr)) {
      return Collections.singletonList((Object) "(.*)");
    } else {
      return Collections.singletonList((Object) new SourceDescriptor(((InstanceInvokeExpr) stmt
          .getInvokeExpr()).getBase(), stmt));
    }

  }
}
