/**
 * Parser class for a simple interpreter.
 * 
 * (c) 2020 by Ronald Mak
 * Department of Computer Science
 * San Jose State University
 */
package frontend;

import java.util.HashSet;

import intermediate.*;
import intermediate.Node.NodeType;

import static frontend.Token.TokenType.*;
import static intermediate.Node.NodeType.*;

public class Parser
{
    private Scanner scanner;
    private Symtab symtab;
    private Token currentToken;
    private int lineNumber;
    private int errorCount;

    public Parser(Scanner scanner, Symtab symtab)
    {
        this.scanner = scanner;
        this.symtab  = symtab;
        this.currentToken = null;
        this.lineNumber = 1;
        this.errorCount = 0;
    }

    public int errorCount() { return errorCount; }

    public Node parseProgram()
    {
        Node programNode = new Node(Node.NodeType.PROGRAM);

        currentToken = scanner.nextToken();  // first token!

        if (currentToken.type == Token.TokenType.PROGRAM) 
        {
            currentToken = scanner.nextToken();  // consume PROGRAM
        }
        else syntaxError("Expecting PROGRAM");

        if (currentToken.type == IDENTIFIER) 
        {
            String programName = currentToken.text;
            symtab.enter(programName);
            programNode.text = programName;

            currentToken = scanner.nextToken();  // consume program name
        }
        else syntaxError("Expecting program name");

        if (currentToken.type == SEMICOLON) 
        {
            currentToken = scanner.nextToken();  // consume ;
        }
        else syntaxError("Missing ;");

        if (currentToken.type != BEGIN) syntaxError("Expecting BEGIN");

        // The PROGRAM node adopts the COMPOUND tree.
        programNode.adopt(parseCompoundStatement());

        if (currentToken.type != PERIOD) syntaxError("Expecting .");
        return programNode;
    }

    private static HashSet<Token.TokenType> statementStarters;
    private static HashSet<Token.TokenType> statementFollowers;
    private static HashSet<Token.TokenType> relationalOperators;
    private static HashSet<Token.TokenType> simpleExpressionOperators;
    private static HashSet<Token.TokenType> termOperators;

    static
    {
        statementStarters = new HashSet<Token.TokenType>();
        statementFollowers = new HashSet<Token.TokenType>();
        relationalOperators = new HashSet<Token.TokenType>();
        simpleExpressionOperators = new HashSet<Token.TokenType>();
        termOperators = new HashSet<Token.TokenType>();

        // Tokens that can start a statement.
        statementStarters.add(BEGIN);
        statementStarters.add(IDENTIFIER);
        statementStarters.add(REPEAT);
        statementStarters.add(Token.TokenType.WRITE);
        statementStarters.add(Token.TokenType.WRITELN);

        // Tokens that can immediately follow a statement.
        statementFollowers.add(SEMICOLON);
        statementFollowers.add(END);
        statementFollowers.add(UNTIL);
        statementFollowers.add(END_OF_FILE);

        relationalOperators.add(EQUALS);
        relationalOperators.add(LESS_THAN);
        relationalOperators.add(GREATER_THAN);
        relationalOperators.add(GREATER_EQUALS);
        relationalOperators.add(LESS_EQUALS);
        relationalOperators.add(NOT_EQUALS);

        simpleExpressionOperators.add(PLUS);
        simpleExpressionOperators.add(MINUS);

        termOperators.add(STAR);
        termOperators.add(SLASH);
    }

