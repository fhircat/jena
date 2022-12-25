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

package org.apache.jena.shex.semact;

import org.apache.jena.atlas.json.io.JSWriter;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShExPathCalculator;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.expressions.ShapeExpression;
import org.apache.jena.shex.expressions.TripleExpression;
import org.apache.jena.shex.sys.ValidationContext;

import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// interface ExtractVar {
//     String run (String str);
// }

public class ShExMapSemanticActionPlugin implements SemanticActionPlugin {
    static String SemActIri = "http://shex.io/extensions/Map/";
    static Pattern ParsePattern, LeadPattern, LastPattern;

    static {
        String term = "(\\\"(?:(?:[^\\\\\\\"]|\\\\[^\\\"])+)\\\"|[spo])";
        ParsePattern = Pattern.compile("^ *(fail|print) *\\(((?:" + term + ", )*" + term + ")\\) *$");
        LeadPattern = Pattern.compile(term + ", ");
        LastPattern = Pattern.compile("((" + term + "))");
    }

    static Pattern ParsePatter1 = Pattern.compile("^ *(fail|print) *\\((\\\"(?:(?:[^\\\\\\\"]|\\\\[^\\\"])+)\\\"|[spo])\\) *$");

//    Map<Object, String> paths = new HashMap<>(); -- could be generic
    Map<ShapeExpression, String> shapeExprs = new HashMap<>();
    Map<TripleExpression, String> tripleExprs = new HashMap<>();
    Stack<ShapeExpression> shapeExprStack = new Stack<>();
    BindingNode root = null;
    BindingNode cur = null;
    Stack<BindingNode> nodeStack = new Stack<>();

    public BindingNode getBindingTree() {
        return root;
    }
    public void getBindingTreeAsJson(OutputStream os) {
        BindingTreeJsonWriter w = new BindingTreeJsonWriter();
        w.writeJson(os, root);
    }

    public ShExMapSemanticActionPlugin(ShexSchema schema) {
/*
        IndentedLineBuffer out = new IndentedLineBuffer();
        out.println("## Print");
        WriterShExC.print(out, schema);
        String s = out.toString();
        System.out.println(s);
*/
        ShExPathCalculator c = new ShExPathCalculator(schema);
        shapeExprs = c.getShapeExprs();
        tripleExprs = c.getTripleExprs();
    }

    @Override
    public List<String> getUris() {
        List<String> uris = new ArrayList<>();
        uris.add(SemActIri);
        return uris;
    }

    List<String> out = new ArrayList<>();

    public List<String> getOut () { return out; }

    @Override
    public boolean evaluateStart(SemAct semAct, ValidationContext vCxt, ShexSchema schema) {
        return parse(semAct, (str) -> resolveStartVar(str), vCxt, null);
    }

    @Override
    public boolean evaluateShapeExpr(SemAct semAct, ValidationContext vCxt, ShapeExpression shapeExpression, Node focus) {
        return parse(semAct, (str) -> resolveNodeVar(str, focus), vCxt, shapeExprs.get(shapeExpression));
    }

    @Override
    public boolean evaluateTripleExpr(SemAct semAct, ValidationContext vCxt, TripleExpression tripleExpression, Collection<Triple> triples) {
        Iterator<Triple> tripleIterator = triples.iterator();
        Triple triple = tripleIterator.hasNext() ? tripleIterator.next() : null; // should be one triple, as currently defined.
        return parse(semAct, (str) -> resolveTripleVar(str, triple), vCxt, tripleExprs.get(tripleExpression));
    }

