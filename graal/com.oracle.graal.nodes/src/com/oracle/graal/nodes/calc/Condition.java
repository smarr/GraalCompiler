/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.max.cri.util.*;

/**
 * Condition codes used in conditionals.
 */
public enum Condition {
    /**
     * Equal.
     */
    EQ("=="),

    /**
     * Not equal.
     */
    NE("!="),

    /**
     * Signed less than.
     */
    LT("<"),

    /**
     * Signed less than or equal.
     */
    LE("<="),

    /**
     * Signed greater than.
     */
    GT(">"),

    /**
     * Signed greater than or equal.
     */
    GE(">="),

    /**
     * Unsigned greater than or equal ("above than or equal").
     */
    AE("|>=|"),

    /**
     * Unsigned less than or equal ("below than or equal").
     */
    BE("|<=|"),

    /**
     * Unsigned greater than ("above than").
     */
    AT("|>|"),

    /**
     * Unsigned less than ("below than").
     */
    BT("|<|");

    public final String operator;

    private Condition(String operator) {
        this.operator = operator;
    }

    public boolean check(int left, int right) {
        switch (this) {
            case EQ: return left == right;
            case NE: return left != right;
            case LT: return left < right;
            case LE: return left <= right;
            case GT: return left > right;
            case GE: return left >= right;
            case AE: return UnsignedMath.aboveOrEqual(left, right);
            case BE: return UnsignedMath.belowOrEqual(left, right);
            case AT: return UnsignedMath.aboveThan(left, right);
            case BT: return UnsignedMath.belowThan(left, right);
        }
        throw new IllegalArgumentException(this.toString());
    }

    /**
     * Given a condition and its negation, this method returns true for one of the two and false for the other one.
     * This can be used to keep comparisons in a canonical form.
     * @return true if this condition is considered to be the canonical form, false otherwise.
     */
    public boolean isCanonical() {
        switch (this) {
            case EQ: return true;
            case NE: return false;
            case LT: return true;
            case LE: return false;
            case GT: return false;
            case GE: return false;
            case BT: return true;
            case BE: return false;
            case AT: return false;
            case AE: return false;
        }
        throw new IllegalArgumentException(this.toString());
    }

    /**
     * Returns true if the condition needs to be mirrored to get to a canonical condition.
     * The result of the mirroring operation might still need to be negated to achieve a canonical form.
     */
    public boolean canonicalMirror() {
        switch (this) {
            case EQ: return false;
            case NE: return false;
            case LT: return false;
            case LE: return true;
            case GT: return true;
            case GE: return false;
            case BT: return false;
            case BE: return true;
            case AT: return true;
            case AE: return false;
        }
        throw new IllegalArgumentException(this.toString());
    }

    /**
     * Returns true if the condition needs to be negated to get to a canonical condition.
     * The result of the negation might still need to be mirrored to achieve a canonical form.
     */
    public boolean canonicalNegate() {
        switch (this) {
            case EQ: return false;
            case NE: return true;
            case LT: return false;
            case LE: return true;
            case GT: return false;
            case GE: return true;
            case BT: return false;
            case BE: return true;
            case AT: return false;
            case AE: return true;
        }
        throw new IllegalArgumentException(this.toString());
    }

    /**
     * Negate this conditional.
     * @return the condition that represents the negation
     */
    public final Condition negate() {
        switch (this) {
            case EQ: return NE;
            case NE: return EQ;
            case LT: return GE;
            case LE: return GT;
            case GT: return LE;
            case GE: return LT;
            case BT: return AE;
            case BE: return AT;
            case AT: return BE;
            case AE: return BT;
        }
        throw new IllegalArgumentException(this.toString());
    }

    public boolean implies(Condition other) {
        if (other == this) {
            return true;
        }
        switch (this) {
            case EQ: return other == LE || other == GE || other == BE || other == AE;
            case NE: return false;
            case LT: return other == LE || other == NE;
            case LE: return false;
            case GT: return other == GE || other == NE;
            case GE: return false;
            case BT: return other == BE || other == NE;
            case BE: return false;
            case AT: return other == AE || other == NE;
            case AE: return false;
        }
        throw new IllegalArgumentException(this.toString());
    }

