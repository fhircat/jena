/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.shex.expressions;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.atlas.lib.InternalErrorException;

public class Cardinality {
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    public static final Cardinality PLUS = new Cardinality(1, UNBOUNDED);
    public static final Cardinality STAR = new Cardinality(0, UNBOUNDED);
    public static final Cardinality OPT = new Cardinality(0, 1);

    public final int min;
    public final int max;

    public Cardinality (int min, int max) {
        if (min < 0 || max < 0)
            throw new InternalErrorException("Negative bounds not allowed for a cardinality");
        this.min = min;
        this.max = max;
    }

    private static Pattern repeatRange = Pattern.compile(".(\\d+)(,(\\d+|\\*)?)?.");
    private String parsedFrom;
    public String getParsedFrom () {
        return parsedFrom;
    }

    public static Cardinality parse(String image) {
        int min = -1;
        int max = -1;
        switch(image) {
            case "*": min = 0 ; max = UNBOUNDED ; break;
            case "?": min = 0 ; max = 1 ;  break;
            case "+": min = 1 ; max = UNBOUNDED ; break;
            default: {
                Matcher matcher = repeatRange.matcher(image);
                if ( !matcher.matches() )
                    throw new InternalErrorException("ShExC: Unexpected cardinality: '"+image+"'");
                min = parseIntOrStar(matcher.group(1));
                if ( matcher.groupCount() != 3 )
                    throw new InternalErrorException("ShExC: Unexpected cardinality: '"+image+"'");
                String comma = matcher.group(2);
                if ( comma == null ) {
                    max = min;
                    break;
                }
                // Has a comma, may have something after it.
                max = matcher.group(3) != null ? parseIntOrStar(matcher.group(3)) : UNBOUNDED;
            }
        }
        Cardinality result = new Cardinality(min, max);
        result.parsedFrom = image;
        return result;
    }

    private static int parseIntOrStar(String str) {
        try {
            if (str.equals("*"))
                return UNBOUNDED;
            else
                return Integer.parseInt(str);
        } catch (NumberFormatException|NullPointerException ex) {
            throw new InternalErrorException("Number format exception");
        }
    }

    @Override
    public String toString() {
        return PrettyPrinter.asPrettyString(this);
    }

    @Override
    public int hashCode() {
        if (min > max)
            // empty interval
            return Objects.hash(2, 1);
        else
            return Objects.hash(max, min);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        Cardinality other = (Cardinality)obj;
        return max == other.max && min == other.min || min > max && other.min > other.max;
    }
}
