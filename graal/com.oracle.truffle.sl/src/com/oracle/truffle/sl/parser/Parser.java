/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

 // The content of this file is automatically generated. DO NOT EDIT.

package com.oracle.truffle.sl.parser;
import java.util.*;

import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.nodes.*;

// Checkstyle: stop
// @formatter:off
public class Parser {
	public static final int _EOF = 0;
	public static final int _identifier = 1;
	public static final int _stringLiteral = 2;
	public static final int _numericLiteral = 3;
	public static final int maxT = 28;

    static final boolean T = true;
    static final boolean x = false;
    static final int minErrDist = 2;

    public Token t; // last recognized token
    public Token la; // lookahead token
    int errDist = minErrDist;

    public final Scanner scanner;
    public final Errors errors;
    private final NodeFactory factory;
    
    public Parser(Scanner scanner, NodeFactory factory) {
        this.scanner = scanner;
        this.factory = factory;
        errors = new Errors();
    }

    void SynErr(int n) {
        if (errDist >= minErrDist)
            errors.SynErr(la.line, la.col, n);
        errDist = 0;
    }

    public void SemErr(String msg) {
        if (errDist >= minErrDist)
            errors.SemErr(t.line, t.col, msg);
        errDist = 0;
    }

    void Get() {
        for (;;) {
            t = la;
            la = scanner.Scan();
            if (la.kind <= maxT) {
                ++errDist;
                break;
            }

            la = t;
        }
    }

    void Expect(int n) {
        if (la.kind == n)
            Get();
        else {
            SynErr(n);
        }
    }

    boolean StartOf(int s) {
        return set[s][la.kind];
    }

    void ExpectWeak(int n, int follow) {
        if (la.kind == n)
            Get();
        else {
            SynErr(n);
            while (!StartOf(follow))
                Get();
        }
    }

    boolean WeakSeparator(int n, int syFol, int repFol) {
        int kind = la.kind;
        if (kind == n) {
            Get();
            return true;
        } else if (StartOf(repFol))
            return false;
        else {
            SynErr(n);
            while (!(set[syFol][kind] || set[repFol][kind] || set[0][kind])) {
                Get();
                kind = la.kind;
            }
            return StartOf(syFol);
        }
    }

	void SimpleLanguage() {
		Function();
		while (la.kind == 4) {
			Function();
		}
	}

	void Function() {
		Expect(4);
		factory.startFunction(); 
		Expect(1);
		String name = t.val; 
		StatementNode body = Block();
		factory.createFunction(body, name); 
	}

	StatementNode  Block() {
		StatementNode  result;
		List<StatementNode> statements = new ArrayList<>(); 
		Expect(5);
		while (StartOf(1)) {
			StatementNode statement = Statement();
			statements.add(statement); 
		}
		Expect(6);
		result = factory.createBlock(statements); 
		return result;
	}

	StatementNode  Statement() {
		StatementNode  result;
		result = null; 
		if (la.kind == 7) {
			result = WhileStatement();
		} else if (la.kind == 1) {
			result = AssignmentStatement();
		} else if (la.kind == 12) {
			result = OutputStatement();
		} else if (la.kind == 13) {
			result = ReturnStatement();
		} else SynErr(29);
		return result;
	}

	StatementNode  WhileStatement() {
		StatementNode  result;
		Expect(7);
		Expect(8);
		ConditionNode condition = Expression();
		Expect(9);
		StatementNode body = Block();
		result = factory.createWhile(condition, body); 
		return result;
	}

	StatementNode  AssignmentStatement() {
		StatementNode  result;
		Expect(1);
		String name = t.val; 
		Expect(10);
		TypedNode rvalue = Expression();
		Expect(11);
		result = factory.createAssignment(name, rvalue); 
		return result;
	}

	StatementNode  OutputStatement() {
		StatementNode  result;
		List<TypedNode> expressions = new ArrayList<>(); 
		Expect(12);
		while (StartOf(2)) {
			TypedNode value = Expression();
			expressions.add(value); 
		}
		Expect(11);
		result = factory.createPrint(expressions); 
		return result;
	}

	StatementNode  ReturnStatement() {
		StatementNode  result;
		Expect(13);
		TypedNode value = Expression();
		Expect(11);
		result = factory.createReturn(value); 
		return result;
	}

	TypedNode  Expression() {
		TypedNode  result;
		result = ValueExpression();
		if (StartOf(3)) {
			switch (la.kind) {
			case 14: {
				Get();
				break;
			}
			case 15: {
				Get();
				break;
			}
			case 16: {
				Get();
				break;
			}
			case 17: {
				Get();
				break;
			}
			case 18: {
				Get();
				break;
			}
			case 19: {
				Get();
				break;
			}
			}
			String op = t.val; 
			TypedNode right = ValueExpression();
			result = factory.createBinary(op, result, right); 
		}
		return result;
	}

	TypedNode  ValueExpression() {
		TypedNode  result;
		result = Term();
		while (la.kind == 20 || la.kind == 21) {
			if (la.kind == 20) {
				Get();
			} else {
				Get();
			}
			String op = t.val; 
			TypedNode right = Term();
			result = factory.createBinary(op, result, right); 
		}
		return result;
	}

	TypedNode  Term() {
		TypedNode  result;
		result = Factor();
		while (la.kind == 22 || la.kind == 23) {
			if (la.kind == 22) {
				Get();
			} else {
				Get();
			}
			String op = t.val; 
			TypedNode right = Factor();
			result = factory.createBinary(op, result, right); 
		}
		return result;
	}

