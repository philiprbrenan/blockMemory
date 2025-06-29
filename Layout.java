//------------------------------------------------------------------------------
// Layout and manipulate a relocatable block of memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Layout extends Test                                                       // Manipulate a btree using static methods and memory
 {final String source;                                                          // The source string we are going to parse
  final TreeMap<String,Field> names = new TreeMap<>();                          // Names of each field

  class Field                                                                   // The fields in the layout
   {final int    line;                                                          // Line at which the layout was parsed
    final int    indent;                                                        // Indentation
    final String name;                                                          // Name
    final String cmd;                                                           // Command
    final int    rep;                                                           // Optional repetition
    final int    parent;                                                        // Parent
    final Stack<Field>dims = new Stack<>();                                     // Dimensions of element
    final Stack<Field>kids = new Stack<>();                                     // Children of an item

    Field(int line, int indent, String name, String cmd, int rep, int parent)   // Constructor
     {this.line   = line;
      this.indent = indent;
      this.name   = name;
      this.cmd    = cmd;
      this.rep    = rep;
      this.parent = parent;
     }
   }
sub parseLayout($lines)                                                         // Parse the layout of description
 {my @lines = split /\n/, $lines;                                               // Split the lines

  my @parse;                                                                    // Parse of each line
  my %names;                                                                    // Check that each name is unique

  for my $l(keys @lines)                                                        // Lines
   {my $line   = $lines[$l];                                                    // Each line
    my $E      = "on line: ".($l + 1)."\n$line";                                // Error line, 1 based
    my $indent = length $line =~ s(\S.*\Z) ()r;                                 // Indentation depth
    my @words  = split /\s+/, substr $line, $indent;                            // Words on the line
    confess "Not enough operands $E" if @words < 2;                             // Need at least a name and a type
    my $name =    $words[0];                                                    // First word is the name
    my $cmd  = lc $words[1];                                                    // Command is the second word
    my $rep  = @words == 2 ? undef : $words[2];                                 // Repetition or width
    confess "Duplicate name: $name $E" if $names{$name}++;                      // Require names to be unique
    confess "Expected one of: array, bit, struct, union, var $E" unless $cmd =~ m(\A(array|bit|struct|union|var)\Z)is;
    confess "Repetition: $rep should be an integer $E" if defined($rep) and $rep !~ m(\A\d+\Z);

    if (!@parse)                                                                // First line parsed
     {push @parse, [$l, $indent, $name, $cmd, $rep, undef];                     // Details of the first line parsed
      next;
     }

    my $prev = sub                                                              // Previous element
     {return $#parse if $indent > $parse[-1][indent()] + 1;                     // Deeper indentation acceptable
      confess "Minimum indentation of 2 spaces required $E" if $indent > $parse[-1][indent()]; # Deeper indentation insufficient
      my $found;
      for my $i(reverse keys @parse)
       {my $ip = $parse[$i][indent()];                                          // Indentation of prior layout
        return $i if $parse[$i][indent()] == $indent;                           // Indentation of previous layout
       }
      confess "Indentation does not match that of any previous line $E" unless $found;
     }->();

    my $pi = $parse[$prev][indent()];                                           // Indentation of parent
    if ($indent > $pi)                                                          // Indenting further
     {push @parse, [$l, $indent, $name, $cmd, $rep, $#parse];                   // Details of the parse
      push $parse[$prev][kids()]->@*, $#parse;                                  // The children associated with each parent
      next;
     }

    my $prevParent = $parse[$prev][parent()];                                   // This item has the same parent as the previous item
    push @parse, [$l, $indent, $name, $cmd, $rep, $parse[$prev][parent()]];     // Details of the parse
    push $parse[$prevParent][kids()]->@*, $#parse;                              // The children associated with each parent
   }

  for my $p(keys @parse)                                                        // Dimensions
   {my @p = $parse[$p]->@*;                                                     // Each parsed line
    next unless $p[cmd()] =~ m(\A(bit|var)\Z);                                  // Items which have dimensions
    my @d;                                                                      // Dimensions of an item
    for(my $q = $p[parent()]; defined $q; $q = $parse[$q][parent()])
     {my @q = $parse[$q]->@*;
      next unless $q[cmd()] =~ m(\A(array)\Z);                                  // Arrays give dimensions to items
      push @d, $q;                                                              // Push repetitions
     }
    $parse[$p][dims()] = [reverse @d];                                          // Reverse the dimensions to get the outermost array first
   }

  for my $p(keys @parse)                                                        // Check children counts
   {my @p = $parse[$p]->@*;                                                     // Each parsed layout
    my $line = $p[line()] + 1;
    my $name = $p[name()];
    my $cmd  = $p[cmd()];
    my $k = $p[kids()];
    my @k = $k ? @$k : ();
    if ($cmd =~ m(\Abit\Z))                                                     // Bit
     {confess"Bit: $name from line: $line cannot have child layouts" if @k;
     }
    if ($cmd =~ m(\Avar\Z))                                                     // Variable
     {confess"Var: $name from line: $line cannot have child layouts" if @k;
     }
    if ($cmd =~ m(\array\Z))                                                    // Array
     {confess"Array: $name from line: $line must have exactly one child layouts" if @k != 1;
     }
    if ($cmd =~ m(\Astruct\Z))                                                  // Struct
     {confess"Struct: $name from line: $line must have two or more child layouts" if @k < 2;
     }
    if ($cmd =~ m(\Aunion\Z))                                                   // Union
     {confess"Union: $name from line: $line must have two or more child layouts" if @k < 2;
     }
   }

  return [@parse]                                                               // Return the parse
 }

sub printParse($parse)                                                          // Print the parse of the input lines
 {my @p = @$parse;
  for my $p(@p)
   {$$p[name()] = (" " x $$p[indent()]).$$p[name()];                            // Indent the names to show structure more effectively
    $$p[parent()] = sub                                                         // Parent
     {if (defined(my $d = $$p[parent()]))
       {return $d+1;
       }
      ""
     }->();
    $$p[dims()] = sub                                                           // Dimensions
     {if (my $d = $$p[dims()])
       {return join(", ", map {$_+1} @$d).".";
       }
      ""
     }->();
    $$p[kids()] = sub                                                           // Children
     {if (my $c = $$p[kids()])
       {return join(", ", map {$_+1} @$c).".";
       }
      ""
     }->();

    shift @$p;                                                                  // Remove the line number at the front as format table will add one automatically
   }

  formatTable \@p, <<END;
Indentation
Name
Command
Repetition
Parent
Dimensions
Children
END
 }



if (1)
 {my $lines = <<END;
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
END

  #say STDERR printParse parseLayout $lines; exit;
  is_deeply [split /\n/, printParse parseLayout $lines],
            [split /\n/, <<END];
    Indentation  Name          Command  Repetition  Parent  Dimensions  Children
 1            0  A             array             2                      2.
 2            2    S           struct                    1              3, 4, 5.
 3            4      a         var               4       2  1.
 4            4      b         bit                       2  1.
 5            4      u         union                     2              6, 10.
 6            6        B       array             4       5              7.
 7            8          S1    struct                    6              8, 9.
 8           10            a1  bit                       7  1, 6.
 9           10            b1  var               2       7  1, 6.
10            6        C       array             2       5              11.
11            8          S2    struct                   10              12, 13.
12           10            a2  bit                      11  1, 10.
13           10            b2  var               5      11  1, 10.
END
 }

done_testing;


  protected static void oldTests()                                              // Tests thought to be in good shape
   {}

  protected static void newTests()                                              // Tests being worked on
   {//oldTests();
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