    /**
     * Mirror this conditional (i.e. commute "a op b" to "b op' a")
     * @return the condition representing the equivalent commuted operation
     */
    public final Condition mirror() {
        switch (this) {
            case EQ: return EQ;
            case NE: return NE;
            case LT: return GT;
            case LE: return GE;
            case GT: return LT;
            case GE: return LE;
            case BT: return AT;
            case BE: return AE;
            case AT: return BT;
            case AE: return BE;
        }
        throw new IllegalArgumentException();
    }

    /**
     * Returns true if this condition represents an unsigned comparison. EQ and NE are not considered to be unsigned.
     */
    public final boolean isUnsigned() {
        return this == Condition.BT || this == Condition.BE || this == Condition.AT || this == Condition.AE;
    }

    /**
     * Checks if this conditional operation is commutative.
     * @return {@code true} if this operation is commutative
     */
    public final boolean isCommutative() {
        return this == EQ || this == NE;
    }

    /**
     * Attempts to fold a comparison between two constants and return the result.
     * @param lt the constant on the left side of the comparison
     * @param rt the constant on the right side of the comparison
     * @param runtime the RiRuntime (might be needed to compare runtime-specific types)
     * @return {@link Boolean#TRUE} if the comparison is known to be true,
     * {@link Boolean#FALSE} if the comparison is known to be false
     */
    public boolean foldCondition(Constant lt, Constant rt, RiRuntime runtime) {
        assert !lt.kind.isFloatOrDouble() && !rt.kind.isFloatOrDouble();
        return foldCondition(lt, rt, runtime, false);
    }

    /**
     * Attempts to fold a comparison between two constants and return the result.
     * @param lt the constant on the left side of the comparison
     * @param rt the constant on the right side of the comparison
     * @param runtime the RiRuntime (might be needed to compare runtime-specific types)
     * @param unorderedIsTrue true if an undecided float comparison should result in "true"
     * @return true if the comparison is known to be true, false if the comparison is known to be false
     */
    public boolean foldCondition(Constant lt, Constant rt, RiRuntime runtime, boolean unorderedIsTrue) {
        switch (lt.kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Jsr:
            case Int: {
                int x = lt.asInt();
                int y = rt.asInt();
                switch (this) {
                    case EQ: return x == y;
                    case NE: return x != y;
                    case LT: return x < y;
                    case LE: return x <= y;
                    case GT: return x > y;
                    case GE: return x >= y;
                    case AE: return UnsignedMath.aboveOrEqual(x, y);
                    case BE: return UnsignedMath.belowOrEqual(x, y);
                    case AT: return UnsignedMath.aboveThan(x, y);
                    case BT: return UnsignedMath.belowThan(x, y);
                    default: throw new GraalInternalError("expected condition: %s", this);
                }
            }
            case Long: {
                long x = lt.asLong();
                long y = rt.asLong();
                switch (this) {
                    case EQ: return x == y;
                    case NE: return x != y;
                    case LT: return x < y;
                    case LE: return x <= y;
                    case GT: return x > y;
                    case GE: return x >= y;
                    case AE: return UnsignedMath.aboveOrEqual(x, y);
                    case BE: return UnsignedMath.belowOrEqual(x, y);
                    case AT: return UnsignedMath.aboveThan(x, y);
                    case BT: return UnsignedMath.belowThan(x, y);
                    default: throw new GraalInternalError("expected condition: %s", this);
                }
            }
            case Object: {
                switch (this) {
                    case EQ: return runtime.areConstantObjectsEqual(lt, rt);
                    case NE: return !runtime.areConstantObjectsEqual(lt, rt);
                    default: throw new GraalInternalError("expected condition: %s", this);
                }
            }
            case Float: {
                float x = lt.asFloat();
                float y = rt.asFloat();
                if (Float.isNaN(x) || Float.isNaN(y)) {
                    return unorderedIsTrue;
                }
                switch (this) {
                    case EQ: return x == y;
                    case NE: return x != y;
                    case LT: return x < y;
                    case LE: return x <= y;
                    case GT: return x > y;
                    case GE: return x >= y;
                    default: throw new GraalInternalError("expected condition: %s", this);
                }
            }
            case Double: {
                double x = lt.asDouble();
                double y = rt.asDouble();
                if (Double.isNaN(x) || Double.isNaN(y)) {
                    return unorderedIsTrue;
                }
                switch (this) {
                    case EQ: return x == y;
                    case NE: return x != y;
                    case LT: return x < y;
                    case LE: return x <= y;
                    case GT: return x > y;
                    case GE: return x >= y;
                    default: throw new GraalInternalError("expected condition: %s", this);
                }
            }
            default: throw new GraalInternalError("expected value kind %s while folding condition: %s", lt.kind, this);
        }
    }