	TypedNode  Factor() {
		TypedNode  result;
		result = null; 
		switch (la.kind) {
		case 27: {
			result = TimeRef();
			break;
		}
		case 1: {
			result = VariableRef();
			break;
		}
		case 2: {
			result = StringLiteral();
			break;
		}
		case 3: {
			result = NumericLiteral();
			break;
		}
		case 24: {
			result = Ternary();
			break;
		}
		case 8: {
			Get();
			result = Expression();
			Expect(9);
			break;
		}
		default: SynErr(30); break;
		}
		return result;
	}

	TypedNode  TimeRef() {
		TypedNode  result;
		Expect(27);
		result = factory.createTime(); 
		return result;
	}

	TypedNode  VariableRef() {
		TypedNode  result;
		Expect(1);
		result = factory.createLocal(t.val); 
		return result;
	}

	TypedNode  StringLiteral() {
		TypedNode  result;
		Expect(2);
		result = factory.createStringLiteral(t.val.substring(1, t.val.length() - 1)); 
		return result;
	}

	TypedNode  NumericLiteral() {
		TypedNode  result;
		Expect(3);
		result = factory.createNumericLiteral(t.val); 
		return result;
	}

	TypedNode  Ternary() {
		TypedNode  result;
		TypedNode condition, thenPart, elsePart; 
		Expect(24);
		condition = Expression();
		Expect(25);
		thenPart = Expression();
		Expect(26);
		elsePart = Expression();
		result = factory.createTernary(condition, thenPart, elsePart); 
		return result;
	}



    public void Parse() {
        la = new Token();
        la.val = "";
        Get();
		SimpleLanguage();
		Expect(0);

    }

    private static final boolean[][] set = {
		{T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
		{x,T,x,x, x,x,x,T, x,x,x,x, T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
		{x,T,T,T, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,x,x,T, x,x},
		{x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, x,x,x,x, x,x,x,x, x,x}

    };

    public String ParseErrors() {
        java.io.PrintStream oldStream = System.out;

        java.io.OutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream newStream = new java.io.PrintStream(out);

        errors.errorStream = newStream;

        Parse();

        String errorStream = out.toString();
        errors.errorStream = oldStream;

        return errorStream;

    }
} // end Parser

class Errors {

    public int count = 0; // number of errors detected
    public java.io.PrintStream errorStream = System.out; // error messages go to this stream
    public String errMsgFormat = "-- line {0} col {1}: {2}"; // 0=line, 1=column, 2=text

    protected void printMsg(int line, int column, String msg) {
        StringBuffer b = new StringBuffer(errMsgFormat);
        int pos = b.indexOf("{0}");
        if (pos >= 0) {
            b.delete(pos, pos + 3);
            b.insert(pos, line);
        }
        pos = b.indexOf("{1}");
        if (pos >= 0) {
            b.delete(pos, pos + 3);
            b.insert(pos, column);
        }
        pos = b.indexOf("{2}");
        if (pos >= 0)
            b.replace(pos, pos + 3, msg);
        errorStream.println(b.toString());
    }

    public void SynErr(int line, int col, int n) {
        String s;
        switch (n) {
			case 0: s = "EOF expected"; break;
			case 1: s = "identifier expected"; break;
			case 2: s = "stringLiteral expected"; break;
			case 3: s = "numericLiteral expected"; break;
			case 4: s = "\"function\" expected"; break;
			case 5: s = "\"{\" expected"; break;
			case 6: s = "\"}\" expected"; break;
			case 7: s = "\"while\" expected"; break;
			case 8: s = "\"(\" expected"; break;
			case 9: s = "\")\" expected"; break;
			case 10: s = "\"=\" expected"; break;
			case 11: s = "\";\" expected"; break;
			case 12: s = "\"print\" expected"; break;
			case 13: s = "\"return\" expected"; break;
			case 14: s = "\"<\" expected"; break;
			case 15: s = "\">\" expected"; break;
			case 16: s = "\"<=\" expected"; break;
			case 17: s = "\">=\" expected"; break;
			case 18: s = "\"==\" expected"; break;
			case 19: s = "\"!=\" expected"; break;
			case 20: s = "\"+\" expected"; break;
			case 21: s = "\"-\" expected"; break;
			case 22: s = "\"*\" expected"; break;
			case 23: s = "\"/\" expected"; break;
			case 24: s = "\"#\" expected"; break;
			case 25: s = "\"?\" expected"; break;
			case 26: s = "\":\" expected"; break;
			case 27: s = "\"time\" expected"; break;
			case 28: s = "??? expected"; break;
			case 29: s = "invalid Statement"; break;
			case 30: s = "invalid Factor"; break;
            default:
                s = "error " + n;
                break;
        }
        printMsg(line, col, s);
        count++;
    }

    public void SemErr(int line, int col, String s) {
        printMsg(line, col, s);
        count++;
    }

    public void SemErr(String s) {
        errorStream.println(s);
        count++;
    }

    public void Warning(int line, int col, String s) {
        printMsg(line, col, s);
    }

    public void Warning(String s) {
        errorStream.println(s);
    }
} // Errors

class FatalError extends RuntimeException {

    public static final long serialVersionUID = 1L;

    public FatalError(String s) {
        super(s);
    }
}