    private boolean parse(SemAct semAct, TestSemanticActionPlugin.ExtractVar extractor, ValidationContext vCxt, String path) {

        List<ShapeExpression> curStack = new ArrayList<>();
        for (ValidationContext parent = vCxt; parent != null; parent = parent.getParent()) {
            ShapeExpression s = parent.getContextShape();
            if (s != null)
                curStack.add(s);
        }
        Collections.reverse(curStack);
        for (int depth = 0; depth < curStack.size(); ++depth) {
            ShapeExpression curShape = curStack.get(depth);
            int curHash = System.identityHashCode(curShape);
            if (depth > shapeExprStack.size() - 1) {
                shapeExprStack.add(curShape);
                if (root == null) {
                    root = cur = new BindingNode();
                } else {
                    cur = cur.nest();
                }
                nodeStack.push(cur);
                System.out.printf("> %x ", curHash);
            } else if (shapeExprStack.get(depth) == curShape) {
                System.out.printf("= %x ", curHash);
                if (depth == curStack.size() - 1)
                    trimStack(curStack, depth + 1);
            } else {
                trimStack(curStack, depth);
                shapeExprStack.add(curShape);
                cur = nodeStack.pop();
                System.out.printf("> %x ", curHash);
            }
        }

        String code = semAct.getCode();
        if (code == null)
            throw new RuntimeException(String.format("%s semantic action should not be null", SemActIri));

        Matcher m = ParsePattern.matcher(code);
        if (!m.find())
            throw new RuntimeException(String.format("%s semantic action %s did not match %s", SemActIri, code, ParsePattern));
        String function = m.group(1);
        String argument = m.group(2);

        List<String> printed = new ArrayList<>();
        while((m = LeadPattern.matcher(argument)).find()) {
            printed.add(extractor.run(m.group(1)));
            argument = argument.substring(m.end());
        }
        m = LastPattern.matcher(argument);
        m.find();
        printed.add(extractor.run(m.group(1)));
        out.add(path + ": " + String.join(", ", printed));
        String var = printed.get(0);
        String val = String.join(", ", printed.subList(1, printed.size()));
        cur.bind(var, val);
        System.out.printf(": %s\n", String.join(", ", printed));
        return function.equals("fail") ? false : true;
    }

    private void trimStack(List<ShapeExpression> curStack, int depth) {
        List<ShapeExpression> toTrim = shapeExprStack.subList(depth, shapeExprStack.size());

        // Make a reversed copy and close each one.
        List<ShapeExpression> toWalk = new ArrayList<>(toTrim);
        Collections.reverse(toWalk);
        toWalk.forEach(s -> System.out.printf("< %x ", System.identityHashCode(s)));

        toTrim.clear();
    }

    private static String resolveStartVar(String varName) {
        if (varName.charAt(0) == '"')
            return varName.replaceAll("\\\\(.)", "$1");

        throw new RuntimeException(String.format("%s semantic action argument %s was not a literal", SemActIri, varName));
    }

    private static String resolveNodeVar(String varName, Node focus) {
        if (varName.charAt(0) == '"')
            return varName.replaceAll("\\\\(.)", "$1");

        Node pos;
        switch (varName) {
            case "s": pos = focus; break;
            default:
                throw new RuntimeException(String.format("%s semantic action argument %s was not literal or 's', 'p', or 'o'", SemActIri, varName));
        }
        return pos.toString();
    }

    private static String resolveTripleVar(String varName, Triple triple) {
        if (varName.charAt(0) == '"')
            return varName.replaceAll("\\\\(.)", "$1");

        if (triple == null)
            return null;

        Node pos;
        switch (varName) {
            case "s": pos = triple.getSubject(); break;
            case "p": pos = triple.getPredicate(); break;
            case "o": pos = triple.getObject(); break;
            default:
                throw new RuntimeException(String.format("%s semantic action argument %s was not a literal or 's', 'p', or 'o'", SemActIri, varName));
        }
        return pos.toString();
    }

    public class BindingNode {
        Map<String , List<String>> vars = new HashMap<>();
        List<BindingNode> children = new ArrayList<>();

        public Map<String, List<String>> getVars() {
            return vars;
        }

        public List<BindingNode> getChildren() {
            return children;
        }

        void bind(String var, String val) {
            if (vars.containsKey(var))
                vars.get(var).add(val);
            else
                vars.put(var, new ArrayList<String>(Arrays.asList(val)));
        }

        BindingNode nest() {
            BindingNode ret = new BindingNode();
            children.add(ret);
            return ret;
        }
    }

    public class BindingTreeJsonWriter {
        public void writeJson(OutputStream os, BindingNode b) {
            JSWriter out = new JSWriter(os);
            out.startOutput();
            writeNode(out, b);
            out.finishOutput();
        }

        private void writeNode(JSWriter out, BindingNode b) {
            out.startArray();
            final boolean[] first = {true};
            Map<String, List<String>> bindings = b.getVars();
            if (!bindings.isEmpty()) {
                first[0] = false;
                out.startObject();
                bindings.forEach((var, vals) -> {
                    if (vals.size() == 1) {
                        out.pair(var, vals.get(0));
                    } else {
                        out.key(var);
                        out.startArray();
                        vals.forEach(val -> {
                            out.arrayElement(val);
                        });
                        out.finishArray();
                    }
                });
                out.finishObject();
            }
            for (BindingNode entry: b.getChildren()) {
                if (first[0]) {
                    first[0] = false;
                } else {
                    out.arraySep();
                }
                writeNode(out, entry);
            }
            out.finishArray();
        }
    }
}
