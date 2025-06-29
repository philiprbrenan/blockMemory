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
    parseFields(source);
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
      array       = cmd.lowerCase().equals("array");
      bit         = cmd.lowerCase().equals("bit");
      struct      = cmd.lowerCase().equals("struct");
      union       = cmd.lowerCase().equals("union");
      var         = cmd.lowerCase().equals("var");
      spacer      = bit || var;
      fields.push(this);
     }
   }

  Integer locatePreviousElement(int indent, String location)                    // The index of the previous field ignoring the dependencies of the previous field
   {if (indent >= fields.lastElement().indent + 2) return fields.length()-1;    // Deeper indentation acceptable so the previous field is the last one parsed
    if (indent >  fields.lastElement().indent)
      stop("Minimum indentation of 2 spaces required", location);
    for(int i = fields.size(); i > 0; ++i)                                      // Each prior layout
     {final int in = fields.elementAt(i).indent;                                // Indentation of prior layout
      if (in == indent) return i;                                               // Matched indentation of a prior layout
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
      final String cmd    = $words[1].lowerCase();                              // Command is the second word
      final String rep    = words.length == 2 ? null : $words[2];               // Repetition or width

      if (names.has(name)) stop("Duplicate name:", name, E);                    // Require names to be unique
      if (!cmd.matches("array|bit|struct|var|union"))                           // Check command
        stop("Expected one of: array, bit, struct, union, var", $E);
      if (!(rep != null && rep.matches("\\A\\d+\\Z")))                          // Repetition if present must be numeric
        stop("Repetition:", rep, "should be an integer", $E);

      if (fields.length == 0)                                                   // First line parsed
       {new Field(l, indent, name, cmd, $rep, null);                            // Details of the first line parsed
        continue;
       }

      final Integer prev = locatePreviousElement(l, E);                         // Previous field

      final Field p = fields.elementAt(prev);                                   // Previous field
      if (indent > p.indent)                                                    // Indenting further
       {final Field f = new Field(l, indent, name, cmd, rep, p);                // Details of the fields of this line
        p.children.push(f);                                                     // The children associated with each parent
        continue;
       }

      final Field prevParent = p.parent;                                        // This field has the same parent as the previous field
      final Field F = new Field(l, indent, name, cmd, rep, prevParent);         // Details of the fields
      p.children.push(F);                                                       // The children associated with each parent
     }

    for(Field p : fields)                                                       // Dimensions of each spacer
     {if (!p.spacer) continue;                                                  // Items which have dimensions
      final Stack<Field>d = p.dimensions;
      for(Field q = p.parent; q != null; q = q.parent)                          // Up through parents
       {if (!q.array) continue;                                                 // Find arrays
        d.insertElementAt(q, 0);                                                // unshift containing arrays
       }
     }

    for(Field p : fields)                                                        // Check children counts
     {final String line = p.line + 1;
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
   {final Stack<StringBuilder> S = new Stack<>();                                // Print of fields
    final String t = String.format
       ("%-4s  %-11s  %-32s  %-8s  %-8s  %-8s  %-8s  %-8s",
"Indentation",
"Name",
"Command",
"Repetition",
"Parent",
"Dimensions",
"Children");

    S.push(t);

    for (int i = 0; i < fields.size(); i++)
     {final Field  f = fields.elementAt(i);
      final String s = String.format
       ("%4d  %11d  %32s  %8s  %8d  %8d",
        i, f.indent, f.name, f.cmd, f.rep, fields.indexOf(f.parent));
      S.push(s);
     }
    return squeezeVerticalSpaces(S);
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

    say(l);
   }

  protected static void oldTests()                                              // Tests thought to be in good shape
   {}

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