    public Condition join(Condition other) {
        if (other == this) {
            return this;
        }
        switch (this) {
            case EQ:
                if (other == LE || other == GE || other == BE || other == AE) {
                    return EQ;
                } else {
                    return null;
                }
            case NE:
                if (other == LT || other == GT || other == BT || other == AT) {
                    return other;
                } else if (other == LE) {
                    return LT;
                } else if (other == GE) {
                    return GT;
                } else if (other == BE) {
                    return BT;
                } else if (other == AE) {
                    return AT;
                } else {
                    return null;
                }
            case LE:
                if (other == GE || other == EQ) {
                    return EQ;
                } else if (other == NE || other == LT) {
                    return LT;
                } else {
                    return null;
                }
            case LT:
                if (other == NE || other == LE) {
                    return LT;
                } else {
                    return null;
                }
            case GE:
                if (other == LE || other == EQ) {
                    return EQ;
                } else if (other == NE || other == GT) {
                    return GT;
                } else {
                    return null;
                }
            case GT:
                if (other == NE || other == GE) {
                    return GT;
                } else {
                    return null;
                }
            case BE:
                if (other == AE || other == EQ) {
                    return EQ;
                } else if (other == NE || other == BT) {
                    return BT;
                } else {
                    return null;
                }
            case BT:
                if (other == NE || other == BE) {
                    return BT;
                } else {
                    return null;
                }
            case AE:
                if (other == BE || other == EQ) {
                    return EQ;
                } else if (other == NE || other == AT) {
                    return AT;
                } else {
                    return null;
                }
            case AT:
                if (other == NE || other == AE) {
                    return AT;
                } else {
                    return null;
                }
        }
        throw new IllegalArgumentException(this.toString());
    }

    public Condition meet(Condition other) {
        if (other == this) {
            return this;
        }
        switch (this) {
            case EQ:
                if (other == LE || other == GE || other == BE || other == AE) {
                    return other;
                } else if (other == LT) {
                    return LE;
                } else if (other == GT) {
                    return GE;
                } else if (other == BT) {
                    return BE;
                } else if (other == AT) {
                    return AE;
                } else {
                    return null;
                }
            case NE:
                if (other == LT || other == GT || other == BT || other == AT) {
                    return NE;
                } else {
                    return null;
                }
            case LE:
                if (other == EQ || other == LT) {
                    return LE;
                } else {
                    return null;
                }
            case LT:
                if (other == EQ || other == LE) {
                    return LE;
                } else if (other == NE || other == GT) {
                    return NE;
                } else {
                    return null;
                }
            case GE:
                if (other == EQ || other == GT) {
                    return GE;
                } else {
                    return null;
                }
            case GT:
                if (other == EQ || other == GE) {
                    return GE;
                } else if (other == NE || other == LT) {
                    return NE;
                } else {
                    return null;
                }
            case BE:
                if (other == EQ || other == BT) {
                    return BE;
                } else {
                    return null;
                }
            case BT:
                if (other == EQ || other == BE) {
                    return BE;
                } else if (other == NE || other == AT) {
                    return NE;
                } else {
                    return null;
                }
            case AE:
                if (other == EQ || other == AT) {
                    return AE;
                } else {
                    return null;
                }
            case AT:
                if (other == EQ || other == AE) {
                    return AE;
                } else if (other == NE || other == BT) {
                    return NE;
                } else {
                    return null;
                }
        }
        throw new IllegalArgumentException(this.toString());
    }
}
