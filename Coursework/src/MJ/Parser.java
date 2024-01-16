/*  MicroJava Parser (HM 06-12-28)
    ================
*/
package MJ;

import java.util.*;

import MJ.CodeGen.Code;
import MJ.SymTab.*;
import MJ.CodeGen.*;

public class Parser {
	private static final int  // token codes
		none      = 0,
		ident     = 1,
		number    = 2,
		charCon   = 3,
		plus      = 4,
		minus     = 5,
		times     = 6,
		slash     = 7,
		rem       = 8,
		eql       = 9,
		neq       = 10,
		lss       = 11,
		leq       = 12,
		gtr       = 13,
		geq       = 14,
		assign    = 15,
		semicolon = 16,
		comma     = 17,
		period    = 18,
		lpar      = 19,
		rpar      = 20,
		lbrack    = 21,
		rbrack    = 22,
		lbrace    = 23,
		rbrace    = 24,
		class_    = 25,
		else_     = 26,
		final_    = 27,
		if_       = 28,
		new_      = 29,
		print_    = 30,
		program_  = 31,
		read_     = 32,
		return_   = 33,
		void_     = 34,
		while_    = 35,
		eof       = 36;
	private static final String[] name = { // token names for error messages
		"none", "identifier", "number", "char constant", "+", "-", "*", "/", "%",
		"==", "!=", "<", "<=", ">", ">=", "=", ";", ",", ".", "(", ")",
		"[", "]", "{", "}", "class", "else", "final", "if", "new", "print",
		"program", "read", "return", "void", "while", "eof"
		};

	private static Token t;			// current token (recently recognized)
	private static Token la;		// lookahead token
	private static int sym;			// always contains la.kind
	public  static int errors;  // error counter
	private static int errDist;	// no. of correctly recognized tokens since last error

	private static BitSet exprStart, statStart, statSeqFollow, statSync, declStart, declFollow, firststate;

	private static Obj curMethod;

	//------------------- auxiliary methods ----------------------
	private static void scan() {
		t = la;
		la = Scanner.next();
		sym = la.kind;
		errDist++;

		System.out.print("line " + la.line + ", col " + la.col + ": " + name[sym]);
		if (sym == ident) System.out.print(" (" + la.string + ")");
		if (sym == number || sym == charCon) System.out.print(" (" + la.val + ")");
		System.out.println();
	}

	private static void check(int expected) {
		if (sym == expected) scan();
		else error(name[expected] + " expected");
	}

	public static void error(String msg) { // syntactic error at token la
		if (errDist >= 3) {
			System.out.println("-- line " + la.line + " col " + la.col + ": " + msg);
			errors++;
		}
		errDist = 0;
	}

	//-------------- parsing methods (in alphabetical order) -----------------

	// Program = "program" ident {ConstDecl | ClassDecl | VarDecl} '{' {MethodDecl} '}'.
	private static void Program() {
		check(program_);
		check(ident);
		Tab.openScope();
		Tab.insert(Obj.Prog, t.string, Tab.noType);
		for (;;) { // Using infinite loops
			if (declStart.get(sym)) { // Check if it is the beginning of a statement
				if (sym == final_) {
					ConstDecl();
				} else if (sym == class_) {
					ClassDecl();
				} else if (sym == ident) {
					VarDecl();
				}
			} else if (sym == lbrace || sym == eof) {
				break; // If it is '{' or the end of the file, exit the loop
			} else {
				error("Invalid declaration");
				// After synchronization, the loop will continue, checking for the next token
				do scan();
				while (sym != final_ && sym != ident && sym != class_ && sym != lbrace && sym != eof);  // sync point
				errDist = 0;
			}
		}
		check(lbrace);
		while (sym == void_ || sym == ident)MethodDecl();
		check(rbrace);
		Tab.dumpScope(Tab.curScope.locals);
		Code.dataSize = Tab.curScope.nVars; //Set number of vars
		Tab.closeScope();
	}

	// Parsing methods for all productions
	//ConstDecl = "final" Type ident "=" (number | charConst) ";".
	private static void ConstDecl() {
		check(final_);
		Struct type = Type();
		check(ident);
		Obj ob = Tab.insert(Obj.Con, t.string, type); // insert into symtable
		check(assign);
		if (sym == number){
			scan();
			if (ob.type == Tab.intType) ob.val = t.val;
			else error ("Char const expected");
		}else if (sym == charCon){
			scan();
			if (ob.type == Tab.charType) ob.val = t.val;
			else error ("Int const expected");
		}else error ("Invalid ConstDecl. Number or character constant expected");
		check(semicolon);
	}

	//VarDecl = Type ident {"," ident } ";".
	private static void VarDecl() {
		Struct type;
		type = Type();
		check(ident); // variable name
		Tab.insert(Obj.Var, t.string, type);
		while (sym == comma) {
			scan();
			check(ident);
			Tab.insert(Obj.Var, t.string, type);
		}
		check(semicolon);
	}