    private Node parseStatement()
    {
        Node stmtNode = null;
        int savedLineNumber = currentToken.lineNumber;
        lineNumber = savedLineNumber;

        switch (currentToken.type)
        {
        case IDENTIFIER : stmtNode = parseAssignmentStatement(); break;
        case BEGIN :      stmtNode = parseCompoundStatement();   break;
        case REPEAT :     stmtNode = parseRepeatStatement();     break;
        case WHILE :      stmtNode = parseWhileStatement();      break;
        case FOR :        stmtNode = parseForStatement();        break;
        case CASE:        stmtNode = parseCaseStatement();       break;
        case WRITE :      stmtNode = parseWriteStatement();      break;
        case WRITELN :    stmtNode = parseWritelnStatement();    break;
        case SEMICOLON :  stmtNode = null; break;  // empty statement
        default : syntaxError("Unexpected token");
        }

        if (stmtNode != null) stmtNode.lineNumber = savedLineNumber;
        return stmtNode;
    }

    private Node parseAssignmentStatement()
    {
        // The current token should now be the left-hand-side variable name.

        Node assignmentNode = new Node(ASSIGN);

        // Enter the variable name into the symbol table
        // if it isn't already in there.
        String variableName = currentToken.text;
        SymtabEntry variableId = symtab.lookup(variableName.toLowerCase());
        if (variableId == null) variableId = symtab.enter(variableName);

        // The assignment node adopts the variable node as its first child.
        Node lhsNode  = new Node(VARIABLE);        
        lhsNode.text  = variableName;
        lhsNode.entry = variableId;
        assignmentNode.adopt(lhsNode);

        currentToken = scanner.nextToken();  // consume the LHS variable;

        if (currentToken.type == COLON_EQUALS) 
        {
            currentToken = scanner.nextToken();  // consume :=
        }
        else syntaxError("Missing :=");

        // The assignment node adopts the expression node as its second child.
        Node rhsNode = parseExpression();
        assignmentNode.adopt(rhsNode);

        return assignmentNode;
    }

    private Node parseCompoundStatement()
    {
        Node compoundNode = new Node(COMPOUND);
        compoundNode.lineNumber = currentToken.lineNumber;

        currentToken = scanner.nextToken();  // consume BEGIN
        parseStatementList(compoundNode, END);    

        if (currentToken.type == END) 
        {
            currentToken = scanner.nextToken();  // consume END
        }
        else syntaxError("Expecting END");

        return compoundNode;
    }

    private void parseStatementList(Node parentNode, Token.TokenType terminalType)
    {
        while (   (currentToken.type != terminalType) 
                && (currentToken.type != END_OF_FILE))
        {
            Node stmtNode = parseStatement();
            if (stmtNode != null) parentNode.adopt(stmtNode);

            // A semicolon separates statements.
            if (currentToken.type == SEMICOLON)
            {
                while (currentToken.type == SEMICOLON)
                {
                    currentToken = scanner.nextToken();  // consume ;
                }
            }
            else if (statementStarters.contains(currentToken.type))
            {
                syntaxError("Missing ;");
            }
        }
    }

    private Node parseRepeatStatement()
    {
        // The current token should now be REPEAT.

        // Create a LOOP node.
        Node loopNode = new Node(LOOP);
        currentToken = scanner.nextToken();  // consume REPEAT

        parseStatementList(loopNode, UNTIL);    

        if (currentToken.type == UNTIL) 
        {
            // Create a TEST node. It adopts the test expression node.
            Node testNode = new Node(TEST);
            lineNumber = currentToken.lineNumber;
            testNode.lineNumber = lineNumber;
            currentToken = scanner.nextToken();  // consume UNTIL

            testNode.adopt(parseExpression());

            // The LOOP node adopts the TEST node as its final child.
            loopNode.adopt(testNode);
        }
        else syntaxError("Expecting UNTIL");

        return loopNode;
    }

