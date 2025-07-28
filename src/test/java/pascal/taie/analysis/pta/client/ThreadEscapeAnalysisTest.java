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

import org.junit.jupiter.api.Test;
import pascal.taie.Main;
import pascal.taie.World;
import pascal.taie.analysis.pta.PointerAnalysis;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.toolkit.PointerAnalysisResultEx;
import pascal.taie.analysis.pta.toolkit.PointerAnalysisResultExImpl;
import pascal.taie.language.classes.JMethod;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreadEscapeAnalysisTest {

    private static final String CLASS_PATH = "src/test/resources/pta/client/threadescape";

    @Test
    void testBasic() {
        Main.main("-m", "ThreadEscape",
                "-cp", CLASS_PATH,
                "-pp",
                "-a", ThreadEscapeAnalysis.ID);
        Set<Obj> threadEscapeObjs = World.get().getResult(ThreadEscapeAnalysis.ID);
        PointerAnalysisResult pta = World.get().getResult(PointerAnalysis.ID);
        PointerAnalysisResultEx ptaEx = new PointerAnalysisResultExImpl(pta, true);
        JMethod main = World.get().getMainMethod();
        assertEquals(6, ptaEx.getObjectsAllocatedIn(main).stream()
                .filter(threadEscapeObjs::contains)
                .count());
    }
}
