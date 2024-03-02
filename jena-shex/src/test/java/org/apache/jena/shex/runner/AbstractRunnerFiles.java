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

package org.apache.jena.shex.runner;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.arq.junit.runners.Directories;
import org.apache.jena.arq.junit.runners.RunnerOneTest;
import org.apache.jena.atlas.io.IndentedWriter;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Common super class for {@code @Runner(....)}
 * where the tests are defined by the contents of a file.
 */
public abstract class AbstractRunnerFiles extends ParentRunner<Runner> {
    private Description  description;
    private List<Runner> children = new ArrayList<>();

    // Includes and excludes are filenames with a directory.
    protected AbstractRunnerFiles(Class<? > klass, Function <String, Runnable> maker,
                                  Set<String> includes, Set<String> excludes) throws InitializationError {
        this(klass, maker, includes, excludes, null);
    }

    // Includes and excludes are filenames with a directory.
    protected AbstractRunnerFiles(Class<? > klass, Function <String, Runnable> maker,
                                  Set<String> includes, Set<String> excludes, String endsWith) throws InitializationError {
        super(klass);
        String testsDirEnv = System.getenv("TESTS_DIR");
        String testsEnv = System.getenv("TESTS");
        List<Pattern> selected = null;
        if (testsEnv != null) {
          selected = Arrays.stream(testsEnv.split(";"))
                  .map(s -> Pattern.compile(s))
                  .collect(Collectors.toList());
        }

        String label = ShexTests.getLabel(klass);
        if ( label == null )
            label = klass.getName();
        String prefix = ShexTests.getPrefix(klass);
        String[] directories = getDirectories(klass);
        description = Description.createSuiteDescription(label);

        for ( String directory : directories ) {
            // LEVEL per directory?
            if (testsDirEnv != null) {
                File override = new File(testsDirEnv);
                if (!override.exists())
                    throw new InitializationError("Can't resolve " + Path.of("").toAbsolutePath().toString());
                String subDir = new File(directory).getName();
                Path overriddenPath = Path.of(testsDirEnv, subDir);
                File overridden = new File(String.valueOf(overriddenPath));
                directory = String.valueOf(overridden.exists() ? overridden : override);
            }
            List<String> files = getFiles(directory, includes, excludes, endsWith, selected);
            if ( files.isEmpty() )
                //System.err.println("No files: "+label);
                throw new InitializationError("No files");

            for ( String file : files ) {
                RunnerOneTest runner = buildTest(file, maker, prefix);
                if ( runner != null ) {
                    description.addChild(runner.getDescription());
                    children.add(runner);
                }
            }
        }

        if ( ShexTests.VERBOSE ) {
            System.err.println(label);
            System.err.println("  inclusions    = "+includes.size());
            System.err.println("  exclusions    = "+excludes.size());
            System.err.println();
        }
    }

    protected final List<String> getFiles(String directory, Set<String> includes, Set<String> excludes, String endsWith, List<Pattern> selected) {
        Path src = Path.of(directory);
        BiPredicate<Path, BasicFileAttributes> predicate = (path,attr)->attr.isRegularFile() && path.toString().endsWith(".shex");

        List<String> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if ( includes.isEmpty() ) {
            try {
                Files.find(src, 1, predicate)
                    .filter(p-> endsWith == null || p.getFileName().toString().endsWith(endsWith))
                    .filter(p-> ! excludes.contains(p.getFileName().toString()))
                    .filter(p -> {
                        if (selected == null) return true;
                        String fn = p.getFileName().toString();
                        if (selected.stream().filter(pattern -> pattern.matcher(fn).matches()).findAny().isPresent())
                            return true;
                        skipped.add(fn);
                        return false;
                    })
                    .sorted()
                    .map(Path::toString)
                    .forEach(files::add);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (files.size() == 0)
                throw new RuntimeException("TESTS pattern " + System.getenv("TESTS") + " matched none of " + skipped.stream().map(l -> "\n  " + l).collect(Collectors.joining()));
        } else {
            includes.forEach(fn->files.add(fn));
        }
        return files;
    }

    // Print all manifests, top level and included.
    private static boolean PrintManifests = false;
    private static IndentedWriter out = IndentedWriter.stdout;

    public static RunnerOneTest buildTest(String filename, Function<String, Runnable> maker, String prefix) {
//        Description description = Description.createSuiteDescription(filename);
        Runnable runnable = maker.apply(filename);
        String name = StringUtils.isEmpty(prefix)
                ? fixupName(filename)
                : prefix+" "+fixupName(filename);
        return new RunnerOneTest(name, runnable);
    }

    public static String fixupName(String string) {
        // Keep Eclipse happy.
        string = string.replace('(', '[');
        string = string.replace(')', ']');

        // Keep IntelliJ happy
        string = string.replace(".shex", "");
        string = string.replace(".", " ");
        return string;
    }

    private static String[] getDirectories(Class<? > klass) throws InitializationError {
        Directories directories = klass.getAnnotation(Directories.class);
        if ( directories == null ) {
            throw new InitializationError(String.format("class '%s' must have a @Directories annotation", klass.getName()));
        }
        return directories.value();
    }

    @Override
    public Description getDescription() {
        return description;
    }

    @Override
    protected List<Runner> getChildren() {
        return children;
    }

    @Override
    protected Description describeChild(Runner child) {
        return child.getDescription();
    }

    @Override
    protected void runChild(Runner child, RunNotifier notifier) {
        child.run(notifier);
    }
}