	//ClassDecl = "class" ident "{" {VarDecl} "}".
	private static void ClassDecl() {
		Struct type = new Struct(Struct.Class);
		Obj ob;
		check(class_);
		check(ident);
		ob = Tab.insert(Obj.Type, t.string, type);
		Tab.openScope();
		check(lbrace);
		for(;;) {
			if (sym == ident) {
				VarDecl();
			}
			else if (sym == rbrace || sym == eof) break;
			else {
				error("error");
				do scan(); while (sym != rbrace && sym != ident && sym != eof);
			}
		}
		ob.type.fields = Tab.curScope.locals;
		ob.type.nFields = Tab.curScope.nVars;
		check(rbrace);
		Tab.closeScope();
	}

	//MethodDecl = (Type | "void") ident "(" [FormPars] ")" {VarDecl} Block.
	private static void MethodDecl() {
		Struct type = Tab.noType;
		String name;
		int n = 0;
		if (type.isRefType())error("methods may only return int or char");
		if (sym == void_) {
			scan();
		} else if (sym == ident) {
			type =Type();
		} else {
            error("Invalid Method Declaration");
        }
		check(ident); // method name
		name = t.string;
		curMethod = Tab.insert(Obj.Meth, name, type);
		check(lpar);
		Tab.openScope();
		if (sym == ident) {
			n = FormPars(); // formal parameter
		}
		curMethod.nPars = n;
		if (name.equals("main")){
			Code.mainPc = Code.pc;
			if (curMethod.type != Tab.noType) error("Main method must be void");
			if (curMethod.nPars != 0) error("Main method must not have parameters");
		}
		check(rpar);
		while (sym == ident) VarDecl(); // Handling local variable declarations
		curMethod.locals = Tab.curScope.locals; //Set methods local variables
		curMethod.adr = Code.pc; //Set methods address for scope
		Code.put(Code.enter);
		Code.put(curMethod.nPars); //Put the number of parameters on the code buffer
		Code.put(Tab.curScope.nVars);
		Block();
		if (curMethod.type == Tab.noType) {
			Code.put(Code.exit);
			Code.put(Code.return_); //Return from this method
		} else {  // end of function reached without a return statement
			Code.put(Code.trap);
			Code.put(1);
		}
		Tab.closeScope();
	}

	//FormPars = Type ident {"," Type ident}.
	private static int FormPars() {
		int parameterNumber = 0;
		Struct type;
		String name;
		type = Type(); //Receive type of parameter
		check(ident);
		name = t.string;
		Tab.insert(Obj.Var, name, type);
		parameterNumber++;
		while(sym == comma) {
			scan();
			type = Type();
			check(ident);
			name = t.string;
			Tab.insert(Obj.Var, name, type);
			parameterNumber++;
		}
		return parameterNumber;
	}

	//Type = ident ["[" "]"].
	private static Struct Type() {
		check(ident);
		Obj ob = Tab.find(t.string);// check in symbol table
		Struct type;
		if (ob.kind != Obj.Type) error("Wrong type!");
		if (sym == lbrack){  // change the struct type as array of the old type when has brack.
			scan();
			check(rbrack);
			type = new Struct(Struct.Arr);
			type.elemType = ob.type;
		}
		return ob.type;
	}

	// Block = "{" {Statement} "}".
	private static void Block()
	{
		check(lbrace);
		while(!statSeqFollow.get(sym))Statement();
		check(rbrace);
	}

	// Block = "{" {Statement | VarDecl} "}".
//	private static void Block(){ //solving the conflict with semantic information
//		check(lbrace);
//		for (;;){
//			if (NextTokenIsType())VarDecl();
//			else if (firststate.get(sym))Statement();
//			else if (statSeqFollow.get(sym)) break;
//			else {
//				error("Unexpected symbol, expected a variable declaration or a statement.");
//			}
//		}
//		check(rbrace);
//	}
//
//	private static  boolean NextTokenIsType(){
//		if (sym != ident) return false;
//		Obj obj = Tab.find(la.string);
//		return obj.kind == Obj.Type;
//	}

	//Statement = Designator ("=" Expr | ActPars) ";"
	//	| "if" "(" Condition ")" Statement ["else" Statement]
	//	| "while" "(" Condition ")" Statement
	//	| "return" [Expr] ";"
	//	| "read" "(" Designator ")" ";"
	//	| "print" "(" Expr ["," number] ")" ";"
	//	| Block
	//	| ";".
	private static void Statement() {
        /* if (!statStart.get(sym)){
            error("Invalid start of statement");
            while(!statStart.get(sym)) scan();
            errDist = 0;
        } */
        if (!statStart.get(sym)) { //improvement of the synchronization
            error("Invalid start of statement");
            do {
                scan();
            } while (!statSync.get(sym) && sym != rbrace && sym != semicolon);
            if (sym == semicolon) {
                scan();
            }
            errDist = 0;
        }
		if (sym == ident) {
			// Assignment statements or method calls
			Designator();
			if (sym == assign) {
				scan();
				Expr();
			} else if (sym == lpar) {
				ActPars();
			} else {
				error("= or ( expected after identifier");
			}
			check(semicolon);
		} else if (sym == if_) {
			// if statement
			scan();
			check(lpar);
			Condition();
			check(rpar);
			Statement();
			if (sym == else_) {
				scan();
				Statement();
			}
		} else if (sym == while_) {
			scan();
			check(lpar);
			Condition();
			check(rpar);
			Statement();
		} else if (sym == read_) {
			// read statement
			scan();
			check(lpar);
			Designator();
			check(rpar);
			check(semicolon);
		} else if (sym == print_) {
			// print statement
			scan();
			check(lpar);
			Expr();
			if (sym == comma) {
				scan();
				check(number);
			}
			check(rpar);
			check(semicolon);
		} else if (sym == return_) {
			// return statement
			scan();
			if (sym == minus || sym == ident) {
				Expr();
			}
			check(semicolon);
		} else if (sym == lbrace) {
			Block();
		} else if (sym == semicolon) {
			scan();
		} else {
			error("Invalid statement");
		}
	}

