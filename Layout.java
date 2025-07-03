//------------------------------------------------------------------------------
// Layout and manipulate a relocatable block of memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Layout extends Test                                                       // Descriobe and manipulate the memory containing the btree
 {final String                source;                                           // The source string we are going to parse into fields  describing the memory layout
  final Stack<Field>          fields = new Stack<>();                           // Each field parsed from the input string
  final TreeMap<String,Field>  names = new TreeMap<>();                         // Names of each field
  Program                          P = new Program();                           // The code that manipulates the fields

//D1 Layout                                                                     // Describe a memory layout

  Layout(String Source)                                                         // A source description of the layout to be parsed into fields
   {source = Source;
    parseFields();
    allocateMemory();                                                           // Allocate memory for all fields
   }

  Layout additionalLayout(String Source)                                        // A source description of an additional layout to be attached to this layout
   {final Layout l = new Layout(Source);                                        // Parse additional layout
    l.P = P;                                                                    // Use the program of the main layout
    return l;                                                                   // Return additional layout
   }

//D1 Fields                                                                     // Describe a field in a memory layout

  class Field                                                                   // The fields in the layout
   {final int     line;                                                         // Line at which the layout was parsed
    final int     indent;                                                       // Indentation
    final String  name;                                                         // Name
    final String  cmd;                                                          // Command
    final Integer rep;                                                          // Optional repetition
    final Integer parent;                                                       // Parent
    final Stack<Field>dimensions = new Stack<>();                               // Dimensions of field
    final Stack<Field>children   = new Stack<>();                               // Children of an item
//  final boolean spacer, array, bit, struct, var, union;                       // Classification - a spacer is a bit or a var as they actually take up space - or a character in "The Caves of Steel"
    final boolean spacer, array, bit, var;                                      // Classification - a spacer is a bit or a var as they actually take up space - or a character in "The Caves of Steel"
    BitSet[]memory;                                                             // Memory for this field
    int     value;                                                              // The last value read from the memory of this field

    Field(int line, int indent, String name, String cmd, Integer rep, Integer parent) // Constructor
     {this.line   = line;
      this.indent = indent;
      this.name   = name;
      this.cmd    = cmd;
      this.rep    = rep;
      this.parent = parent;
      array       = cmd.equals("array");
      bit         = cmd.equals("bit");
      //struct      = cmd.equals("struct");
      //union       = cmd.equals("union");
      var         = cmd.equals("var");
      spacer      = bit || var;
      fields.push(this);
      names.put(name, this);                                                    // Already checked that the name is non unique when we had access to the location of this field in the source
     }

    boolean hasParent()                                                         // Whether a field has a parent.  Variables that do not are top most variables
     {return getParent() != null;
     }

    Field getParent()                                                           // Parent of a field
     {if (parent == null) return null;
      return fields.elementAt(parent);
     }

    String dump()                                                               // Dump the details of a field
     {final StringBuilder s = new StringBuilder();
      s.append("Field(line="+line);
      s.append(", indent="  +indent);
      s.append(", name="    +name);
      s.append(", cmd="     +cmd);
      if (rep    != null) s.append(", rep="   +rep);
      if (parent != null) s.append(", parent="+getParent().name);
      return ""+s+")";
     }

    public String toString()                                                    // Dump the memory associated with a field
     {final StringBuilder s = new StringBuilder();
      s.append(name+": value="   +value);
      if (memory != null)
       {final int d = memory.length;
        if (d > 0)
         {for (int i = 0; i < d; i++)
           {s.append(", "+i+"="+getIntFromBits(memory[i]));
           }
         }
       }
      return ""+s;
     }

    Integer rep()                                                               // The width of the element in bits or the array dimension
     {if (bit) return 1;
      return rep;
      //if (var || array) return rep;
      //stop("Only array, bit or var can have a rep count, not:", cmd, "with name:", name);
      //return null;
     }

    int dims() {return dimensions.size();}                                      // The number if containing arrays - if there are none the field has no backing memory only its cirrent value

    int dimProduct()                                                            // The product of the dimensions
     {if (dims() == 0) return 1;                                                // No dimensions
      if (dims() == 1) return dimensions.firstElement().rep;                    // One dimension
      int n = 1;
      for(Field d : dimensions) n *= d.rep;                                     // Multiple dimensions
      return n;
     }

    String dimensions()                                                         // The dimensions as a string
     {final Stack<String> s = new Stack<>();
      if (dims() == 0) return "";                                               // No dimensions
      if (dims() == 1) return ""+dimensions.firstElement().rep;                 // One dimension
      for(Field d : dimensions) s.push(""+d.rep);                               // Several dimensions
      int n = 1;
      for(Field d : dimensions) n *= d.rep;
      return joinStrings(s, "*")+" = "+n;
     }

    String children()                                                           // The names of the children as a string
     {final Stack<String> s = new Stack<>();
      for(Field c : children) s.push(""+c.name);
      return joinStrings(s, ", ");
     }

    void allocateMemory()                                                       // Allocate memory for this field if is a var or bit and part of an array. Otherwise it is just temporary
     {if (rep() == null || dims() == 0) return;                                 // Only vars and bits are allocated memeory and even then only if they are part of an array
      final int N = dimProduct();
      memory = new BitSet[N];                                                   // Array of bit sets.  This is inefficient for representing bit fields in Java but not a problem in Verilog and, ultimately, it is the Verilog that counts.
      for (int i = 0; i < N; i++) memory[i] = new BitSet(rep());                // Memory at each index
     }

    Field checkVar()                                                            // Check that this is a bit or var field - a bit is a var containing just one bit
     {if (bit || var) return this;
      stop("Expected a bit or a var but got a:", cmd, "called", name);
      return null;
     }

    int getIntFromBits(BitSet b)                                                // Get the value of a bitset as an integer to the extent that the integer can accept
     {return b.length() == 0 ? 0 : (int) b.toLongArray()[0];                    // Relying on the fact that the Java is only ever test code unlike the Verilog.
     }

    void setBitsFromInt(BitSet b, int value)                                    // Set a bit set to as much of an integer as it can accept
     {final int l = min(rep(), Integer.SIZE-1);
      b.clear();                                                                // Zero the memory
      for (int i = 0; i < l; i++)                                               // Set each bit in the bitset if the corresponding bit in the value is set
       {if (((value >> i) & 1) != 0)
         {b.set(i);
         }
       }
     }

    int convolute(Field...j)                                                    // Convolute the dimensions of this field with the supplied top level vars acting as array indices to locat the index of an element in an array
     {int i = j[0].value;                                                       // Current value of var
      final int J = j.length;
      for (int c = 1; c < J; c++)                                               // Each dimension beyond the forst one contributes to the indes.  The first dimension determines the size but not the location of an element in the array
       {final int    d = dimensions.elementAt(c).rep();
        final Field  f = j[c];
        final int    v = f.value;
        final String m = "Index: "+v+"from: "+f.name+" is";
        if (v <  0) stop(m, "negative");                                        // Index out of range low
        if (v >= d) stop(m, "is greater than or equal to:", d);                 // Index out of range high
        i = i * d + v;                                                          // Move up one dimension
       }
      return i;
     }

//D2 Read                                                                       // Read values from memory

    void iRead(int index)                                                       // Create an instruction that loads the value of this field from the constant indexed element of the memory associated with this field
     {final Field f = checkVar();
      P.new Instruction()
       {void action() {f.value = f.getIntFromBits(memory[index]); P.pc++;}
       };
     }

    void iRead(Field...indices)                                                 // Create an instruction that loads the value of this field from the variable indexed element of the memory associated with this field
     {final Field f = checkVar();
      P.new Instruction()
       {void action()
         {final int index = convolute(indices);
          f.value = f.getIntFromBits(memory[index]);
          P.pc++;
         }
       };
     }

//D2 Write                                                                      // Write values into memory

    void iWrite(int value)                                                      // Create an instruction that sets the value of this field but does not modify the memory backing the field
     {final Field  f = checkVar();
      final BitSet b = new BitSet(f.rep());
      P.new Instruction()
       {void action()
         {f.setBitsFromInt(b, value);
          f.value = f.getIntFromBits(b);                                        // So the value matches what would actually be written into memory
          P.pc++;
         }
       };
     }

    void iWrite(int value, int index)                                           // Create an instruction that sets the value of this field and updates the constant indexed element of the memory associated with this field with the same value
     {final Field f = checkVar();
      P.new Instruction()
       {void action()
         {final BitSet b = f.memory[index];                                     // Bit set in memory holding value at this index
          f.setBitsFromInt(b, value);
          f.value = f.getIntFromBits(b);                                        // So the value matches what is actually in memory
          P.pc++;
         }
       };
     }

    void iWrite(Field source, Field...indices)                                  // Create an instruction that sets the value of this field and updates the variable indexed element of the memory associated with this field with the same value
     {final Field f = checkVar();
      P.new Instruction()
       {void action()
         {final int index = convolute(indices);
          final int value = source.value;
          final BitSet b  = f.memory[index];                                    // Bit set in memory holding value at this index
          f.setBitsFromInt(b, value);
          f.value = f.getIntFromBits(b);                                        // So the value matches what is actually in memory
          P.pc++;
         }
       };
     }

//D2 Instructions                                                               // Instructions that can be executed against memory

    void iMove(Field Source) {iAdd(Source);}                                    // Copy the source value to the target. To write ino backing mmeory as well call iWrite() as well

    void iDec()                                                                 // Decrement the value of this field
     {final Field f = checkVar();
      P.new Instruction()
       {void action() {f.value--; P.pc++;}
       };
     }

    void iInc()                                                                 // Increment the value of this field
     {final Field f = checkVar();
      P.new Instruction()
       {void action() {f.value++; P.pc++;}
       };
     }

    void iAdd(Field...Source)                                                   // Add the values of the source fields and store in the target. If no source fields are supplied trhe source is zeroed.  IF one field is supplied the source value is copied in to the target.  Otherwise the source fields are summed and the result stored in the target value
     {final Field t = checkVar();
      switch(Source.length)
       {case 0: P.new Instruction()
         {void action() {t.value = 0; P.pc++;}
         };
        break;
        case 1: P.new Instruction()
         {void action() {t.value = Source[0].value; P.pc++;}
         };
        break;
        case 2: P.new Instruction()
         {void action() {t.value = Source[0].value + Source[1].value; P.pc++;}
         };
        break;
        default: P.new Instruction()
         {void action()
           {t.value = 0; for(Field s : Source) t.value += s.value; P.pc++;

           }
         };
        break;
       };
     }
   }

//D2 Programs                                                                   // Define a program to manipulate the layout

  class Program                                                                 // Program definition
   {final Stack<Instruction>      code = new Stack<>();                         // The code that manipulates the fields
    final Stack<Label>          labels = new Stack<>();                         // Labels into the code
    final int                 maxSteps = 200;                                   // Maximum number of steps to execute
    int                             pc = 0;                                     // The index of the next instruction to be executed
    String                          rc = null;                                  // The result of executing the program.  If null then no problems were detected
    boolean   supressErrorMessagePrint = false;                                 // Do not print error message from iStop() during testing if true

//D2 Conditional Programming                                                    // Conditional changes to the flow of execution of a program modifying the memory layout

    class Label                                                                 // Labels label instructions in the code
     {int offset;                                                               // Offset in code of this label
      Label()    {set();}                                                       // Initially at the current end of the code
      void set() {offset = P.code.size(); P.labels.push(this);}                 // Track all labels created
     }

    void Goto(Label label)                                                      // Goto a label unconditionally
     {P.new Instruction()
       {void   action()
         {pc = label.offset;
         }
       };
     }

    void GoNotZero(Label label, Field condition)                                // Go to a specified label if the value of a field is not zero
     {P.new Instruction()
       {void action()
         {if (condition.value > 0) pc = label.offset; else P.pc++;
         }
       };
     }

    void GoZero(Label label, Field condition)                                   // Go to a specified label if the value of a field is zero
     {P.new Instruction()
       {void action()
         {if (condition.value == 0) pc = label.offset; else P.pc++;
         }
       };
     }

    abstract class If                                                           // An if statement
     {final Label Else = new Label(), End = new Label();                        // Components of an if statement

      If (Field Condition)                                                      // If a condition
       {GoZero(Else, Condition);                                                // Branch on the current value of condition
        Then();
        Goto(End);
        Else.set();
        Else();
        End.set();
       }
      void Then() {}
      void Else() {}
     }

    abstract class Block                                                        // A block that can be continued or exited
     {final Label start = new Label(), end = new Label();                       // Labels at start and end of block to facilitate continuing or exiting
      Block()
       {code();
        end.set();
       }
      abstract void code();
     }

//D1 Execute                                                                    // Execute instructions in a program to modify the memory described by the layout

    abstract class Instruction                                                  // Instructions used to manipulate the fields
     {final String traceBack = traceBack();                                     // Line at which this instruction was created

      Instruction() {P.code.push(this);}                                        // Add the instruction to the code

      abstract void action();                                                   // Override this method to specify what the instruction does
     }

    void clearProgram() {code.clear();}                                         // Clear the program code

    void runProgram()                                                           // Run the program code
     {rc = null;                                                                // Clear the return code
      int  i = 0;
      for (i = pc = 0; pc < code.size() && i < maxSteps; ++i)
       {code.elementAt(pc).action();
       }
      if (pc < code.size()) stop("Out of steps after :", i);
     }

    void stopProgram(final String message)                                      // Halt program execution with a message
     {rc = message;                                                             // Use the message as a result code
      if (!supressErrorMessagePrint) say(message);                              // Write the supplied message
      pc = code.size();                                                         // Halt the program
     }

    void iStop(final String message)                                            // Halt program execution with a message
     {P.new Instruction()
       {void action() {stopProgram(message);}
       };
     }
   }

  void clearProgram() {P.clearProgram();}                                       // Clear the program code
  void runProgram()   {P.runProgram();}                                         // Run the program code
  void stopProgram(final String message) {P.stopProgram(message);}              // Halt program execution with a message

//D1 Parsing                                                                    // Parse the source description of a memory layout

  Field locateFieldByName(String name) {return names.get(name);}                // Locate a field by name

  void allocateMemory()                                                         // Allocate memory for all fields that actually use memory
   {for(Field f: fields) if (f.spacer && f.dims() > 0) f.allocateMemory();
   }

  Integer locatePreviousElement(int indent, String location)                    // The index of the previous field ignoring the dependencies of the previous field
   {if (indent >= fields.lastElement().indent + 2) return fields.size()-1;      // Deeper indentation acceptable so the previous field is the last one parsed
    if (indent >  fields.lastElement().indent)
      stop("Minimum indentation of 2 spaces required", location);
    for(int i = fields.size(); i > 0; --i)                                      // Each prior layout
     {final int in = fields.elementAt(i-1).indent;                              // Indentation of prior layout
      if (in == indent) return i-1;                                             // Matched indentation of a prior layout
     }
    stop("Indentation does not match that of any previous line", location);     // No matching previous layout
    return null;
   }

  private void parseFields()                                                    // Parse the layout description into fields
   {final String[]lines = source.split("\n");                                   // Split source into lines

    for(int l = 0; l < lines.length; ++l)                                       // Lines
     {final String line   = lines[l];                                           // Each line
      if (line.matches("\\A\\s*#")) continue;                                   // Comment
      final String E      = "on line: "+(l + 1)+"\n"+line;                      // Error line, 1 based
      final int indent    = line.length() - line.stripLeading().length();       // Indentation depth
      final String[]words = line.trim().split("\\s+");                          // Words in line
      if (words.length < 2) stop("Not enough operands", E);                     // Need at least a name and a type
      final String name   = words[0];                                           // First word is the name
      final String cmd    = words[1].toLowerCase();                             // Command is the second word
      if (!cmd.matches("array|bit|var"))                                        // Check command
        stop("Expected one of: array, bit or var", E);
      final boolean bit   = cmd.equals("bit");                                  // Need at least a name and a type
      final int eo = bit ? 2 : 3, ao = words.length;                            // Number of operands expected and actually found
      if (ao != eo) stop("Not enough operands", E, "expected:", eo, "found:", ao);
      final String Rep = ao > 2 ? words[2] : null;
      if (Rep != null && !Rep.matches("\\A\\d+\\Z"))                            // Repetition if present must be numeric
        stop("Repetition:", Rep, "should be an integer", E);
      final int rep = Rep == null ? 1 : Integer.parseInt(Rep);                  // Repetition or width

      if (names.containsKey(name)) stop("Duplicate name:", name, E);            // Require names to be unique

      if (fields.size() == 0)                                                   // First line parsed
       {new Field(l, indent, name, cmd, rep, null);                             // Details of the first line parsed
        continue;
       }

      final Integer prev = locatePreviousElement(indent, E);                    // Previous field

      final Field p = fields.elementAt(prev);                                   // Previous field
      if (indent > p.indent)                                                    // Indenting further
       {final Field f = new Field(l, indent, name, cmd, rep, prev);             // Details of the fields of this line
        p.children.push(f);                                                     // The children associated with each parent
        continue;
       }

      final Field F = new Field(l, indent, name, cmd, rep, p.parent);           // Indenting at the same level as a previous field
      if (p.hasParent()) p.getParent().children.push(F);                        // The children associated with each parent. I f a field hass no parent then it is a top field and can be used for indexing fields under arrays
     }

    for(Field p : fields)                                                       // Dimensions of each spacer
     {if (!p.spacer) continue;                                                  // Items which have dimensions
      final Stack<Field>d = p.dimensions;
      for(Field q = p.getParent(); q != null; q = q.getParent())                // Up through parents
       {if (!q.array) continue;                                                 // Find arrays
        d.insertElementAt(q, 0);                                                // Unshift containing arrays
       }
     }

    for(Field p : fields)                                                        // Check children counts
     {final int    line = p.line + 1;
      final String name = p.name;
      final String cmd  = p.cmd;
      final int    k    = p.children.size();
      final String loc = name+" from line: "+line;
      if (p.bit    && k >  0) stop("Bit:",    loc, "cannot have child layouts");
      if (p.var    && k >  0) stop("Var:",    loc, "cannot have child layouts");
      if (p.array  && k <  1) stop("Array:",  loc, "must have exactly one or more child layouts");
     }
   }

//D2 Printing                                                                   // Print the results if parsing a memory layout

  public String toString()                                                      // Print the fields of the input lines
   {final Stack<StringBuilder> S = new Stack<>();                               // Print of fields
    final String t = String.format
       ("%4s  %11s  %-32s  %-8s  %-8s  %8s  %8s  %-32s  %-32s",
"#",
"Indent",
"Name",
"Value___",
"Command",
"Rep",
"Parent",
"Children",
"Dimension");
    S.push(new StringBuilder(t));

    for (int i = 0; i < fields.size(); i++)
     {final Field  f = fields.elementAt(i);
      final String c = f.cmd;
      final String C = f.children();
      final String D = f.dimensions();
      final int    d = f.indent;
      final String n = " ".repeat(d)+f.name;
      final String p = f.parent != null ? f.getParent().name : "";
      final String r = f.rep    != null ? String.format("%8d", f.rep)              : "";
      final String v = String.format("%8d", f.value);
      final String s = String.format
       ("%4d  %11d  %-32s  %-8s  %-8s  %8s  %8s  %-32s  %-32s",
        i,    d,    n,    v, c,     r,   p,   C,     D);
      S.push(new StringBuilder(s));
     }
    squeezeVerticalSpaces(S);
    return joinStringBuilders(S, "\n")+"\n";
   }

//D1 Tests                                                                      // Test memory layouts

  protected static void test_parse()
   {
//    Layout l = new Layout("""
//A array 2
//  S struct
//    a var 4
//    b bit
//    u union
//      B array 4
//        S1 struct
//          a1 bit
//          b1 var 2
//      C array 2
//        S2 struct
//          a2 bit
//          b2 var 5
//""");

    Layout l = new Layout("""
A array 2
  a var 4
  b bit
  B array 4
    a1 bit
    b1 var 2
    C array 2
      a2 bit
      b2 var 5
""");

    //stop(l);
    ok(l, """
  #  Indent  Name      Value___  Command  Rep  Parent  Children   Dimension
  0       0  A                0  array      2          a, b, B
  1       2    a              0  var        4       A             2
  2       2    b              0  bit        1       A             2
  3       2    B              0  array      4       A  a1, b1, C
  4       4      a1           0  bit        1       B             2*4 = 8
  5       4      b1           0  var        2       B             2*4 = 8
  6       4      C            0  array      2       B  a2, b2
  7       6        a2         0  bit        1       C             2*4*2 = 16
  8       6        b2         0  var        5       C             2*4*2 = 16
""");

    Field b1 = l.locateFieldByName("b1");
    ok(b1.dimProduct(), 8);
    l.allocateMemory();
   }

  protected static void test_parse_top()
//   {Layout l = new Layout("""
//a var 4
//b bit
//S struct
//  a1 bit
//  b1 var 5
//c var 2
//""");
   {Layout l = new Layout("""
a var 4
b bit
A array 1
  a1 bit
  b1 var 5
c var 2
""");

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            0  var        4
  1       0  b            0  bit        1
  2       0  A            0  array      1          a1, b1
  3       2    a1         0  bit        1       A            1
  4       2    b1         0  var        5       A            1
  5       0  c            0  var        2
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field c = l.locateFieldByName("c");
    ok(a.rep(), 4);
    ok(b.rep(), 1);
    ok(c.rep(), 2);
   }

  protected static void test_vars()
   {Layout l = new Layout("""
i var 4
A array 2
  a var 4
v var 4
""");

    Field i = l.locateFieldByName("i");
    Field a = l.locateFieldByName("a");
    Field v = l.locateFieldByName("v");

    i.iWrite(0); v.iWrite(2); a.iWrite(v, i);
    i.iWrite(1); v.iWrite(4); a.iWrite(v, i);

    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  i            1  var        4
  1       0  A            0  array      2          a
  2       2    a          4  var        4       A            2
  3       0  v            4  var        4
""");

    l.clearProgram(); i.iWrite(0); a.iRead(i); l.runProgram();
    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  i            0  var        4
  1       0  A            0  array      2          a
  2       2    a          2  var        4       A            2
  3       0  v            4  var        4
""");

    l.clearProgram();  i.iWrite(1); a.iRead(i); l.runProgram();
    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  i            1  var        4
  1       0  A            0  array      2          a
  2       2    a          4  var        4       A            2
  3       0  v            4  var        4
""");
   }

  protected static void test_array()
   {Layout l = new Layout("""
i var 4
j var 4
A array 2
  B array 3
    b var 4
v var 4
""");

    Field i = l.locateFieldByName("i");
    Field j = l.locateFieldByName("j");
    Field A = l.locateFieldByName("A");
    Field a = l.locateFieldByName("a");
    Field B = l.locateFieldByName("B");
    Field b = l.locateFieldByName("b");
    Field v = l.locateFieldByName("v");

    l.clearProgram();
    for   (int x = 0; x < A.rep; x++)
     {for (int y = 0; y < B.rep; y++)
       {i.iWrite(x); j.iWrite(y); b.iWrite(2 * x + y); b.iWrite(b, i, j);
       }
     }
    l.runProgram();

    for   (int y = 0; y < B.rep; y++)
     {for (int x = 0; x < A.rep; x++)
       {l.clearProgram();
        i.iWrite(x); j.iWrite(y); b.iRead(i, j);
        l.runProgram();
        ok(b.value, 2 * x + y);
       }
     }
    ok(b, "b: value=4, 0=0, 1=1, 2=2, 3=2, 4=3, 5=4");
   }

  protected static void test_add()
   {Layout l = new Layout("""
a var 4
b var 4
c var 4
d var 4
e var 4
f var 4
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field c = l.locateFieldByName("c");
    Field d = l.locateFieldByName("d");
    Field e = l.locateFieldByName("e");
    Field f = l.locateFieldByName("f");

    a.iWrite(2); a.iDec();
    b.iWrite(1); b.iInc();
    c.iAdd();
    d.iAdd(b);
    e.iAdd(a, b);
    f.iAdd(a, b, c, d, e);
    l.runProgram();
    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            1  var        4
  1       0  b            2  var        4
  2       0  c            0  var        4
  3       0  d            2  var        4
  4       0  e            3  var        4
  5       0  f            8  var        4
""");
   }

  protected static void test_if()
   {Layout l = new Layout("""
a var 4
b var 4
t var 4
e var 4
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field t = l.locateFieldByName("t");
    Field e = l.locateFieldByName("e");

    a.iWrite(1);
    l.P.new If(a)
     {void Then() {t.iWrite(2);}
      void Else() {e.iWrite(3);}
     };
    l.P.new If(b)
     {void Then() {t.iWrite(4);}
      void Else() {e.iWrite(5);}
     };
    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            1  var        4
  1       0  b            0  var        4
  2       0  t            2  var        4
  3       0  e            5  var        4
""");
   }

  protected static void test_block()
   {Layout l = new Layout("""
a var 4
b var 4
c var 4
d var 4
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field c = l.locateFieldByName("c");
    Field d = l.locateFieldByName("d");

    a.iWrite(1);
    l.P.new Block()
     {void code()
       {c.iWrite(1);
        l.P.GoNotZero(end, a);
        d.iWrite(2);
       };
     };
    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            1  var        4
  1       0  b            0  var        4
  2       0  c            1  var        4
  3       0  d            0  var        4
""");
   }

  protected static void test_stop()
   {Layout l = new Layout("""
a var 4
""");

    Field a = l.locateFieldByName("a");
    l.P.supressErrorMessagePrint = true;
    l.P.iStop("Stopped");
    a.iWrite(1);
    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            0  var        4
""");

    ok(l.P.rc.equals("Stopped"));
   }

  protected static void oldTests()                                              // Tests thought to be in good shape
   {test_parse();
    test_parse_top();
    test_vars();
    test_array();
    test_add();
    test_if();
    test_block();
    test_stop();
   }

  protected static void newTests()                                              // Tests being worked on
   {oldTests();
    test_stop();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      if (github_actions)                                                       // Coverage analysis
       {coverageAnalysis(12, "Test.java");                                      // Used for printing
       }
      testSummary();                                                            // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
