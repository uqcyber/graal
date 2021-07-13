package org.graalvm.veriopt.integration;

import jdk.vm.ci.meta.{Constant, JavaConstant, JavaKind, PrimitiveConstant}
import org.graalvm.compiler.core.common.`type`.{IntegerStamp, Stamp, StampFactory, StampPair}
import org.graalvm.compiler.nodes.{ConstantNode, LogicNegationNode, LogicNode, NodeView, ParameterNode, StructuredGraph, ValueNode}
import org.graalvm.compiler.nodes.calc.{AbsNode, AddNode, AndNode, BinaryNode, ConditionalNode, IntegerBelowNode, IntegerEqualsNode, IntegerLessThanNode, MulNode, NegateNode, NotNode, OrNode, SubNode, UnaryArithmeticNode, UnaryNode, XorNode}
import org.graalvm.veriopt.integration.IRTreeEval.{BinAdd, BinAnd, BinIntegerBelow, BinIntegerEquals, BinIntegerLessThan, BinMul, BinOr, BinSub, BinXor, BinaryExpr, UnaryAbs, UnaryLogicNegation, UnaryNeg, UnaryNot}
import org.graalvm.veriopt.integration.Stamp2.VoidStamp;

object HOL {

  abstract sealed class itself[A]
  final case class typea[A]() extends itself[A]

} /* object HOL */

object Nat {

  abstract sealed class nat
  final case class zero_nat() extends nat
  final case class Suc(a: nat) extends nat

  def one_nat: nat = Suc(zero_nat())

  def plus_nat(x0: nat, n: nat): nat = (x0, n) match {
    case (Suc(m), n) => plus_nat(m, Suc(n))
    case (zero_nat(), n) => n
  }

  def times_nat(x0: nat, n: nat): nat = (x0, n) match {
    case (zero_nat(), n) => zero_nat()
    case (Suc(m), n) => plus_nat(n, times_nat(m, n))
  }

} /* object Nat */

object Num {

  abstract sealed class num
  final case class One() extends num
  final case class Bit0(a: num) extends num
  final case class Bit1(a: num) extends num

  def nat_of_num(x0: num): Nat.nat = x0 match {
    case Bit1(n) => {
      val m: Nat.nat = nat_of_num(n);
      Nat.Suc(Nat.plus_nat(m, m))
    }
    case Bit0(n) => {
      val m: Nat.nat = nat_of_num(n);
      Nat.plus_nat(m, m)
    }
    case One() => Nat.one_nat
  }

} /* object Num */

object Int {

  abstract sealed class int
  final case class zero_int() extends int
  final case class Pos(a: Num.num) extends int
  final case class Neg(a: Num.num) extends int

  def one_int: int = Pos(Num.One())

} /* object Int */

object Countable {

  trait countable[A] {
  }
  object countable{
  }

} /* object Countable */

object Finite_Set {

  trait finite[A] extends Countable.countable[A] {
  }
  object finite{
  }

} /* object Finite_Set */

object Numeral_Type {

  abstract sealed class bit0[A]
  final case class Abs_bit0[A](a: Int.int) extends bit0[A]

  abstract sealed class num1
  final case class one_num1() extends num1

} /* object Numeral_Type */

object Type_Length {

  trait len0[A] {
    val `Type_Length.len_of`: (HOL.itself[A]) => Nat.nat
  }
  def len_of[A](a: HOL.itself[A])(implicit A: len0[A]): Nat.nat =
    A.`Type_Length.len_of`(a)
  object len0{
    implicit def `Type_Length.len0_num1`: len0[Numeral_Type.num1] = new
        len0[Numeral_Type.num1] {
      val `Type_Length.len_of` = (a: HOL.itself[Numeral_Type.num1]) =>
        len_of_num1(a)
    }
    implicit def `Type_Length.len0_bit0`[A : len0]: len0[Numeral_Type.bit0[A]] =
      new len0[Numeral_Type.bit0[A]] {
        val `Type_Length.len_of` = (a: HOL.itself[Numeral_Type.bit0[A]]) =>
          len_of_bit0[A](a)
      }
  }

  def len_of_bit0[A : len0](uu: HOL.itself[Numeral_Type.bit0[A]]): Nat.nat =
    Nat.times_nat(Num.nat_of_num(Num.Bit0(Num.One())), len_of[A](HOL.typea[A]()))

