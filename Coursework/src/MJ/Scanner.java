/* MicroJava Scanner (HM 06-12-28)
   =================
*/
package MJ;
import java.io.*;

public class Scanner {
	private static final char eofCh = '\u0080';
	private static final char eol = '\n';
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
	private static final String key[] = { // sorted list of keywords
		"class", "else", "final", "if", "new", "print",
		"program", "read", "return", "void", "while"
	};
	private static final int keyVal[] = {
		class_, else_, final_, if_, new_, print_,
		program_, read_, return_, void_, while_
	};

	private static char ch;			// lookahead character
	public  static int col;			// current column
	public  static int line;		// current line
	private static int pos;			// current position from start of source file
	private static Reader in;  	// source file reader
	private static char[] lex;	// current lexeme (token string)

	//----- ch = next input character
	private static void nextCh() {
		try {
			ch = (char)in.read(); col++; pos++;
			if (ch == eol) {line++; col = 0;}
			else if (ch == '\uffff') ch = eofCh;
		} catch (IOException e) {
			ch = eofCh;
		}
	}

	//--------- Initialize scanner
	public static void init(Reader r) {
		in = new BufferedReader(r);
		lex = new char[64];
		line = 1; col = 0;
		nextCh();
	}

	private static void readName(Token t) {
		StringBuilder sb = new StringBuilder();
		do {
			sb.append(ch);
			nextCh();
		} while (Character.isLetterOrDigit(ch));
		String name = sb.toString();

		// Check if the name is a keyword
		for (int i = 0; i < key.length; i++) {
			if (name.equals(key[i])) {
				t.kind = keyVal[i]; // Set the token kind to the corresponding keyword value
				return;
			}
		}

		// If not a keyword, it's an identifier
		t.kind = ident;
		t.string = name; // Assuming Token has a field to store the string of an identifier
	}

	private static void readNumber(Token t) {
		StringBuilder sb = new StringBuilder();
		do {
			sb.append(ch);
			nextCh();
		} while (Character.isDigit(ch));

		t.kind = number;
		try {
			t.val = Integer.parseInt(sb.toString());
		} catch (NumberFormatException e) {
			reportError("Number format error or overflow: " + sb.toString());
			t.kind = none;
		}
	}

	private static void readCharCon(Token t) {
		nextCh(); // Skip the opening single quote
		if (ch == '\'') {
			reportError("Empty character constant");
			nextCh(); // Skip the closing quote and move on
			t.kind = none; // Set to none or a specific error token
		} else {
			if (ch == '\\') { // Handle escape characters
				nextCh();
				switch (ch) {
					case 'n': t.val = '\n'; break;
					case 't': t.val = '\t'; break;
					case 'r': t.val = '\r'; break;
					case '\'': t.val = '\''; break;
					case '\\': t.val = '\\'; break;
					default:
						reportError("Invalid escape character");
						break;
				}
				nextCh(); // Move to the character after the escape sequence
			} else {
				t.val = ch;
				nextCh(); // Move to the character after the constant
			}
			if (ch != '\'') {
				reportError("Character constant not properly closed");
				t.kind = none; // Set to none or a specific error token
				// Skip until finding a closing quote or a new line to recover from error
				while (ch != '\'' && ch != eol && ch != eofCh) nextCh();
				if (ch == '\'') nextCh(); // Skip the closing quote
			} else {
				nextCh(); // Skip the closing single quote
				t.kind = charCon;
			}
		}
	}

	private static void reportError(String message) {
		System.out.println("Error: " + message + " at line " + line + ", col " + col);
	}

	//---------- Return next input token
	public static Token next() {
		while (ch <= ' ') nextCh();
		Token t = new Token(); // assuming there is a constructor in Token
		t.line = line;
		t.col = col;

		if (Character.isLetter(ch)) {
			readName(t); // to be implemented, for identifying keywords or identifiers
		} else if (Character.isDigit(ch)) {
			readNumber(t); // to be implemented, for identifying numeric literals
		} else if (ch == '\'') {
			readCharCon(t); // Add this line to handle character constants
		} else {
			switch (ch) {
				case '+': nextCh(); t.kind = plus; break;
				case '-': nextCh(); t.kind = minus; break;
				case '*': nextCh(); t.kind = times; break;
				case '/':
					nextCh();
					if (ch == '/') { //Check for comments
						do nextCh();
						while (ch != '\n' && ch != eofCh);
						t = next();  // call scanner recursively
					} else t.kind = slash;
					break;
				case '%': nextCh(); t.kind = rem; break;
				case '=':
					nextCh();
					if (ch == '=') { //Check if assignment or equality comparison
						nextCh();
						t.kind = eql;
					} else t.kind = assign;
					break;
				case ';': nextCh(); t.kind = semicolon; break;
				case ',': nextCh(); t.kind = comma; break;
				case '.': nextCh(); t.kind = period; break;
				case '(': nextCh(); t.kind = lpar; break;
				case ')': nextCh(); t.kind = rpar; break;
				case '[': nextCh(); t.kind = lbrack; break;
				case ']': nextCh(); t.kind = rbrack; break;
				case '{': nextCh(); t.kind = lbrace; break;
				case '}': nextCh(); t.kind = rbrace; break;
				case '<':
					nextCh();
					if (ch == '='){
						nextCh();
						t.kind = leq;
					} else t.kind = lss;
					break;
				case '>':
					nextCh();
					if (ch == '='){
						nextCh();
						t.kind = geq;
					} else t.kind = gtr;
					break;
				case '!':
					nextCh();
					if (ch == '='){
						nextCh();
						t.kind = neq;
					} else {
						t.kind = none;
					}
					break;
				case eofCh: t.kind = eof; break;
				default:
					nextCh();
					t.kind = none;
					break; // handle invalid character
			}
		}
		return t;
	}
}