    private Node parseForStatement() {
        boolean isTO = false; // flag for isTO/DOWNTO

        Node forNode = new Node(COMPOUND);
        currentToken = scanner.nextToken(); // consume FOR

        //Adopt the assignment statement node
        forNode.adopt(parseAssignmentStatement());

        //create the loop node
        Node loopNode = new Node(LOOP);

        if(currentToken.type == TO) {
            currentToken = scanner.nextToken(); // consume TO
            isTO = true;

            Node testNode = new Node(TEST);
            Node gt = new Node(GT);
            gt.adopt(forNode.children.get(0).children.get(0)); // get the variable from the original assign node
            gt.adopt(parseExpression()); // Parse next number

            testNode.adopt(gt);
            loopNode.adopt(testNode);
        }

        else if(currentToken.type == DOWNTO) {
            currentToken = scanner.nextToken(); // consume DOWNTO
            isTO = false;

            Node testNode = new Node(TEST);
            Node lt = new Node(LT);
            lt.adopt(forNode.children.get(0).children.get(0)); // get the variable from the original assign node
            lt.adopt(parseExpression()); // Parse next number

            testNode.adopt(lt);
            loopNode.adopt(testNode);
        }
        else syntaxError("Expecting TO or DOWNTO");

        //Parse DO 
        if(currentToken.type == DO) {
            currentToken = scanner.nextToken(); // consume DO

            // If DO is present, consume it and adopt either a compound statement
            // or the next statement (stole bhavdeep's code) 
            if (currentToken.type == BEGIN) {
                loopNode.adopt(parseCompoundStatement());
            }
            else {
                loopNode.adopt(parseStatement());
            }
        }
        else syntaxError("Expecting DO");

        Node assignNode = new Node(ASSIGN);
        assignNode.adopt(forNode.children.get(0).children.get(0));// get the variable from the original assign node

        if(isTO) {
            Node addNode = new Node(ADD);
            addNode.adopt(forNode.children.get(0).children.get(0));// get the variable from the original assign node
            Node constIntNode = new Node(INTEGER_CONSTANT);
            long val = 1;
            constIntNode.value = val;

            addNode.adopt(constIntNode);

            assignNode.adopt(addNode);
        }
        else {
            Node subNode = new Node(SUBTRACT);
            subNode.adopt(forNode.children.get(0).children.get(0));// get the variable from the original assign node
            Node constIntNode = new Node(INTEGER_CONSTANT);
            long val = 1;
            constIntNode.value = val;
            subNode.adopt(constIntNode);

            assignNode.adopt(subNode);
        }

        loopNode.adopt(assignNode);
        forNode.adopt(loopNode);


        return forNode;
    }

    private Node parseWhileStatement() {
        Node loopNode = new Node(LOOP);
        currentToken = scanner.nextToken(); // consume WHILE

        Node testNode = new Node(TEST);
        lineNumber = currentToken.lineNumber;
        testNode.lineNumber = lineNumber;

        // Create and adopt a NOT node
        Node notNode = new Node(Node.NodeType.NOT);
        notNode.adopt(parseExpression());
        testNode.adopt(notNode);

        // LOOP node adopts TEST node
        loopNode.adopt(testNode);
        
        System.out.println("-------> " + currentToken.text);

        // If DO is present, consume it and adopt either a compound statement
        // or the next statement tree
        if(currentToken.type == DO) {
            currentToken = scanner.nextToken();
            if (currentToken.type == BEGIN) {
                loopNode.adopt(parseCompoundStatement());
            }
            else {
                loopNode.adopt(parseStatement());
            }
        }
        else syntaxError("Expecting DO");

        return loopNode;
    }

    private Node parseCaseStatement() {
        System.out.println("PARSE_CASE_STATEMENT");
        
        Node caseNode = new Node(COMPOUND);
        currentToken = scanner.nextToken(); // consume CASE
        
        
        Node testNode = new Node(TEST);
        System.out.println("CCurrent: " + currentToken.text);
        //System.out.println("CCurrent: " + currentToken);
        lineNumber = currentToken.lineNumber;
        testNode.lineNumber = lineNumber;
        
        currentToken = scanner.nextToken();
        if(currentToken.type == OF) {
            
            currentToken = scanner.nextToken();
            System.out.println("-----" + currentToken.type);
            
            caseNode.adopt(parseCompoundStatement());
            
        } else {
            syntaxError("Expecting OF");
        }
        
        

        return null;
    }