  trait len[A] extends len0[A] {
  }
  object len{
    implicit def `Type_Length.len_num1`: len[Numeral_Type.num1] = new
        len[Numeral_Type.num1] {
      val `Type_Length.len_of` = (a: HOL.itself[Numeral_Type.num1]) =>
        len_of_num1(a)
    }
    implicit def `Type_Length.len_bit0`[A : len]: len[Numeral_Type.bit0[A]] = new
        len[Numeral_Type.bit0[A]] {
      val `Type_Length.len_of` = (a: HOL.itself[Numeral_Type.bit0[A]]) =>
        len_of_bit0[A](a)
    }
  }

  def len_of_num1(uu: HOL.itself[Numeral_Type.num1]): Nat.nat = Nat.one_nat

} /* object Type_Length */

object Word {

  abstract sealed class word[A]
  final case class Worda[A](a: Int.int) extends word[A]

  def one_word[A : Type_Length.len]: word[A] = Worda[A](Int.one_int)

  def zero_word[A : Type_Length.len]: word[A] = Worda[A](Int.zero_int())

} /* object Word */

object Float {

  abstract sealed class float
  final case class Floata(a: Int.int, b: Int.int) extends float

} /* object Float */

object String {

  abstract sealed class char
  final case class Char(a: Boolean, b: Boolean, c: Boolean, d: Boolean,
                        e: Boolean, f: Boolean, g: Boolean, h: Boolean)
    extends char

} /* object String */

object Stamp2 {

  abstract sealed class Stamp
  final case class VoidStamp() extends Stamp
  final case class IntegerStamp(a: Nat.nat, b: Int.int, c: Int.int) extends Stamp
  final case class KlassPointerStamp(a: Boolean, b: Boolean) extends Stamp
  final case class MethodCountersPointerStamp(a: Boolean, b: Boolean) extends
    Stamp
  final case class MethodPointersStamp(a: Boolean, b: Boolean) extends Stamp
  final case class ObjectStamp(a: List[String.char], b: Boolean, c: Boolean,
                               d: Boolean)
    extends Stamp
  final case class RawPointerStamp(a: Boolean, b: Boolean) extends Stamp
  final case class IllegalStamp() extends Stamp

} /* object Stamp2 */

object Values2 {

  abstract sealed class Value
  final case class UndefVal() extends Value
  final case class IntVal32(a: Word.word[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.num1]]]]]])
    extends Value
  final case class IntVal64(a: Word.word[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.bit0[Numeral_Type.num1]]]]]]])
    extends Value
  final case class FloatVal(a: Float.float) extends Value
  final case class ObjRef(a: Option[Nat.nat]) extends Value
  final case class ObjStr(a: List[String.char]) extends Value

} /* object Values2 */

object IRTreeEval {

  abstract sealed class IRBinaryOp
  final case class BinAdd() extends IRBinaryOp
  final case class BinMul() extends IRBinaryOp
  final case class BinSub() extends IRBinaryOp
  final case class BinAnd() extends IRBinaryOp
  final case class BinOr() extends IRBinaryOp
  final case class BinXor() extends IRBinaryOp
  final case class BinIntegerEquals() extends IRBinaryOp
  final case class BinIntegerLessThan() extends IRBinaryOp
  final case class BinIntegerBelow() extends IRBinaryOp

  abstract sealed class IRUnaryOp
  final case class UnaryAbs() extends IRUnaryOp
  final case class UnaryNeg() extends IRUnaryOp
  final case class UnaryNot() extends IRUnaryOp
  final case class UnaryLogicNegation() extends IRUnaryOp

  abstract sealed class IRExpr
  final case class UnaryExpr(a: IRUnaryOp, b: IRExpr) extends IRExpr
  final case class BinaryExpr(a: IRBinaryOp, b: IRExpr, c: IRExpr) extends IRExpr
  final case class ConditionalExpr(a: IRExpr, b: IRExpr, c: IRExpr) extends IRExpr
  final case class ConstantExpr(a: Values2.Value) extends IRExpr
  final case class ParameterExpr(a: Nat.nat, b: Stamp2.Stamp) extends IRExpr
  final case class LeafExpr(a: Nat.nat, b: Stamp2.Stamp) extends IRExpr

} /* object IRTreeEval */


object VerioptIntegration {
  def intToNum(n:Int): Num.num = {
    var v: Num.num = Num.One()
    for (i <- n to 0 by -1) {
      if ((n & (1 << i)) != 0) {
        v = Num.Bit1(v);
      } else {
        v = Num.Bit0(v);
      }
    }
    v
  }

