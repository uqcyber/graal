import java.util.Set;
import java.util.HashSet;

interface ASTNode {
    public void accept (Visitor v);
}
class IdentifierNode implements ASTNode {
    public final String id;
    public IdentifierNode (String id) {
	this.id = id;
    }
    public void accept (Visitor v) {
	System.out.println("1");
	v.visit(this);
    }
}
class NumberNode implements ASTNode {
    public final String num;
    public NumberNode (String num) {
	this.num = num;
    }
    public void accept (Visitor v) {
	System.out.println("2");
	v.visit(this);
    }
}
class BinopNode implements ASTNode {
    public final String op;
    public final ASTNode lhs;
    public final ASTNode rhs;
    public BinopNode (ASTNode lhs,
		      String op,
		      ASTNode rhs) {
	this.lhs = lhs;
	this.op = op;
	this.rhs = rhs;
    }
    public void accept (Visitor v) {
	System.out.println("3");
	v.visit(this);
    }
}
class Visitor {
    public void visit (ASTNode n) {
	System.out.println("4");
	n.accept(this);
    }
    public void visit (IdentifierNode n) {
	System.out.println("5");
    }
    public void visit (NumberNode n) {
	System.out.println("6");
    }
    public void visit (BinopNode n) {
	System.out.println("7");
	n.lhs.accept(this);
	n.rhs.accept(this);
    }
}
class FindFree extends Visitor {
    public Set variables;
    public FindFree () {
	this.variables = new HashSet();
    }
    public void visit (IdentifierNode n) {
	System.out.println("8");
	variables.add(n);
    }
}
class VisitorTest {
    public static void main(String[] args) {
	// Construct AST for expression 1+i
	ASTNode a = (ASTNode) new BinopNode(
					    new NumberNode("1"),
					    "+",
					    new IdentifierNode("i")
					    );
	Visitor v = new FindFree();
	v.visit(a);
    }
}