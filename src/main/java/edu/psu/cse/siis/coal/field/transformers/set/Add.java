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
package edu.psu.cse.siis.coal.field.transformers.set;

import edu.psu.cse.siis.coal.field.transformers.FieldTransformer;
import edu.psu.cse.siis.coal.field.transformers.set.SetFieldTransformer;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link FieldTransformer} for an add operation.
 */
public class Add extends SetFieldTransformer {

  @SuppressWarnings("unchecked")
  public Add(Object value) {
    this.add = new HashSet<>(2);
    if (value instanceof Set) {
      this.add.addAll((Set<Object>) value);
    } else {
      this.add.add(value);
    }
  }

  private Add(edu.psu.cse.siis.coal.field.transformers.set.Add add1, edu.psu.cse.siis.coal.field.transformers.set.Add add2) {
    this.add = new HashSet<>(add1.add);
    this.add.addAll(add2.add);
  }

  @Override
  public FieldTransformer compose(FieldTransformer secondFieldOperation) {
    if (secondFieldOperation instanceof edu.psu.cse.siis.coal.field.transformers.set.Add) {
      return new edu.psu.cse.siis.coal.field.transformers.set.Add(this, (edu.psu.cse.siis.coal.field.transformers.set.Add) secondFieldOperation).intern();
    } else {
      return super.compose(secondFieldOperation);
    }
  }
}
