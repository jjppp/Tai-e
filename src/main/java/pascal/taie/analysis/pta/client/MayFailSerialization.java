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
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.graph.FieldPointsToGraph;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.language.classes.ClassNames;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.graph.MergedNode;
import pascal.taie.util.graph.MergedSCCGraph;
import pascal.taie.util.graph.TopologicalSorter;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class MayFailSerialization extends ProgramAnalysis<Set<Obj>> {

    private static final Logger logger = LogManager.getLogger(MayFailSerialization.class);

    public static final String ID = "may-fail-serialization";

    private final TypeSystem typeSystem;

    private final ClassType serializable;

    public MayFailSerialization(AnalysisConfig config) {
        super(config);
        typeSystem = World.get().getTypeSystem();
        serializable = typeSystem.getClassType(ClassNames.SERIALIZABLE);
    }

    @Override
    public Set<Obj> analyze() {
        PointerAnalysisResult ptaResult = World.get().getResult(PointerAnalysis.ID);
        MergedSCCGraph<CSObj> mergedSCCGraph = new MergedSCCGraph<>(new FieldPointsToGraph(ptaResult));
        TopologicalSorter<MergedNode<CSObj>> sorter = new TopologicalSorter<>(mergedSCCGraph);
        Set<MergedNode<CSObj>> marked = Sets.newSet();
        for (MergedNode<CSObj> mergedNode : sorter.get()) {
            if (mergedSCCGraph.getSuccsOf(mergedNode).stream().anyMatch(marked::contains)) {
                marked.add(mergedNode);
            }
            if (mergedNode.getNodes().stream().anyMatch(obj ->
                    !typeSystem.isSubtype(serializable, obj.getObject().getType()))) {
                marked.add(mergedNode);
            }
        }
        Set<Obj> sObjs = mergedSCCGraph.getNodes().stream()
                .filter(marked::contains)
                .map(MergedNode::getNodes).flatMap(Collection::stream)
                .map(CSObj::getObject)
                .filter(obj -> typeSystem.isSubtype(serializable, obj.getType()))
                .collect(Collectors.toUnmodifiableSet());

        logger.info("#{}: found {} in {} serializable objects",
                ID, sObjs.size(),
                ptaResult.getObjects().stream().filter(this::isSerializable).count());
        logger.info("#{}: found {} in {} serializable objects (app)",
                ID, sObjs.stream().filter(this::isApp).distinct().count(),
                ptaResult.getObjects().stream().filter(this::isSerializable).filter(this::isApp).distinct().count());

        sObjs.forEach(obj -> logger.info("{}", obj));
        return sObjs;
    }

    private boolean isSerializable(Obj obj) {
        return typeSystem.isSubtype(serializable, obj.getType());
    }

    /**
     * Filters objects based on analysis scope.
     * If onlyApp is true, only considers objects from application methods.
     */
    private boolean isApp(Obj obj) {
        // TODO: this method might not be well-defined for MergedObjs
        return obj.getContainerMethod().isPresent()
                && obj.getContainerMethod().get().isApplication();
    }
}