  def numToInt(a: Num.num): Int = a match {
    case Num.One() => 1
    case Num.Bit0(a) => 0 // TODO: implement
    case Num.Bit1(a) => 1 // TODO: implement
  }

  def intToInt(a: Int.int): Int = a match {
    case Int.zero_int() => 0
    case Int.Pos(a) => numToInt(a)
    case Int.Neg(a) => -numToInt(a)
  }

  def intToInt(a: Int): Int.int = {
    if (a < 0) {
      Int.Neg(intToNum(a));
    } else if (a > 0) {
      Int.Pos(intToNum(a));
    } else {
      Int.zero_int();
    }
  }

  def natToInt(a: Nat.nat): Int = a match {
    case Nat.zero_nat() => 0;
    case Nat.Suc(a) => 1 + natToInt(a);
  }

  def valueToConstant(a: Values2.Value): JavaConstant = a match {
    case Values2.UndefVal() => JavaConstant.forIllegal();
    case Values2.FloatVal(a) => JavaConstant.defaultForKind(JavaKind.Float); // TODO: implement
    case Values2.IntVal32(Word.Worda(v)) => JavaConstant.forPrimitiveInt(32, intToInt(v))
    case Values2.IntVal64(Word.Worda(v)) => JavaConstant.forPrimitiveInt(64, intToInt(v))
    case Values2.ObjRef(a) => JavaConstant.defaultForKind(JavaKind.Object); // TODO: implement
    case Values2.ObjStr(v) => JavaConstant.defaultForKind(JavaKind.Object); // TODO: implement
  }

  def stampToStamp(a: Stamp2.Stamp): Stamp = a match {
    case Stamp2.VoidStamp() => StampFactory.forVoid();
    case Stamp2.IntegerStamp(bits, lower, upper) => IntegerStamp.create(natToInt(bits), intToInt(lower), intToInt(upper));
    case Stamp2.KlassPointerStamp(a, b) => StampFactory.forKind(JavaKind.Illegal); // TODO: implement
    case Stamp2.MethodCountersPointerStamp(a, b) => StampFactory.forKind(JavaKind.Illegal); // TODO: implement
    case Stamp2.MethodPointersStamp(a, b) => StampFactory.forKind(JavaKind.Illegal); // TODO: implement
    case Stamp2.ObjectStamp(a, b, c, d) => StampFactory.forKind(JavaKind.Illegal); // TODO: implement
    case Stamp2.RawPointerStamp(a, b) => StampFactory.forKind(JavaKind.Illegal); // TODO: implement
    case Stamp2.IllegalStamp() => StampFactory.forKind(JavaKind.Illegal);
  }

