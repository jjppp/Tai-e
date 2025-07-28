/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.ProgramAnalysis;
import pascal.taie.analysis.pta.PointerAnalysis;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.StaticField;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.ClassNames;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.graph.SimpleGraph;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class implements the context-sensitive thread-escape analysis from
 * Mayur Naik, Alex Aiken, and John Whaley. PLDI 2006.
 * Effective static race detection for Java.
 */
public class ThreadEscapeAnalysis extends ProgramAnalysis<Set<Obj>> {

    public static final String ID = "thread-escape";

    private static final Logger logger = LogManager.getLogger(ThreadEscapeAnalysis.class);

    private JClass javaLangThread;

    private PointerAnalysisResult pta;

    private ObjGraph objGraph;

    public ThreadEscapeAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public Set<Obj> analyze() {
        ClassHierarchy hierarchy = World.get().getClassHierarchy();
        javaLangThread = hierarchy.getJREClass(ClassNames.THREAD);
        if (javaLangThread == null) {
            logger.warn("Class {} not found", ClassNames.THREAD);
        }
        pta = World.get().getResult(PointerAnalysis.ID);
        objGraph = new ObjGraph(pta);
        Set<CSObj> roots = computeRoots();
        Set<Obj> mayEscapeObjs = reachableFrom(roots).stream()
                .map(CSObj::getObject)
                .collect(Collectors.toUnmodifiableSet());
        logger.info("#{}: found {} out of {} objects",
                ID, mayEscapeObjs.size(), pta.getObjects().size());
        return mayEscapeObjs;
    }

    private Set<CSObj> computeRoots() {
        Set<CSObj> roots = Sets.newSet();
        // 1. If an object is reachable from static fields, it escapes
        pta.getStaticFields().stream()
                .map(StaticField::getObjects)
                .forEach(roots::addAll);
        // 2. If an object is reachable from the `this` var of a spawning thread, it escapes
        pta.getCSVars().stream()
                .filter(v -> isThreadConstructorThis(v.getVar()))
                .map(CSVar::getObjects)
                .forEach(roots::addAll);
        return roots;
    }

    private Set<CSObj> reachableFrom(Set<CSObj> roots) {
        // 3. If an object is reachable from other escaping objects via field access, it escapes
        Set<CSObj> visited = Sets.newSet();
        Queue<CSObj> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            CSObj obj = queue.poll();
            if (visited.add(obj)) {
                objGraph.getSuccsOf(obj).stream()
                        .filter(Predicate.not(visited::contains))
                        .forEach(queue::add);
            }
        }
        return visited;
    }

    private boolean isThreadConstructorThis(Var var) {
        JMethod method = var.getMethod();
        return method.getDeclaringClass() == javaLangThread
                && method.isConstructor()
                && method.getIR().getThis() == var;
    }

    /**
     * This class provides an abstract
     */
    private static class ObjGraph extends SimpleGraph<CSObj> {
        ObjGraph(PointerAnalysisResult pta) {
            pta.getCSObjects().forEach(this::addNode);
            pta.getInstanceFields().forEach(instanceField -> {
                CSObj baseObj = instanceField.getBase();
                instanceField.objects().forEach(toObj -> addEdge(baseObj, toObj));
            });
        }
    }
}
