/*
 * Tai-e: A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Tai-e is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package pascal.taie.frontend.soot;

import pascal.taie.ir.DefaultNewIR;
import pascal.taie.ir.NewIR;
import pascal.taie.ir.exp.ArithmeticExp;
import pascal.taie.ir.exp.BinaryExp;
import pascal.taie.ir.exp.BitwiseExp;
import pascal.taie.ir.exp.ClassLiteral;
import pascal.taie.ir.exp.ComparisonExp;
import pascal.taie.ir.exp.DoubleLiteral;
import pascal.taie.ir.exp.FloatLiteral;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.Literal;
import pascal.taie.ir.exp.LongLiteral;
import pascal.taie.ir.exp.NullLiteral;
import pascal.taie.ir.exp.ShiftExp;
import pascal.taie.ir.exp.StringLiteral;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignLiteral;
import pascal.taie.ir.stmt.Binary;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.java.classes.JMethod;
import pascal.taie.java.types.Type;
import soot.Body;
import soot.Local;
import soot.Unit;
import soot.Value;
import soot.jimple.AbstractConstantSwitch;
import soot.jimple.AbstractJimpleValueSwitch;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.AddExpr;
import soot.jimple.AndExpr;
import soot.jimple.AnyNewExpr;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.ClassConstant;
import soot.jimple.CmpExpr;
import soot.jimple.CmpgExpr;
import soot.jimple.CmplExpr;
import soot.jimple.Constant;
import soot.jimple.DivExpr;
import soot.jimple.DoubleConstant;
import soot.jimple.FloatConstant;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.LongConstant;
import soot.jimple.MulExpr;
import soot.jimple.NullConstant;
import soot.jimple.OrExpr;
import soot.jimple.RemExpr;
import soot.jimple.ShlExpr;
import soot.jimple.ShrExpr;
import soot.jimple.StringConstant;
import soot.jimple.SubExpr;
import soot.jimple.UshrExpr;
import soot.jimple.XorExpr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pascal.taie.util.CollectionUtils.freeze;

class MethodIRBuilder {

    private final JMethod method;

    private final Converter converter;

    private VarManager varManager;

    private List<Stmt> stmts;

    MethodIRBuilder(JMethod method, Converter converter) {
        this.method = method;
        this.converter = converter;
    }

    NewIR build() {
        Body body = method.getSootMethod().retrieveActiveBody();
        varManager = new VarManager();
        stmts = new ArrayList<>();
        if (!method.isStatic()) {
            buildThis(body);
        }
        buildParams(body);
        buildStmts(body);
        return new DefaultNewIR(method,
                varManager.getThis(), freeze(varManager.getParams()),
                freeze(varManager.getVars()), freeze(stmts));
    }

    private void buildThis(Body body) {
        varManager.addThis(body.getThisLocal());
    }

    private void buildParams(Body body) {
        body.getParameterLocals().forEach(varManager::addParam);
    }

    private void buildStmts(Body body) {
        StmtBuilder builder = new StmtBuilder();
        body.getUnits().forEach(unit -> unit.apply(builder));
    }

    private class StmtBuilder extends AbstractStmtSwitch {

        /**
         * Current Jimple statement being handled.
         */
        private Unit currentStmt;

        /**
         * Convert Constants to Literals.
         */
        private final AbstractConstantSwitch constantConverter
                = new AbstractConstantSwitch() {

            @Override
            public void caseDoubleConstant(DoubleConstant v) {
                setResult(DoubleLiteral.get(v.value));
            }

            @Override
            public void caseFloatConstant(FloatConstant v) {
                setResult(FloatLiteral.get(v.value));
            }

            @Override
            public void caseIntConstant(IntConstant v) {
                setResult(IntLiteral.get(v.value));
            }

            @Override
            public void caseLongConstant(LongConstant v) {
                setResult(LongLiteral.get(v.value));
            }

            @Override
            public void caseNullConstant(NullConstant v) {
                setResult(NullLiteral.get());
            }

            @Override
            public void caseStringConstant(StringConstant v) {
                setResult(StringLiteral.get(v.value));
            }

            @Override
            public void caseClassConstant(ClassConstant v) {
                setResult(ClassLiteral.get(getType(v.value)));
            }

            @Override
            public void defaultCase(Object v) {
                throw new SootFrontendException(
                        "Cannot convert constant: " + v);
            }
        };

        /**
         * Extract BinaryExp.Op from BinopExpr.
         */
        private final AbstractJimpleValueSwitch binaryOpExtractor
                = new AbstractJimpleValueSwitch() {

            // ---------- Arithmetic expression ----------
            @Override
            public void caseAddExpr(AddExpr v) {
                setResult(ArithmeticExp.Op.ADD);
            }

            @Override
            public void caseSubExpr(SubExpr v) {
                setResult(ArithmeticExp.Op.SUB);
            }

            @Override
            public void caseMulExpr(MulExpr v) {
                setResult(ArithmeticExp.Op.MUL);
            }

            @Override
            public void caseDivExpr(DivExpr v) {
                setResult(ArithmeticExp.Op.DIV);
            }

            @Override
            public void caseRemExpr(RemExpr v) {
                setResult(ArithmeticExp.Op.REM);
            }

            // ---------- Bitwise expression ----------
            @Override
            public void caseAndExpr(AndExpr v) {
                setResult(BitwiseExp.Op.AND);
            }

            @Override
            public void caseOrExpr(OrExpr v) {
                setResult(BitwiseExp.Op.OR);
            }

            @Override
            public void caseXorExpr(XorExpr v) {
                setResult(BitwiseExp.Op.XOR);
            }

            // ---------- Comparison expression ----------
            @Override
            public void caseCmpExpr(CmpExpr v) {
                setResult(ComparisonExp.Op.CMP);
            }

            @Override
            public void caseCmplExpr(CmplExpr v) {
                setResult(ComparisonExp.Op.CMPL);
            }

            @Override
            public void caseCmpgExpr(CmpgExpr v) {
                setResult(ComparisonExp.Op.CMPG);
            }

            // ---------- Shift expression ----------
            @Override
            public void caseShlExpr(ShlExpr v) {
                setResult(ShiftExp.Op.SHL);
            }

            @Override
            public void caseShrExpr(ShrExpr v) {
                setResult(ShiftExp.Op.SHR);
            }

            @Override
            public void caseUshrExpr(UshrExpr v) {
                setResult(ShiftExp.Op.USHR);
            }

            @Override
            public void defaultCase(Object v) {
                throw new SootFrontendException(
                        "Unexpected binary expression: " + v);
            }
        };

        private void buildNew(Value lhs, AnyNewExpr newExpr) {

        }

        private void buildCopy(Local lhs, Local rhs) {
            addStmt(new Copy(varManager.getVar(lhs), varManager.getVar(rhs)));
        }

        private void buildAssignLiteral(Local lhs, Constant constant) {
            constant.apply(constantConverter);
            addStmt(new AssignLiteral(varManager.getVar(lhs),
                    (Literal) constantConverter.getResult()));
        }

        private void buildBinary(Local lhs, BinopExpr rhs) {
            rhs.apply(binaryOpExtractor);
            BinaryExp.Op op = (BinaryExp.Op) binaryOpExtractor.getResult();
            BinaryExp binaryExp;
            Var v1 = getLocalOrConstant(rhs.getOp1());
            Var v2 = getLocalOrConstant(rhs.getOp2());
            if (op instanceof ArithmeticExp.Op) {
                binaryExp = new ArithmeticExp((ArithmeticExp.Op) op, v1, v2);
            } else if (op instanceof ComparisonExp.Op) {
                binaryExp = new ComparisonExp((ComparisonExp.Op) op, v1, v2);
            } else if (op instanceof BitwiseExp.Op) {
                binaryExp = new BitwiseExp((BitwiseExp.Op) op, v1, v2);
            } else if (op instanceof ShiftExp.Op) {
                binaryExp = new ShiftExp((ShiftExp.Op) op, v1, v2);
            } else {
                throw new SootFrontendException("Cannot handle BinopExpr: " + rhs);
            }
            addStmt(new Binary(varManager.getVar(lhs), binaryExp));
        }

        private void buildInvoke(Local lhs, InvokeExpr invokeExpr) {

        }

        private Var getLocalOrConstant(Value value) {
            if (value instanceof Local) {
                return varManager.getVar((Local) value);
            } else if (value instanceof Constant) {
                value.apply(constantConverter);
                Literal rvalue = (Literal) constantConverter.getResult();
                Var lvalue = varManager.newConstantVar(rvalue);
                addStmt(new AssignLiteral(lvalue, rvalue));
                return lvalue;
            }
            throw new SootFrontendException("Expected Local or Constant, given " + value);
        }

        private Type getType(String typeName) {
            throw new UnsupportedOperationException();
        }

        private void addStmt(Stmt stmt) {
            stmts.add(stmt);
        }

        @Override
        public void caseAssignStmt(AssignStmt stmt) {
            currentStmt = stmt;
            Value lhs = stmt.getLeftOp();
            Value rhs = stmt.getRightOp();
            if (rhs instanceof InvokeExpr) {
                buildInvoke((Local) lhs, (InvokeExpr) rhs);
                return;
            }
            if (lhs instanceof Local) {
                if (rhs instanceof Constant) {
                    buildAssignLiteral((Local) lhs, (Constant) rhs);
                } else if (rhs instanceof Local) {
                    buildCopy((Local) lhs, (Local) rhs);
                } else if (rhs instanceof BinopExpr) {
                    buildBinary((Local) lhs, (BinopExpr) rhs);
                }
            }
        }

        @Override
        public void defaultCase(Object obj) {
            System.out.println("Unhandled Stmt: " + obj);
        }
    }

    /**
     * Shortcut for obtaining and converting the type of soot.Value.
     */
    private Type getType(Value value) {
        return converter.convertType(value.getType());
    }

    private class VarManager {

        private final static String THIS = "#this";

        private final static String PARAM = "#param";

        private final static String STRING_CONSTANT = "#stringconstant";

        private final static String CLASS_CONSTANT = "#classconstant";

        private final static String NULL_CONSTANT = "#nullconstant";

        private final Map<Local, Var> varMap = new LinkedHashMap<>();

        private final List<Var> vars = new ArrayList<>();

        private Var thisVar;

        private final List<Var> params = new ArrayList<>();

        /**
         * Counter for temporary constant variables.
         */
        private int counter = 0;

        private void addThis(Local thisLocal) {
            thisVar = newVar(THIS, getType(thisLocal));
            varMap.put(thisLocal, thisVar);
        }

        private void addNativeThis(Type thisType) {
            thisVar = newVar(THIS, thisType);
        }

        private void addParam(Local paramLocal) {
            Var param = newVar(paramLocal.getName(), getType(paramLocal));
            params.add(param);
        }

        private void addNativeParam(Type paramType) {
            Var param = newVar(PARAM + params.size(), paramType);
            params.add(param);
        }

        private Var getVar(Local local) {
            return varMap.computeIfAbsent(local, l ->
                    newVar(l.getName(), getType(l)));
        }

        /**
         * @return a new temporary variable that holds given literal value.
         */
        private Var newConstantVar(Literal literal) {
            String varName;
            if (literal instanceof StringLiteral) {
                varName = STRING_CONSTANT + counter++;
            } else if (literal instanceof ClassLiteral) {
                varName = CLASS_CONSTANT + counter++;
            } else if (literal instanceof NullLiteral) {
                varName = NULL_CONSTANT + counter++;
            } else {
                varName = "#" + literal.getType().getName() +
                        "constant" + counter++;
            }
            return newVar(varName, literal.getType());
        }

        private Var newVar(String name, Type type) {
            Var var = new Var(name, type);
            vars.add(var);
            return var;
        }

        private Var getThis() {
            return thisVar;
        }

        public List<Var> getParams() {
            return params;
        }

        private List<Var> getVars() {
            return vars;
        }
    }
}