  def exprToNode(g: StructuredGraph, a: IRTreeEval.IRExpr): ValueNode = a match {
    case IRTreeEval.UnaryExpr(IRTreeEval.UnaryAbs(), e) => new AbsNode(exprToNode(g, e));
    case IRTreeEval.UnaryExpr(IRTreeEval.UnaryNeg(), e) => new NegateNode(exprToNode(g, e));
    case IRTreeEval.UnaryExpr(IRTreeEval.UnaryNot(), e) => NotNode.create(exprToNode(g, e));
    case IRTreeEval.UnaryExpr(IRTreeEval.UnaryLogicNegation(), e) => new LogicNegationNode(exprToNode(g, e).asInstanceOf[LogicNode]);

    case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), x, y) => AddNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinMul(), x, y) => MulNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinSub(), x, y) => SubNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinAnd(), x, y) => AndNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinOr(), x, y) => OrNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinXor(), x, y) => XorNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinIntegerEquals(), x, y) => IntegerEqualsNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinIntegerLessThan(), x, y) => IntegerLessThanNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);
    case IRTreeEval.BinaryExpr(IRTreeEval.BinIntegerBelow(), x, y) => IntegerBelowNode.create(exprToNode(g, x), exprToNode(g, y), NodeView.DEFAULT);

    case IRTreeEval.ConditionalExpr(c, tv, fv) => new ConditionalNode(exprToNode(g, c).asInstanceOf[LogicNode], exprToNode(g, tv), exprToNode(g, fv));

    case IRTreeEval.ConstantExpr(a) => ConstantNode.forPrimitive(valueToConstant(a));

    case IRTreeEval.ParameterExpr(i, stamp) => new ParameterNode(natToInt(i), StampPair.createSingle(IntegerStamp.create(32, 0, 0).unrestricted())); // TODO: implement

    case IRTreeEval.LeafExpr(nid, stamp) => g.getNode(natToInt(nid)).asInstanceOf[ValueNode];
  }

  def unaryToOp(op: ValueNode): IRTreeEval.IRUnaryOp = op match {
    case op: AbsNode => UnaryAbs()
    case op: NegateNode => UnaryNeg()
    case op: NotNode => UnaryNot()
    case op: LogicNegationNode => UnaryLogicNegation()
  }

  def binaryToOp(op: ValueNode): IRTreeEval.IRBinaryOp = op match {
    case op: AddNode => BinAdd()
    case op: MulNode => BinMul()
    case op: SubNode => BinSub()
    case op: AndNode => BinAnd()
    case op: OrNode => BinOr()
    case op: XorNode => BinXor()
    case op: IntegerEqualsNode => BinIntegerEquals()
    case op: IntegerLessThanNode => BinIntegerLessThan()
    case op: IntegerBelowNode => BinIntegerBelow()
  }

  def constantToValue(c: Constant): Values2.Value = c match {
    case constant: PrimitiveConstant => Values2.IntVal32(Word.Worda(intToInt(constant.asInt()))); // TODO: implement
    case _ => ???
  }

  def stampToStamp(s: Stamp): Stamp2.Stamp = s match {
    case stamp: IntegerStamp => Stamp2.IntegerStamp(Nat.one_nat, Int.one_int, Int.one_int);
    case _ => Stamp2.IllegalStamp();
  }

  def intToNat(n: Int): Nat.nat = {
    var nat: Nat.nat = Nat.zero_nat()
    for(_ <- 1 to n) {
      nat = Nat.Suc(nat)
    }
    nat
  }

  def nodeToExpr(g: StructuredGraph, n: ValueNode): IRTreeEval.IRExpr = n match {
    case node: UnaryNode => IRTreeEval.UnaryExpr(unaryToOp(node), nodeToExpr(g, node.getValue));
    case node: BinaryNode => IRTreeEval.BinaryExpr(binaryToOp(node), nodeToExpr(g, node.getX), nodeToExpr(g, node.getY));
    case node: ConditionalNode => IRTreeEval.ConditionalExpr(nodeToExpr(g, node.condition()), nodeToExpr(g, node.trueValue()), nodeToExpr(g, node.falseValue()));
    case node: ConstantNode => IRTreeEval.ConstantExpr(constantToValue(node.getValue));
    case node: ParameterNode => IRTreeEval.ParameterExpr(intToNat(node.index()), stampToStamp(node.stamp(NodeView.DEFAULT))); // TODO: implement
    case node => IRTreeEval.LeafExpr(intToNat(node.getId()), stampToStamp(node.stamp(NodeView.DEFAULT))) // TODO: implement
  }

  def canonicalizeNode(n: IRTreeEval.IRExpr): IRTreeEval.IRExpr =
    (n match {
      case IRTreeEval.UnaryExpr(_, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.UnaryExpr(IRTreeEval.UnaryAbs(), _))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), x,
      IRTreeEval.UnaryExpr(IRTreeEval.UnaryNeg(), y))
      => IRTreeEval.BinaryExpr(IRTreeEval.BinSub(), x, y)
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.UnaryExpr(IRTreeEval.UnaryNot(), _))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.UnaryExpr(IRTreeEval.UnaryLogicNegation(),
      _))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.BinaryExpr(_, _, _))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.ConditionalExpr(_, _, _))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.ConstantExpr(_))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.ParameterExpr(_, _))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAdd(), _,
      IRTreeEval.LeafExpr(_, _))
      => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinMul(), _, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinSub(), _, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinAnd(), _, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinOr(), _, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinXor(), _, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinIntegerEquals(), _, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinIntegerLessThan(), _, _) => n
      case IRTreeEval.BinaryExpr(IRTreeEval.BinIntegerBelow(), _, _) => n
      case IRTreeEval.ConditionalExpr(_, _, _) => n
      case IRTreeEval.ConstantExpr(_) => n
      case IRTreeEval.ParameterExpr(_, _) => n
      case IRTreeEval.LeafExpr(_, _) => n
    })

  def canonicalize(g: StructuredGraph, n: ValueNode): ValueNode =
    try {
      val expr = nodeToExpr(g, n)
      val canonicalized = canonicalizeNode(expr)
      if (!expr.equals(canonicalized)) {
        return exprToNode(g, canonicalized)
      }
      n
    } catch {
      case e: MatchError => {
        println("unable to match:" + e);
        n
      }
    }

}
