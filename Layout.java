//------------------------------------------------------------------------------
// Layout and manipulate a relocatable block of memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Layout extends Test                                                       // Manipulate a btree using static methods and memory
 {final String                source;                                           // The source string we are going to parse into fields  describing the memory layout
  final Stack<Field>          fields = new Stack<>();                           // Each field parsed from the input string
  final TreeMap<String,Field>  names = new TreeMap<>();                         // Names of each field
  final Stack<Instruction>      code = new Stack<>();                           // The code that manipulates the fields

  Layout(String Source)                                                         // A source description of the layout to be parsed into fieldsd
   {source = Source;
    parseFields();
    allocateMemory();                                                            // Allocate memory for all fields
   }

  class Field                                                                   // The fields in the layout
   {final int     line;                                                         // Line at which the layout was parsed
    final int     indent;                                                       // Indentation
    final String  name;                                                         // Name
    final String  cmd;                                                          // Command
    final Integer rep;                                                          // Optional repetition
    final Integer parent;                                                       // Parent
    final Stack<Field>dimensions = new Stack<>();                               // Dimensions of field
    final Stack<Field>children   = new Stack<>();                               // Children of an item
    final boolean spacer, array, bit, struct, var, union;                       // Classification - a spacer is a bit or a var as they actually take up space - or a character in "The Caves of Steel"
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
      struct      = cmd.equals("struct");
      union       = cmd.equals("union");
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

    public String toString()                                                    // Dump a field
     {final StringBuilder s = new StringBuilder();
      s.append("Field(line="+line);
      s.append(", indent="  +indent);
      s.append(", name="    +name);
      s.append(", cmd="     +cmd);
      if (rep    != null) s.append(", rep="   +rep);
      if (parent != null) s.append(", parent="+getParent().name);
      return ""+s+")";
     }

    Integer rep()                                                               // The width of the element in bits or the array dimension
     {if (bit) return 1;
      if (var || array) return rep;
      stop("Only array, bit or var can have a rep count, not:", cmd, "with name:", name);
      return null;
     }

    int dims() {return dimensions.size();}                                      // The number if containing arrays - if there are nonw the field has no backing memory only its cirrent value

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

    void iRead(int index)                                                       // Create an instruction that loads the value of this field from the indexed  element of the memory associated with this field
     {final Field f = checkVar();
      new Instruction()
       {void action() {f.value = f.getIntFromBits(memory[index]);}
       };
     }

    void iRead(Field...indices)                                                 // Create an instruction that loads the value of this field from the indexed  element of the memory associated with this field
     {final Field f = checkVar();
      new Instruction()
       {void action()
         {final int index = convolute(indices);
          f.value = f.getIntFromBits(memory[index]);
         }
       };
     }

    void iWrite(int value)                                                      // Create an instruction that sets the value of this field butthe does not modify the memory backing the field
     {final Field  f = checkVar();
      final BitSet b = new BitSet(f.rep());
      new Instruction()
       {void action()
         {f.setBitsFromInt(b, value);
          f.value = f.getIntFromBits(b);                                        // So the value matches what would actually be written into memory
         }
       };
     }

    void iWrite(int value, int index)                                           // Create an instruction that sets the value of this field and updates the indexed element of the memory associated with this field with the same value
     {final Field f = checkVar();
      new Instruction()
       {void action()
         {final BitSet b = f.memory[index];                                     // Bit set in memory holding value at this index
          f.setBitsFromInt(b, value);
          f.value = f.getIntFromBits(b);                                        // So the value matches what is actually in memory
         }
       };
     }

    void iWrite(Field source, Field...indices)                                  // Create an instruction that sets the value of this field and updates the indexed element of the memory associated with this field with the same value
     {final Field f = checkVar();
      new Instruction()
       {void action()
         {final int index = convolute(indices);
          final int value = source.value;
          final BitSet b  = f.memory[index];                                    // Bit set in memory holding value at this index
          f.setBitsFromInt(b, value);
          f.value = f.getIntFromBits(b);                                        // So the value matches what is actually in memory
         }
       };
     }
   }

  Field locateFieldByName(String name) {return names.get(name);}                // Locate a field by name

  abstract class Instruction                                                    // Instructions used to manipulate the fields
   {final String traceBack = traceBack();                                       // Line at which this instruction was created

    Instruction() {code.push(this);}                                            // Add the instruction to the code

    abstract void action();                                                     // Override this method to specify what the instruction does
   }

  void allocateMemory()                                                         // Allocate memory for all fields that actually use memory
   {for(Field f: fields) if (f.spacer) f.allocateMemory();
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
      final String E      = "on line: "+(l + 1)+"\n"+line;                      // Error line, 1 based
      final int indent    = line.length() - line.stripLeading().length();       // Indentation depth
      final String[]words = line.trim().split("\\s+");                          // Words in line
      if (words.length < 2) stop("Not enough operands", E);                     // Need at least a name and a type
      final String name   = words[0];                                           // First word is the name
      final String cmd    = words[1].toLowerCase();                             // Command is the second word
      final String Rep    = words.length == 2 ? null : words[2];                // Repetition or width as string

      if (names.containsKey(name)) stop("Duplicate name:", name, E);            // Require names to be unique
      if (!cmd.matches("array|bit|struct|var|union"))                           // Check command
        stop("Expected one of: array, bit, struct, union, var", E);
      if (Rep != null && !Rep.matches("\\A\\d+\\Z"))                            // Repetition if present must be numeric
        stop("Repetition:", Rep, "should be an integer", E);

      final Integer rep   = Rep == null ? null : Integer.parseInt(Rep);         // Repetition or width as integer

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
      if (p.array  && k != 1) stop("Array:",  loc, "must have exactly one child layout");
      if (p.struct && k < 2)  stop("Struct:", loc, "must have two or more child layouts");
      if (p.union  && k < 2)  stop("Union:",  loc, "must have two or more child layouts");
     }
   }

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

  void clearProgram() {code.clear();}                                           // Clear the program code

  void runProgram()                                                             // Run the program code
   {for (int i = 0; i < code.size(); i++)
     {code.elementAt(i).action();
     }
   }

  protected static void test_parse()
   {Layout l = new Layout("""
A array 2
  S struct
    a var 4
    b bit
    u union
      B array 4
        S1 struct
          a1 bit
          b1 var 2
      C array 2
        S2 struct
          a2 bit
          b2 var 5
""");

    //stop(l);
    ok(l, """
   #  Indent  Name          Value___  Command  Rep  Parent  Children  Dimension
   0       0  A                    0  array      2          S
   1       2    S                  0  struct             A  a, b, u
   2       4      a                0  var        4       S            2
   3       4      b                0  bit                S            2
   4       4      u                0  union              S  B, C
   5       6        B              0  array      4       u  S1
   6       8          S1           0  struct             B  a1, b1
   7      10            a1         0  bit               S1            2*4 = 8
   8      10            b1         0  var        2      S1            2*4 = 8
   9       6        C              0  array      2       u  S2
  10       8          S2           0  struct             C  a2, b2
  11      10            a2         0  bit               S2            2*2 = 4
  12      10            b2         0  var        5      S2            2*2 = 4
""");

    Field b1 = l.locateFieldByName("b1");
    ok(b1.dimProduct(), 8);
    l.allocateMemory();
   }

  protected static void test_parse_top()
   {Layout l = new Layout("""
a var 4
b bit
S struct
  a1 bit
  b1 var 5
c var 2
""");

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            0  var        4
  1       0  b            0  bit
  2       0  S            0  struct                a1, b1
  3       2    a1         0  bit                S
  4       2    b1         0  var        5       S
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
   }

  protected static void oldTests()                                              // Tests thought to be in good shape
   {test_parse();
    test_parse_top();
    test_vars();
    test_array();
   }

  protected static void newTests()                                              // Tests being worked on
   {oldTests();
    test_array();
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
