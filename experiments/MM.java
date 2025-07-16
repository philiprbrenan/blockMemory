//------------------------------------------------------------------------------
// Multimemory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

/* Each instruction becomes one Verilog always block.  Variables can only ever
be written to by one always block. Each block can read any variable in any
block.  If we have multiple readers, they must indicate  which memory location
they want read and provide an area into which the result can be written by the
memory controller. */

class MM extends Test                                                           // Manipulate a btree using static methods and memory
 {final Stack<Integer> memory  = new Stack<>();                                 // The memory
  final Stack<Reader>  readers = new Stack<>();                                 // Processes that want to read from memory

  class Reader                                                                  // A reader requests the value stored at a specified memory index and provides both an output location for the result and the necessary information to perform a handshake with the memory controller.
   {int requestedAt;                                                            // Step at which last request was made                                                                              //
    int index;                                                                  // Memory location requested
    int resultAt;                                                               // Step at which last response was made
    int value;                                                                  // Value held in memory at the indexed location

    Reader(int RequestedAt, int Index)
     {index       = Index;
      requestedAt = RequestedAt;
      readers.push(this);
     }

    public String toString()
     {return ""+index+"@"+value;
     }
   }

  void read(int pc)                                                             // Read memory
   {final Reader r = readers.elementAt(pc % readers.size());
    if (r.requestedAt >= pc && r.resultAt <= r.requestedAt)                     // Found a request that is still pending
     {r.value = memory.elementAt(r.index);
say("AAAA", pc, memory);
      r.resultAt = pc+2;
     }
   }

  protected static void test_read()                                             // Test read memory
   {final int N = 4;
    final MM  m = new MM();
    for (int  i = 0; i < N; i++) m.memory.push(i+1);
    final Reader a = m.new Reader(10, 1);
    final Reader b = m.new Reader(20, 2);
    for (int i = 0; i < 30; i++) m.read(i);
    ok(m.readers, "[1@2, 2@3]");
   }

  protected static void oldTests()                                              // Tests thought to be in good shape
   {test_read();
   }

  protected static void newTests()                                              // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      if (github_actions)                                                       // Coverage analysis
       {coverageAnalysis(12, "yyy.java");                                       // Used for printing
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
