/*
 * * Copyright 2016 Intuit Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sprinkle;

/**
 * Created by mboussarov on 6/26/15.
 */
public class Nothing {
    // Just one "nothing" instance
    private static Nothing nothing = new Nothing();

    // Get the "nothing", no more nulls
    public static Nothing nothing() {
        return nothing;
    }
}