    private Node parseWriteStatement()
    {
        // The current token should now be WRITE.

        // Create a WRITE node. It adopts the variable or string node.
        Node writeNode = new Node(Node.NodeType.WRITE);
        currentToken = scanner.nextToken();  // consume WRITE

        parseWriteArguments(writeNode);
        if (writeNode.children.size() == 0)
        {
            syntaxError("Invalid WRITE statement");
        }

        return writeNode;
    }

    private Node parseWritelnStatement()
    {
        // The current token should now be WRITELN.

        // Create a WRITELN node. It adopts the variable or string node.
        Node writelnNode = new Node(Node.NodeType.WRITELN);
        currentToken = scanner.nextToken();  // consume WRITELN

        if (currentToken.type == LPAREN) parseWriteArguments(writelnNode);
        return writelnNode;
    }

    private void parseWriteArguments(Node node)
    {
        // The current token should now be (

        boolean hasArgument = false;

        if (currentToken.type == LPAREN) 
        {
            currentToken = scanner.nextToken();  // consume (
        }
        else syntaxError("Missing left parenthesis");

        if (currentToken.type == IDENTIFIER)
        {
            node.adopt(parseVariable());
            hasArgument = true;
        }
        else if (   (currentToken.type == CHARACTER)
                || (currentToken.type == STRING))
        {
            node.adopt(parseStringConstant());
            hasArgument = true;
        }
        else syntaxError("Invalid WRITE or WRITELN statement");

        // Look for a field width and a count of decimal places.
        if (hasArgument)
        {
            if (currentToken.type == COLON) 
            {
                currentToken = scanner.nextToken();  // consume ,

                if (currentToken.type == INTEGER)
                {
                    // Field width
                    node.adopt(parseIntegerConstant());

                    if (currentToken.type == COLON) 
                    {
                        currentToken = scanner.nextToken();  // consume ,

                        if (currentToken.type == INTEGER)
                        {
                            // Count of decimal places
                            node.adopt(parseIntegerConstant());
                        }
                        else syntaxError("Invalid count of decimal places");
                    }
                }
                else syntaxError("Invalid field width");
            }
        }

        if (currentToken.type == RPAREN) 
        {
            currentToken = scanner.nextToken();  // consume )
        }
        else syntaxError("Missing right parenthesis");
    }

    private Node parseExpression()
    {
        // The current token should now be an identifier or a number.

        // The expression's root node.
        Node exprNode = parseSimpleExpression();

        // The current token might now be a relational operator.
        if (relationalOperators.contains(currentToken.type))
        {
            Token.TokenType tokenType = currentToken.type;
            Node opNode = tokenType == EQUALS         ? new Node(EQ)
                    : tokenType == LESS_THAN      ? new Node(LT)
                            : tokenType == LESS_EQUALS    ? new Node(LEQ)
                                    : tokenType == GREATER_THAN   ? new Node(GT)
                                            : tokenType == GREATER_EQUALS ? new Node(GEQ)
                                                    : tokenType == NOT_EQUALS     ? new Node(NEQ)
                                                            :                               null;

                                                    currentToken = scanner.nextToken();  // consume relational operator

                                                    // The relational operator node adopts the first simple expression
                                                    // node as its first child and the second simple expression node
                                                    // as its second child. Then it becomes the expression's root node.
                                                    if (opNode != null)
                                                    {
                                                        opNode.adopt(exprNode);
                                                        opNode.adopt(parseSimpleExpression());
                                                        exprNode = opNode;
                                                    }
        }

        return exprNode;
    }

