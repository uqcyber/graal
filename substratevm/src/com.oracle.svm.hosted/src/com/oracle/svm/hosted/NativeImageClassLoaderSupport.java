/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtracter;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.util.AnnotationExtracter;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.module.Modules;

public class NativeImageClassLoaderSupport {

    private final List<Path> imagecp;
    private final List<Path> buildcp;
    private final List<Path> imagemp;
    private final List<Path> buildmp;

    private final EconomicMap<URI, EconomicSet<String>> classes;
    private final EconomicMap<URI, EconomicSet<String>> packages;
    private final EconomicSet<String> emptySet;

    private final ClassPathClassLoader classPathClassLoader;
    private final ClassLoader classLoader;

    public final ModuleFinder upgradeAndSystemModuleFinder;
    public final ModuleLayer moduleLayerForImageBuild;
    public final ModuleFinder modulepathModuleFinder;

    public final AnnotationExtracter annotationExtracter;

    static final class ClassPathClassLoader extends URLClassLoader {
        ClassPathClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }
    }

    protected NativeImageClassLoaderSupport(ClassLoader defaultSystemClassLoader, String[] classpath, String[] modulePath) {

        classes = EconomicMap.create();
        packages = EconomicMap.create();
        emptySet = EconomicSet.create();

        classPathClassLoader = new ClassPathClassLoader(Util.verifyClassPathAndConvertToURLs(classpath), defaultSystemClassLoader);

        imagecp = Arrays.stream(classPathClassLoader.getURLs())
                        .map(Util::urlToPath)
                        .collect(Collectors.toUnmodifiableList());

        String builderClassPathString = System.getProperty("java.class.path");
        String[] builderClassPathEntries = builderClassPathString.isEmpty() ? new String[0] : builderClassPathString.split(File.pathSeparator);
        if (Arrays.asList(builderClassPathEntries).contains(".")) {
            VMError.shouldNotReachHere("The classpath of " + NativeImageGeneratorRunner.class.getName() +
                            " must not contain \".\". This can happen implicitly if the builder runs exclusively on the --module-path" +
                            " but specifies the " + NativeImageGeneratorRunner.class.getName() + " main class without --module.");
        }
        buildcp = Arrays.stream(builderClassPathEntries)
                        .map(Path::of)
                        .map(Util::toRealPath)
                        .collect(Collectors.toUnmodifiableList());

        imagemp = Arrays.stream(modulePath)
                        .map(Path::of)
                        .map(Util::toRealPath)
                        .collect(Collectors.toUnmodifiableList());

        buildmp = Optional.ofNullable(System.getProperty("jdk.module.path")).stream()
                        .flatMap(s -> Arrays.stream(s.split(File.pathSeparator)))
                        .map(Path::of)
                        .map(Util::toRealPath)
                        .collect(Collectors.toUnmodifiableList());

        upgradeAndSystemModuleFinder = createUpgradeAndSystemModuleFinder();
        ModuleLayer moduleLayer = createModuleLayer(imagemp.toArray(Path[]::new), classPathClassLoader);
        adjustBootLayerQualifiedExports(moduleLayer);
        moduleLayerForImageBuild = moduleLayer;

        classLoader = getSingleClassloader(moduleLayer);

        modulepathModuleFinder = ModuleFinder.of(modulepath().toArray(Path[]::new));

        annotationExtracter = new SubstrateAnnotationExtracter();
    }

    List<Path> classpath() {
        return Stream.concat(imagecp.stream(), buildcp.stream()).distinct().collect(Collectors.toList());
    }

    List<Path> applicationClassPath() {
        return imagecp;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void loadAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
        new LoadClassHandler(executor, imageClassLoader).run();
    }

    private HostedOptionParser hostedOptionParser;
    private OptionValues parsedHostedOptions;
    private List<String> remainingArguments;

    public void setupHostedOptionParser(List<String> arguments) {
        hostedOptionParser = new HostedOptionParser(getClassLoader());
        remainingArguments = Collections.unmodifiableList((hostedOptionParser.parse(arguments)));
        parsedHostedOptions = new OptionValues(hostedOptionParser.getHostedValues());
    }

    public HostedOptionParser getHostedOptionParser() {
        return hostedOptionParser;
    }

    public List<String> getRemainingArguments() {
        return remainingArguments;
    }

    public OptionValues getParsedHostedOptions() {
        return parsedHostedOptions;
    }

    public EconomicSet<String> classes(URI container) {
        return classes.get(container, emptySet);
    }

    public EconomicSet<String> packages(URI container) {
        return packages.get(container, emptySet);
    }

    public boolean noEntryForURI(EconomicSet<String> set) {
        return set == emptySet;
    }

    protected static class Util {

        static URL[] verifyClassPathAndConvertToURLs(String[] classpath) {
            Stream<Path> pathStream = new LinkedHashSet<>(Arrays.asList(classpath)).stream().flatMap(Util::toClassPathEntries);
            return pathStream.map(v -> {
                try {
                    return toRealPath(v).toUri().toURL();
                } catch (MalformedURLException e) {
                    throw UserError.abort("Invalid classpath element '%s'. Make sure that all paths provided with '%s' are correct.", v, SubstrateOptions.IMAGE_CLASSPATH_PREFIX);
                }
            }).toArray(URL[]::new);
        }

        static Path toRealPath(Path p) {
            try {
                return p.toRealPath();
            } catch (IOException e) {
                throw UserError.abort("Path entry '%s' does not map to a real path.", p);
            }
        }

        static Stream<Path> toClassPathEntries(String classPathEntry) {
            Path entry = ClasspathUtils.stringToClasspath(classPathEntry);
            if (entry.endsWith(ClasspathUtils.cpWildcardSubstitute)) {
                try {
                    return Files.list(entry.getParent()).filter(ClasspathUtils::isJar);
                } catch (IOException e) {
                    return Stream.empty();
                }
            }
            if (Files.isReadable(entry)) {
                return Stream.of(entry);
            }
            return Stream.empty();
        }

        static Path urlToPath(URL url) {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                throw VMError.shouldNotReachHere();
            }
        }
    }

    private ModuleLayer createModuleLayer(Path[] modulePaths, ClassLoader parent) {
        ModuleFinder modulePathsFinder = ModuleFinder.of(modulePaths);
        Set<String> moduleNames = modulePathsFinder.findAll().stream().map(moduleReference -> moduleReference.descriptor().name()).collect(Collectors.toSet());

        /**
         * When building a moduleLayer for the module-path passed to native-image we need to be able
         * to resolve against system modules that are not used by the moduleLayer in which the
         * image-builder got loaded into. To do so we use {@link upgradeAndSystemModuleFinder} as
         * {@code ModuleFinder after} in
         * {@link Configuration#resolve(ModuleFinder, ModuleFinder, Collection)}.
         */
        Configuration configuration = ModuleLayer.boot().configuration().resolve(modulePathsFinder, upgradeAndSystemModuleFinder, moduleNames);
        /**
         * For the modules we want to build an image for, a ModuleLayer is needed that can be
         * accessed with a single classloader so we can use it for {@link ImageClassLoader}.
         */
        return ModuleLayer.defineModulesWithOneLoader(configuration, List.of(ModuleLayer.boot()), parent).layer();
    }

    /**
     * Gets a finder that locates the upgrade modules and the system modules, in that order. Upgrade
     * modules are used when mx environment variable {@code MX_BUILD_EXPLODED=true} is used.
     */
    private static ModuleFinder createUpgradeAndSystemModuleFinder() {
        ModuleFinder finder = ModuleFinder.ofSystem();
        ModuleFinder upgradeModulePath = finderFor("jdk.module.upgrade.path");
        if (upgradeModulePath != null) {
            finder = ModuleFinder.compose(upgradeModulePath, finder);
        }
        return finder;
    }

    /**
     * Creates a finder from a module path specified by the {@code prop} system property.
     */
    private static ModuleFinder finderFor(String prop) {
        String s = System.getProperty(prop);
        if (s == null || s.isEmpty()) {
            return null;
        } else {
            String[] dirs = s.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir : dirs) {
                paths[i++] = Path.of(dir);
            }
            return ModuleFinder.of(paths);
        }
    }

    private static void adjustBootLayerQualifiedExports(ModuleLayer layer) {
        /*
         * For all qualified exports packages of modules in the the boot layer we check if layer
         * contains modules that satisfy such qualified exports. If we find a match we perform a
         * addExports.
         */
        for (Module module : ModuleLayer.boot().modules()) {
            for (ModuleDescriptor.Exports export : module.getDescriptor().exports()) {
                for (String target : export.targets()) {
                    Optional<Module> optExportTargetModule = layer.findModule(target);
                    if (optExportTargetModule.isEmpty()) {
                        continue;
                    }
                    Module exportTargetModule = optExportTargetModule.get();
                    if (module.isExported(export.source(), exportTargetModule)) {
                        continue;
                    }
                    Modules.addExports(module, export.source(), exportTargetModule);
                }
            }
        }
    }

    private ClassLoader getSingleClassloader(ModuleLayer moduleLayer) {
        ClassLoader singleClassloader = classPathClassLoader;
        for (Module module : moduleLayer.modules()) {
            ClassLoader moduleClassLoader = module.getClassLoader();
            if (singleClassloader == classPathClassLoader) {
                singleClassloader = moduleClassLoader;
            } else {
                VMError.guarantee(singleClassloader == moduleClassLoader);
            }
        }
        return singleClassloader;
    }

    private static void implAddReadsAllUnnamed(Module module) {
        try {
            Method implAddReadsAllUnnamed = Module.class.getDeclaredMethod("implAddReadsAllUnnamed");
            ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, NativeImageClassLoaderSupport.class, Module.class);
            implAddReadsAllUnnamed.setAccessible(true);
            implAddReadsAllUnnamed.invoke(module);
        } catch (ReflectiveOperationException | NoSuchElementException e) {
            VMError.shouldNotReachHere("Could reflectively call Module.implAddReadsAllUnnamed", e);
        }
    }

    protected List<Path> modulepath() {
        return Stream.concat(imagemp.stream(), buildmp.stream()).collect(Collectors.toUnmodifiableList());
    }

    protected List<Path> applicationModulePath() {
        return imagemp;
    }

    public Optional<Module> findModule(String moduleName) {
        return moduleLayerForImageBuild.findModule(moduleName);
    }

    void processClassLoaderOptions() {

        if (NativeImageClassLoaderOptions.ListModules.getValue(parsedHostedOptions)) {
            processListModulesOption(moduleLayerForImageBuild);
        }

        processOption(NativeImageClassLoaderOptions.AddExports).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                Modules.addExportsToAllUnnamed(val.module, val.packageName);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addExports(val.module, val.packageName, targetModule);
                }
            }
        });
        processOption(NativeImageClassLoaderOptions.AddOpens).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                Modules.addOpensToAllUnnamed(val.module, val.packageName);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addOpens(val.module, val.packageName, targetModule);
                }
            }
        });
        processOption(NativeImageClassLoaderOptions.AddReads).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                implAddReadsAllUnnamed(val.module);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addReads(val.module, targetModule);
                }
            }
        });
    }

    private static void processListModulesOption(ModuleLayer layer) {
        Class<?> launcherHelperClass = ReflectionUtil.lookupClass(false, "sun.launcher.LauncherHelper");
        Method initOutputMethod = ReflectionUtil.lookupMethod(launcherHelperClass, "initOutput", boolean.class);
        Method showModuleMethod = ReflectionUtil.lookupMethod(launcherHelperClass, "showModule", ModuleReference.class);

        boolean first = true;
        for (ModuleLayer moduleLayer : allLayers(layer)) {
            List<ResolvedModule> resolvedModules = moduleLayer.configuration().modules().stream()
                            .sorted(Comparator.comparing(ResolvedModule::name))
                            .collect(Collectors.toList());
            if (first) {
                try {
                    initOutputMethod.invoke(null, false);
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere("Unable to use " + initOutputMethod + " to set printing with " + showModuleMethod + " to System.out.", e);
                }
                first = false;
            } else if (!resolvedModules.isEmpty()) {
                System.out.println();
            }
            for (ResolvedModule resolvedModule : resolvedModules) {
                try {
                    showModuleMethod.invoke(null, resolvedModule.reference());
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere("Unable to use " + showModuleMethod + " for printing list of modules.", e);
                }
            }
        }

        throw new InterruptImageBuilding("");
    }

    public void propagateQualifiedExports(String fromTargetModule, String toTargetModule) {
        Optional<Module> optFromTarget = moduleLayerForImageBuild.findModule(fromTargetModule);
        Optional<Module> optToTarget = moduleLayerForImageBuild.findModule(toTargetModule);
        VMError.guarantee(optFromTarget.isPresent() && optToTarget.isPresent());
        Module toTarget = optToTarget.get();
        Module fromTarget = optFromTarget.get();

        allLayers(moduleLayerForImageBuild).stream().flatMap(layer -> layer.modules().stream()).forEach(m -> {
            if (!m.equals(toTarget)) {
                for (String p : m.getPackages()) {
                    if (m.isExported(p, fromTarget)) {
                        Modules.addExports(m, p, toTarget);
                    }
                    if (m.isOpen(p, fromTarget)) {
                        Modules.addOpens(m, p, toTarget);
                    }
                }
            }
        });
    }

    public static List<ModuleLayer> allLayers(ModuleLayer moduleLayer) {
        /** Implementation taken from {@link ModuleLayer#layers()} */
        List<ModuleLayer> allLayers = new ArrayList<>();
        Set<ModuleLayer> visited = new HashSet<>();
        Deque<ModuleLayer> stack = new ArrayDeque<>();
        visited.add(moduleLayer);
        stack.push(moduleLayer);

        while (!stack.isEmpty()) {
            ModuleLayer layer = stack.pop();
            allLayers.add(layer);

            // push in reverse order
            for (int i = layer.parents().size() - 1; i >= 0; i--) {
                ModuleLayer parent = layer.parents().get(i);
                if (!visited.contains(parent)) {
                    visited.add(parent);
                    stack.push(parent);
                }
            }
        }
        return allLayers;
    }

    private Stream<AddExportsAndOpensAndReadsFormatValue> processOption(OptionKey<LocatableMultiOptionValue.Strings> specificOption) {
        Stream<Pair<String, OptionOrigin>> valuesWithOrigins = specificOption.getValue(parsedHostedOptions).getValuesWithOrigins();
        Stream<AddExportsAndOpensAndReadsFormatValue> parsedOptions = valuesWithOrigins.flatMap(valWithOrig -> {
            try {
                return Stream.of(asAddExportsAndOpensAndReadsFormatValue(specificOption, valWithOrig));
            } catch (UserError.UserException e) {
                if (ModuleSupport.modulePathBuild && classpath().isEmpty()) {
                    throw e;
                } else {
                    /*
                     * Until we switch to always running the image-builder on module-path we have to
                     * be tolerant if invalid --add-exports -add-opens or --add-reads options are
                     * used. GR-30433
                     */
                    System.out.println("Warning: " + e.getMessage());
                    return Stream.empty();
                }
            }
        });
        return parsedOptions;
    }

    private static final class AddExportsAndOpensAndReadsFormatValue {
        private final Module module;
        private final String packageName;
        private final List<Module> targetModules;

        private AddExportsAndOpensAndReadsFormatValue(Module module, String packageName, List<Module> targetModules) {
            this.module = module;
            this.packageName = packageName;
            this.targetModules = targetModules;
        }
    }

    private AddExportsAndOpensAndReadsFormatValue asAddExportsAndOpensAndReadsFormatValue(OptionKey<?> option, Pair<String, OptionOrigin> valueOrigin) {
        OptionOrigin optionOrigin = valueOrigin.getRight();
        String optionValue = valueOrigin.getLeft();

        boolean reads = option.equals(NativeImageClassLoaderOptions.AddReads);
        String format = reads ? NativeImageClassLoaderOptions.AddReadsFormat : NativeImageClassLoaderOptions.AddExportsAndOpensFormat;
        String syntaxErrorMessage = " Allowed value format: " + format;

        String[] modulePackageAndTargetModules = optionValue.split("=", 2);
        if (modulePackageAndTargetModules.length != 2) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }
        String modulePackage = modulePackageAndTargetModules[0];
        String targetModuleNames = modulePackageAndTargetModules[1];

        String[] moduleAndPackage = modulePackage.split("/");
        if (moduleAndPackage.length > 1 + (reads ? 0 : 1)) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }
        String moduleName = moduleAndPackage[0];
        String packageName = moduleAndPackage.length > 1 ? moduleAndPackage[1] : null;

        List<String> targetModuleNamesList = Arrays.asList(targetModuleNames.split(","));
        if (targetModuleNamesList.isEmpty()) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }

        Module module = findModule(moduleName).orElseThrow(() -> {
            return userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, " Specified module '" + moduleName + "' is unknown.");
        });
        List<Module> targetModules;
        if (targetModuleNamesList.contains("ALL-UNNAMED")) {
            targetModules = Collections.emptyList();
        } else {
            targetModules = targetModuleNamesList.stream().map(mn -> {
                return findModule(mn).orElseThrow(() -> {
                    throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, " Specified target-module '" + mn + "' is unknown.");
                });
            }).collect(Collectors.toList());
        }
        return new AddExportsAndOpensAndReadsFormatValue(module, packageName, targetModules);
    }

    private static UserError.UserException userErrorAddExportsAndOpensAndReads(OptionKey<?> option, OptionOrigin origin, String value, String detailMessage) {
        Objects.requireNonNull(detailMessage, "missing detailMessage");
        return UserError.abort("Invalid option %s provided by %s.%s", SubstrateOptionsParser.commandArgument(option, value), origin, detailMessage);
    }

    Class<?> loadClassFromModule(Module module, String className) {
        assert isModuleClassLoader(classLoader, module.getClassLoader()) : "Argument `module` is java.lang.Module from unknown ClassLoader";
        return Class.forName(module, className);
    }

    private static boolean isModuleClassLoader(ClassLoader loader, ClassLoader moduleClassLoader) {
        if (moduleClassLoader == loader) {
            return true;
        } else {
            if (loader == null) {
                return false;
            }
            return isModuleClassLoader(loader.getParent(), moduleClassLoader);
        }
    }

    Optional<String> getMainClassFromModule(Object module) {
        assert module instanceof Module : "Argument `module` is not an instance of java.lang.Module";
        return ((Module) module).getDescriptor().mainClass();
    }

    final Path excludeDirectoriesRoot = Paths.get("/");
    final Set<Path> excludeDirectories = getExcludeDirectories();

    private Set<Path> getExcludeDirectories() {
        return Stream.of("dev", "sys", "proc", "etc", "var", "tmp", "boot", "lost+found")
                        .map(excludeDirectoriesRoot::resolve).collect(Collectors.toUnmodifiableSet());
    }

    private final class LoadClassHandler {

        private final ForkJoinPool executor;
        private final ImageClassLoader imageClassLoader;

        AtomicInteger entriesProcessed;
        volatile String currentlyProcessedEntry;
        boolean initialReport;

        private LoadClassHandler(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
            this.executor = executor;
            this.imageClassLoader = imageClassLoader;

            entriesProcessed = new AtomicInteger(0);
            currentlyProcessedEntry = "Unknown Entry";
            initialReport = true;
        }

        private void run() {
            ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            try {
                scheduledExecutor.scheduleAtFixedRate(() -> {
                    if (initialReport) {
                        initialReport = false;
                        System.out.println("Loading classes is taking a long time. This can be caused by class- or module-path entries that point to large directory structures.");
                    }
                    System.out.println("Total processed entries: " + entriesProcessed.get() + ", current entry: " + currentlyProcessedEntry);
                }, 5, 1, TimeUnit.MINUTES);

                List<String> requiresInit = Arrays.asList(
                                "jdk.internal.vm.ci", "jdk.internal.vm.compiler", "com.oracle.graal.graal_enterprise",
                                "org.graalvm.sdk", "org.graalvm.truffle");

                for (ModuleReference moduleReference : upgradeAndSystemModuleFinder.findAll()) {
                    if (requiresInit.contains(moduleReference.descriptor().name())) {
                        initModule(moduleReference);
                    }
                }
                for (ModuleReference moduleReference : modulepathModuleFinder.findAll()) {
                    initModule(moduleReference);
                }

                classpath().parallelStream().forEach(this::loadClassesFromPath);
            } finally {
                scheduledExecutor.shutdown();
            }
        }

        private void initModule(ModuleReference moduleReference) {
            String moduleReferenceLocation = moduleReference.location().map(URI::toString).orElse("UnknownModuleReferenceLocation");
            currentlyProcessedEntry = moduleReferenceLocation;
            Optional<Module> optionalModule = findModule(moduleReference.descriptor().name());
            if (optionalModule.isEmpty()) {
                return;
            }
            try (ModuleReader moduleReader = moduleReference.open()) {
                Module module = optionalModule.get();
                moduleReader.list().forEach(moduleResource -> {
                    if (moduleResource.endsWith(CLASS_EXTENSION)) {
                        currentlyProcessedEntry = moduleReferenceLocation + "/" + moduleResource;
                        executor.execute(() -> handleClassFileName(moduleReference.location().orElseThrow(), module, moduleResource, '/'));
                    }
                    entriesProcessed.incrementAndGet();
                });
            } catch (IOException e) {
                throw new RuntimeException("Unable get list of resources in module" + moduleReference.descriptor().name(), e);
            }
        }

        private void loadClassesFromPath(Path path) {
            if (ClasspathUtils.isJar(path)) {
                try {
                    URI container = path.toAbsolutePath().toUri();
                    URI jarURI = new URI("jar:" + container);
                    FileSystem probeJarFileSystem;
                    try {
                        probeJarFileSystem = FileSystems.newFileSystem(jarURI, Collections.emptyMap());
                    } catch (UnsupportedOperationException e) {
                        /* Silently ignore invalid jar-files on image-classpath */
                        probeJarFileSystem = null;
                    }
                    if (probeJarFileSystem != null) {
                        try (FileSystem jarFileSystem = probeJarFileSystem) {
                            loadClassesFromPath(container, jarFileSystem.getPath("/"), null, Collections.emptySet());
                        }
                    }
                } catch (ClosedByInterruptException ignored) {
                    throw new InterruptImageBuilding();
                } catch (IOException | URISyntaxException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                URI container = path.toUri();
                loadClassesFromPath(container, path, excludeDirectoriesRoot, excludeDirectories);
            }
        }

        private static final String CLASS_EXTENSION = ".class";

        private void loadClassesFromPath(URI container, Path root, Path excludeRoot, Set<Path> excludes) {
            boolean useFilter = root.equals(excludeRoot);
            if (useFilter) {
                String excludesStr = excludes.stream().map(Path::toString).collect(Collectors.joining(", "));
                System.err.println("Warning: Using directory " + excludeRoot + " on classpath is discouraged." +
                                " Reading classes/resources from directories " + excludesStr + " will be suppressed.");
            }
            FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                private final char fileSystemSeparatorChar = root.getFileSystem().getSeparator().charAt(0);

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    currentlyProcessedEntry = dir.toUri().toString();
                    if (useFilter && excludes.contains(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    assert !excludes.contains(file.getParent()) : "Visiting file '" + file + "' with excluded parent directory";
                    String fileName = root.relativize(file).toString();
                    if (fileName.endsWith(CLASS_EXTENSION)) {
                        currentlyProcessedEntry = file.toUri().toString();
                        executor.execute(() -> handleClassFileName(container, null, fileName, fileSystemSeparatorChar));
                    }
                    entriesProcessed.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    /* Silently ignore inaccessible files or directories. */
                    return FileVisitResult.CONTINUE;
                }
            };

            try {
                Files.walkFileTree(root, visitor);
            } catch (IOException ex) {
                throw shouldNotReachHere(ex);
            }
        }

        /**
         * Take a file name from a possibly-multi-versioned jar file and remove the versioning
         * information. See https://docs.oracle.com/javase/9/docs/api/java/util/jar/JarFile.html for
         * the specification of the versioning strings.
         *
         * Then, depend on the JDK class loading mechanism to prefer the appropriately-versioned
         * class when the class is loaded. The same class name be loaded multiple times, but each
         * request will return the same appropriately-versioned class. If a higher-versioned class
         * is not available in a lower-versioned JDK, a ClassNotFoundException will be thrown, which
         * will be handled appropriately.
         */
        private String strippedClassFileName(String fileName) {
            final String versionedPrefix = "META-INF/versions/";
            final String versionedSuffix = "/";
            String result = fileName;
            if (fileName.startsWith(versionedPrefix)) {
                final int versionedSuffixIndex = fileName.indexOf(versionedSuffix, versionedPrefix.length());
                if (versionedSuffixIndex >= 0) {
                    result = fileName.substring(versionedSuffixIndex + versionedSuffix.length());
                }
            }
            return result.substring(0, result.length() - CLASS_EXTENSION.length());
        }

        private void handleClassFileName(URI container, Module module, String fileName, char fileSystemSeparatorChar) {
            String strippedClassFileName = strippedClassFileName(fileName);
            if (strippedClassFileName.equals("module-info")) {
                return;
            }

            String className = strippedClassFileName.replace(fileSystemSeparatorChar, '.');
            synchronized (classes) {
                EconomicSet<String> classNames = classes.get(container);
                if (classNames == null) {
                    classNames = EconomicSet.create();
                    classes.put(container, classNames);
                }
                classNames.add(className);
            }
            int packageSep = className.lastIndexOf('.');
            String packageName = packageSep > 0 ? className.substring(0, packageSep) : "";
            synchronized (packages) {
                EconomicSet<String> packageNames = packages.get(container);
                if (packageNames == null) {
                    packageNames = EconomicSet.create();
                    packages.put(container, packageNames);
                }
                packageNames.add(packageName);
            }

            Class<?> clazz = null;
            try {
                clazz = imageClassLoader.forName(className, module);
            } catch (AssertionError error) {
                VMError.shouldNotReachHere(error);
            } catch (Throwable t) {
                ImageClassLoader.handleClassLoadingError(t);
            }
            if (clazz != null) {
                imageClassLoader.handleClass(clazz);
            }
        }
    }
}
