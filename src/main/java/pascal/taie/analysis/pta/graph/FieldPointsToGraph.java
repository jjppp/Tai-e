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

package pascal.taie.analysis.pta.graph;

import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.Modifier;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.ReferenceType;
import pascal.taie.language.type.Type;
import pascal.taie.util.graph.SimpleGraph;

import java.util.Set;

public class FieldPointsToGraph extends SimpleGraph<CSObj> {

    public FieldPointsToGraph(PointerAnalysisResult pta) {
        // build field points-to graph by examining points-to results
        // and scanning all the fields and array indices
        Field.Factory factory = new Field.Factory();
        pta.getInstanceFields().stream()
                .filter(instanceField -> isConcerned(instanceField.getType()))
                .forEach(instanceField -> {
                    CSObj baseObj = instanceField.getBase();
                    JField jField = instanceField.getField();
                    // Handle non-transient fields only
                    if (!jField.getModifiers().contains(Modifier.TRANSIENT)) {
                        PointsToSet pts = instanceField.getPointsToSet();
                        if (pts != null) {
                            addFieldPointsTo(baseObj, factory.get(jField), pts.getObjects());
                        }
                    }
                });
        pta.getArrayIndexes().stream()
                .filter(arrayIndex -> isConcerned(arrayIndex.getType()))
                .forEach(arrayIndex -> {
                    CSObj array = arrayIndex.getArray();
                    PointsToSet pts = arrayIndex.getPointsToSet();
                    // Handle non-transient fields only
                    if (pts != null) {
                        addFieldPointsTo(array, factory.getArrayIndex(), pts.getObjects());
                    }
                });
    }

    private static boolean isConcerned(Type type) {
        return type instanceof ReferenceType && !(type instanceof NullType);
    }

    private void addFieldPointsTo(CSObj baseObj, Field field, Set<CSObj> pts) {
        pts.forEach(toObj -> addEdge(baseObj, toObj));
    }
}
