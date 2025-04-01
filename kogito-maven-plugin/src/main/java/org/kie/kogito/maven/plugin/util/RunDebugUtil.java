/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.kie.kogito.maven.plugin.util;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.logging.Log;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class RunDebugUtil {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.US);

    public static void buildProject(Log log) {
        // Here the logic to rebuild/package the project
        log.info("Building KOGITO project");
    }

    public static void runProject(File workingDir, String buildDirectory, String finalName, Log log) throws IOException {
        executeProject(workingDir, buildDirectory, finalName, log, false);
    }

    public static void debugProject(File workingDir, String buildDirectory, String finalName, Log log) throws IOException {
        executeProject(workingDir, buildDirectory, finalName, log, true);
    }

    private static void executeProject(File workingDir, String buildDirectory, String finalName, Log log, boolean debug) throws IOException {
        String executable;
        try {
            executable = getJavaExecutable();
        } catch (IOException e) {
            log.warn("Unable to autodetect 'java' path, using 'java' from the environment.");
            executable = "java";
        }
        Path generatedJarPath = Paths.get(buildDirectory, finalName);
        CommandLine cli = new CommandLine(executable, workingDir, generatedJarPath, debug);
        Console console = System.console();
        ExecutingThread executingThread = new ExecutingThread(cli, log, console);
        startWatchService(workingDir,
                console,
                executingThread,
                cli,
                log);
        startExecution(console, executingThread, cli, log);
    }

    private static void startWatchService(File workingDir,
            Console console,
            ExecutingThread executingThread, CommandLine cli, Log log) throws IOException {
        Path srcDir = workingDir.toPath().resolve("src");
        //        log.info("Starting watch service for " + srcDir);
        WatchService watcher = FileSystems.getDefault().newWatchService();
        Map<WatchKey, Path> registeredKeys = registerAll(watcher, srcDir, log);
        //        log.info("Registered paths " + registeredKeys);
        getWatcherThread(watcher, registeredKeys, console, executingThread, cli, log).start();
    }

    /**
     * Register the given directory with the WatchService
     */
    private static void register(WatchService watcher, Path dir,
            Map<WatchKey, Path> keys,
            Log log) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        Path prev = keys.get(key);
        if (prev == null) {
            //            log.info("Registering watch key for " + dir);
        } else {
            if (!dir.equals(prev)) {
                log.info(String.format("update: %s -> %s\n", prev, dir));
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private static Map<WatchKey, Path> registerAll(final WatchService watcher, final Path start, final Log log) throws IOException {
        // register directory and sub-directories
        Map<WatchKey, Path> toReturn = new HashMap<>();
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                register(watcher, dir, toReturn, log);
                return FileVisitResult.CONTINUE;
            }
        });
        return toReturn;
    }

    private static void waitCommand(Console console, ExecutingThread executingThread, CommandLine cli, Log log) {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        readCommandLine(console, executingThread, cli, log);
        //        log.info("Done");
    }

    private static void readCommandLine(Console console, ExecutingThread executingThread, CommandLine cli, Log log) {
        console.printf("Press [h] to hot-reload, [x] to exit the program >");
        char input = console.readPassword()[0];
        switch (input) {
            case 'x':
                stopExecution(executingThread);
                break;
            case 'h':
                hotReload(console, executingThread, cli, log);
                break;
            default:
                readCommandLine(console, executingThread, cli, log);
        }
    }

    private static void startExecution(Console console, ExecutingThread executingThread, CommandLine cli, Log log) {
        //        log.info("Start execution");
        executingThread.start();
        waitCommand(console, executingThread, cli, log);
    }

    private static void stopExecution(ExecutingThread executingThread) {
        //        executingThread.log.info("Stopping execution");
        executingThread.process.destroy();
        executingThread.interrupt();
        while (executingThread.isAlive()) {
            executingThread.log.info("Wait thread to stop");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                executingThread.log.info("Interrupted");
            }
        }
    }

    private static void hotReload(Console console, ExecutingThread executingThread, CommandLine cli, Log log) {
        //        log.info("Hot-reload");
        console.printf("hot-reload");
        stopExecution(executingThread);
        buildProject(log);
        ExecutingThread newExecutingThread = new ExecutingThread(cli, log, console);
        startExecution(console, newExecutingThread, cli, log);
    }

    /**
     * Get the path of the javac tool executable: try to find it depending the OS or the <code>java.home</code>
     * system property or the <code>JAVA_HOME</code> environment variable.
     *
     * @return the path of the Javadoc tool
     * @throws IOException if not found
     */
    private static String getJavaExecutable()
            throws IOException {
        String javaCommand = "java" + (isWindows() ? ".exe" : "");

        String javaHome = System.getProperty("java.home");
        File javaExe;
        if (OS_NAME.toLowerCase().contains("aix")) {
            javaExe = new File(javaHome + File.separator + ".." + File.separator + "sh", javaCommand);
        } else if (OS_NAME.toLowerCase().contains("mac os x")) {
            javaExe = new File(javaHome + File.separator + "bin", javaCommand);
        } else {
            javaExe = new File(javaHome + File.separator + ".." + File.separator + "bin", javaCommand);
        }

        // ----------------------------------------------------------------------
        // Try to find javacExe from JAVA_HOME environment variable
        // ----------------------------------------------------------------------
        if (!javaExe.isFile()) {
            Properties env = getSystemEnv();
            javaHome = env.getProperty("JAVA_HOME");
            if (javaHome == null || javaHome.isEmpty()) {
                throw new IOException("The environment variable JAVA_HOME is not correctly set.");
            }
            if (!new File(javaHome).isDirectory()) {
                throw new IOException(
                        "The environment variable JAVA_HOME=" + javaHome + " doesn't exist or is not a valid directory.");
            }

            javaExe = new File(env.getProperty("JAVA_HOME") + File.separator + "bin", javaCommand);
        }

        if (!javaExe.isFile()) {
            throw new IOException("The javadoc executable '" + javaExe
                    + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable.");
        }

        return javaExe.getAbsolutePath();
    }

    private static boolean isWindows() {
        return OS_NAME.contains("windows");
    }

    private static Properties getSystemEnv() {
        Properties toReturn = new Properties();
        toReturn.putAll(System.getenv());
        return toReturn;
    }

    private static Thread getWatcherThread(WatchService watcher,
            Map<WatchKey, Path> registeredKeys,
            Console console, ExecutingThread executingThread, CommandLine cli, Log log) {
        return new Thread(() -> {
            log.info("Watcher thread started for " + watcher);
            for (;;) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException x) {
                    return;
                }
                //                log.info("Watcher thread started for " + key);
                for (WatchEvent<?> event : key.pollEvents()) {
                    log.info("WatchEvent " + event);
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    } else if (kind == ENTRY_CREATE) {
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path name = ev.context();
                        Path dir = registeredKeys.get(key);
                        Path child = dir.resolve(name);
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            try {
                                registerAll(watcher, child, log);
                            } catch (IOException e) {
                                log.error(e);
                                // ignore for the moment being
                            }
                        }
                    }
                    hotReload(console, executingThread, cli, log);
                }

                // Reset the key -- this step is critical if you want to
                // receive further watch events.  If the key is no longer valid,
                // the directory is inaccessible so exit the loop.
                boolean valid = key.reset();
                if (!valid) {
                    log.info("BREAK");
                    break;
                }
            }
        });
    }

    private static class ExecutingThread extends Thread {

        private final CommandLine cli;
        private final Log log;
        private final Console console;
        private Process process;

        ExecutingThread(CommandLine cli, Log log, Console console) {
            this.cli = cli;
            this.log = log;
            this.console = console;
        }

        @Override
        public void run() {
            try {
                console.printf("Starting %s", cli.command());
                process = Runtime.getRuntime().exec(cli.cmdarray, cli.environmentProperties, cli.workingDir);
                getConsoleWriterThread(process.getErrorStream(), console).start();
                getConsoleWriterThread(process.getInputStream(), console).start();
            } catch (Exception e) {
                log.error(e);
            }
        }
    }

    private static Thread getConsoleWriterThread(InputStream inputStream, Console console) {
        return new Thread(() -> {
            try (BufferedReader bufferedReaderStream = new BufferedReader(new InputStreamReader(inputStream))) {
                bufferedReaderStream.lines().forEach(line -> console.printf("%s\n", line));
            } catch (Exception e) {
                console.printf("%s\n", e.getMessage());
            }
        });
    }

    private static class CommandLine {
        final String[] cmdarray;
        final String[] environmentProperties;
        final File workingDir;

        CommandLine(String executable, File workingDir, Path generatedJarPath, boolean debug) {
            List<String> commands = new ArrayList<>();
            commands.add(executable);
            if (debug) {
                commands.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000");
            }
            commands.add("-jar");
            commands.add(generatedJarPath.toString());
            this.cmdarray = commands.toArray(String[]::new);
            environmentProperties = new String[0];
            this.workingDir = workingDir;
        }

        public String command() {
            return String.join(" ", cmdarray);
        }
    }
}
