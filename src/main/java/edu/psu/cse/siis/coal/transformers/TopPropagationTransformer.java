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
package edu.psu.cse.siis.coal.transformers;

import edu.psu.cse.siis.coal.transformers.AllTop;
import edu.psu.cse.siis.coal.values.BasePropagationValue;
import edu.psu.cse.siis.coal.values.TopPropagationValue;

/**
 * A "top" transformer, which maps everything to a "top" value. This is a singleton.
 */
public class TopPropagationTransformer extends AllTop<BasePropagationValue> {
  private static edu.psu.cse.siis.coal.transformers.TopPropagationTransformer instance = new edu.psu.cse.siis.coal.transformers.TopPropagationTransformer();

  /**
   * Returns the singleton instance for this class.
   * 
   * @return The singleton instance for this class.
   */
  public static edu.psu.cse.siis.coal.transformers.TopPropagationTransformer v() {
    return instance;
  }

  private TopPropagationTransformer() {
    super(TopPropagationValue.v());
  }
}