	//ActPars = "(" [ Expr {"," Expr} ] ")".
	private static void ActPars() {
		check(lpar);
		if (exprStart.get(sym)) {
			Expr();
			while (sym == comma) {
				scan();
				Expr();
			}
		}
		check(rpar);
	}

	//Condition = Expr Relop Expr.
	private static void Condition() {
		Expr(); // Left expression
		Relop();
		Expr(); // Right expression
	}

	//Relop = "==" | "!=" | ">" | ">=" | "<" | "<=".
	private  static void Relop(){
		if (sym == eql || sym == neq || sym == lss || sym == leq || sym == gtr || sym == geq) {
			scan(); // Comparison Operators
		} else {
			error("Comparison operator expected");
		}
	}

	//Expr = ["-"] Term {Addop Term}.
    private static void Expr(){
		Operand x,y;
        if (sym == minus) {
            scan();
        }
		Term();
		for (;;){
			if (sym == minus){
				Addop();
				Term();
			}else if (sym == plus){
				Addop();
				Term();
			} else break;
		}
    }

	//Term = Factor {Mulop Factor}.
	private static void Term() {
		Factor();
		for (;;) { // Infinite loop, must break out explicitly
			if (sym == times || sym == slash || sym == rem) {
				Mulop(); // Process multiplication, division, or modulo operator
				Factor(); // Process the next factor
			} else {
				break; // Break out of the loop if no more multiplication/division/modulo operators
			}
		}
	}

	/*Factor = Designator  [ActPArs]
               | Number
               | charConst
               | */
	private static void Factor() {
		if (sym == number) {
			scan();
		} else if (sym == ident) {
			Designator();
            /*if (sym == lpar) { // Checking function call
                ActPars();
            }*/
		} else if (sym == charCon) {
			scan();
		} else if (sym == lpar) {
			scan();
			Expr();
			check(rpar);
		} else if (sym == new_) {
			scan();
			check(ident); // Checking class names or types
			if (sym == lbrack) {
				scan();
				Expr();
				check(rbrack);
			}
		} else {
			error("Invalid factor: Unexpected symbol");
		}
	}

	//Designator = ident {"." ident | "[" Exp "]"}.
	private static void Designator() {
		check(ident);
		Obj obj = Tab.find(t.string);
        for (;;) {
            if (sym == period) {
                scan();
                check(ident);
            } else if (sym == lbrack) {
                scan();
                Expr();
                check(rbrack);
            } else break;
        }
	}


	// Addop = "+" | "-".
	private  static void Addop(){
		if (sym == plus) {
			scan();
		} else if (sym == minus) {
			scan();
		} else {
			error("Expected addition or subtraction operator"); // Error handling
		}
	}

	//Mulop = "*" | "/" | "%".
	private  static void Mulop(){
		if (sym == times) {
			scan();
		} else if (sym == slash) {
			scan();
		} else if (sym == rem) {
			scan();
		} else {
			error("Expected multiplication, division, or modulo operator"); // Error handling
		}
	}

	public static void parse() {
		// initialize symbol sets
		BitSet s;
		s = new BitSet(64); exprStart = s;
		s.set(ident); s.set(number); s.set(charCon); s.set(new_); s.set(lpar); s.set(minus);

		s = new BitSet(64); statStart = s;
		s.set(ident); s.set(if_); s.set(while_); s.set(read_);
		s.set(return_); s.set(print_); s.set(lbrace); s.set(semicolon);

		s = new BitSet(64); statSync = s;
		s.set(eof); s.set(if_); s.set(while_); s.set(read_);
		s.set(return_); s.set(print_); s.set(lbrace); s.set(semicolon);

		s = new BitSet(64); statSeqFollow = s;
		s.set(rbrace); s.set(eof);

		s = new BitSet(64); declStart = s;
		s.set(final_); s.set(ident); s.set(class_);

		s = new BitSet(64); declFollow = s;
		s.set(lbrace); s.set(void_); s.set(eof);

		s = new BitSet(64); firststate = s;
		s.set(if_); s.set(while_); s.set(read_); s.set(return_); s.set(lbrace); s.set(ident); s.set(print_);

		// start parsing
		errors = 0; errDist = 3;
		//Initialize
		Tab.init();
		Code.init();
		scan();
		Program();
		if (sym != eof) error("end of file found before end of program");
	}
}








