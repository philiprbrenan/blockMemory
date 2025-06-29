//------------------------------------------------------------------------------
// Layout and manipulate a relocatable block of memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Layout extends Test                                                       // Manipulate a btree using static methods and memory
 {final String                source;                                           // The source string we are going to fields
  final Stack<Field>          fields = new Stack<>();                           // Each field parsed from the input string
  final TreeMap<String,Field> names  = new TreeMap<>();                         // Names of each field

  Layout(String Source)                                                         // A source description of the layout to be parsed into fieldsd
   {source = Source;
    parseFields();
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
     }

    Field getParent()                                                           // Paent of a field
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

    String dimensions()                                                         // The dimensions as a string
     {final Stack<String> s = new Stack<>();
      for(Field d : dimensions) s.push(""+d.rep);
      return joinStrings(s, ", ");
     }

    String children()                                                           // The name sof the children as a string
     {final Stack<String> s = new Stack<>();
      for(Field c : children) s.push(""+c.name);
      return joinStrings(s, ", ");
     }
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
      p.getParent().children.push(F);                                           // The children associated with each parent
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
       ("%4s  %11s  %-32s  %-8s  %8s  %8s  %-32s  %-32s",
"#",
"Indent",
"Name",
"Command",
"Rep",
"Parent",
"Size",
"Children");
    S.push(new StringBuilder(t));

    for (int i = 0; i < fields.size(); i++)
     {final Field  f = fields.elementAt(i);
      final String c = f.cmd;
      final String C = f.children();
      final String D = f.dimensions();
      final int    d = f.indent;
      final String n = " ".repeat(d)+f.name;
      final String p = f.parent != null ? String.format("%8d", f.getParent().line) : "";
      final String r = f.rep    != null ? String.format("%8d", f.rep)              : "";
      final String s = String.format
       ("%4d  %11d  %-32s  %-8s  %8s  %8s  %-32s  %-32s",
        i,    d,    n,    c,   r,   p, D, C);
      S.push(new StringBuilder(s));
     }
    squeezeVerticalSpaces(S);
    return joinStringBuilders(S, "\n")+"\n";
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
   #  Indent  Name          Command  Rep  Parent  Size  Children
   0       0  A             array      2                S
   1       2    S           struct             0        a, b, u
   2       4      a         var        4       1  2
   3       4      b         bit                1  2
   4       4      u         union              1        B, C
   5       6        B       array      4       4        S1
   6       8          S1    struct             5        a1, b1
   7      10            a1  bit                6  2, 4
   8      10            b1  var        2       6  2, 4
   9       6        C       array      2       4        S2
  10       8          S2    struct             9        a2, b2
  11      10            a2  bit               10  2, 2
  12      10            b2  var        5      10  2, 2
""");
   }

  protected static void oldTests()                                              // Tests thought to be in good shape
   {test_parse();
   }

  protected static void newTests()                                              // Tests being worked on
   {//oldTests();
    test_parse();
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