    private Node parseSimpleExpression()
    {
        // The current token should now be an identifier or a number.

        // The simple expression's root node.
        Node simpExprNode = parseTerm();

        // Keep parsing more terms as long as the current token
        // is a + or - operator.
        while (simpleExpressionOperators.contains(currentToken.type))
        {
            Node opNode = currentToken.type == PLUS ? new Node(ADD)
                    : new Node(SUBTRACT);

            currentToken = scanner.nextToken();  // consume the operator

            // The add or subtract node adopts the first term node as its
            // first child and the next term node as its second child. 
            // Then it becomes the simple expression's root node.
            opNode.adopt(simpExprNode);
            opNode.adopt(parseTerm());
            simpExprNode = opNode;
        }

        return simpExprNode;
    }

    private Node parseTerm()
    {
        // The current token should now be an identifier or a number.

        // The term's root node.
        Node termNode = parseFactor();

        // Keep parsing more factors as long as the current token
        // is a * or / operator.
        while (termOperators.contains(currentToken.type))
        {
            Node opNode = currentToken.type == STAR ? new Node(MULTIPLY)
                    : new Node(DIVIDE);

            currentToken = scanner.nextToken();  // consume the operator

            // The multiply or dive node adopts the first factor node as its
            // as its first child and the next factor node as its second child. 
            // Then it becomes the term's root node.
            opNode.adopt(termNode);
            opNode.adopt(parseFactor());
            termNode = opNode;
        }

        return termNode;
    }

    private Node parseFactor()
    {   
        // The current token should now be an identifier or a number or (

        if      (currentToken.type == IDENTIFIER) return parseVariable();
        else if (currentToken.type == INTEGER)    return parseIntegerConstant();
        else if (currentToken.type == REAL)       return parseRealConstant();
        else if (currentToken.type == CHARACTER)  return parseStringConstant();
        else if (currentToken.type == STRING)     return parseStringConstant();

        else if (currentToken.type == LPAREN)
        {
            currentToken = scanner.nextToken();  // consume (
            Node exprNode = parseExpression();
            
            System.out.println("cccc" + currentToken.text);

            if (currentToken.type == RPAREN)
            {
                currentToken = scanner.nextToken();  // consume )
            }
            else syntaxError("Expecting )");

            return exprNode;
        }

        else syntaxError("Unexpected token5555");
        return null;
    }

    private Node parseVariable()
    {
        // The current token should now be an identifier.

        // Has the variable been "declared"?
        String variableName = currentToken.text;
        SymtabEntry variableId = symtab.lookup(variableName.toLowerCase());
        if (variableId == null) semanticError("Undeclared identifier");

        Node node  = new Node(VARIABLE);
        node.text  = variableName;
        node.entry = variableId;

        currentToken = scanner.nextToken();  // consume the identifier        
        return node;
    }

    private Node parseIntegerConstant()
    {
        // The current token should now be a number.

        Node integerNode = new Node(INTEGER_CONSTANT);
        integerNode.value = currentToken.value;

        currentToken = scanner.nextToken();  // consume the number        
        return integerNode;
    }

    private Node parseRealConstant()
    {
        // The current token should now be a number.

        Node realNode = new Node(REAL_CONSTANT);
        realNode.value = currentToken.value;

        currentToken = scanner.nextToken();  // consume the number        
        return realNode;
    }

    private Node parseStringConstant()
    {
        // The current token should now be CHARACTER or STRING.

        Node stringNode = new Node(STRING_CONSTANT);
        stringNode.value = currentToken.value;

        currentToken = scanner.nextToken();  // consume the string        
        return stringNode;
    }

    private void syntaxError(String message)
    {
        System.out.println("SYNTAX ERROR at line " + lineNumber 
                + ": " + message + " at '" + currentToken.text + "'");
        errorCount++;

        // Recover by skipping the rest of the statement.
        // Skip to a statement follower token.
        while (! statementFollowers.contains(currentToken.type))
        {
            currentToken = scanner.nextToken();
        }
    }

    private void semanticError(String message)
    {
        System.out.println("SEMANTIC ERROR at line " + lineNumber 
                + ": " + message + " at '" + currentToken.text + "'");
        errorCount++;
    }
}
