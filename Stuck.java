//------------------------------------------------------------------------------
// Stuck
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Stuck extends Test                                                        // A fixed size collection of key, data pairs
 {final int size;                                                               // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout L;                                                               // Layout of the stuck
  final Layout.Field stuckSize;                                                 // Current size of stuck up to the maximum size
  final Layout.Field stuckKeys;                                                 // Keys field
  final Layout.Field stuckData;                                                 // Data field

//D1 Construction                                                               // Create a stuck

  Stuck(int Size, int BitsPerKey, int BitsPerData)                              // Create the stuck. The memory layout containing the stuck
   {size        = Size;                                                         // The maximum number of entries in the stuck.
    bitsPerKey  = BitsPerKey;                                                   // The number of bits needed to define a key
    bitsPerData = BitsPerData;                                                  // The number of bits needed to define a data field
    L           = layout();
    stuckSize   = L.locateFieldByName("stuckSize");                             // Current size of stuck up to the maximum size
    stuckKeys   = L.locateFieldByName("stuckKeys");                             // Keys field
    stuckData   = L.locateFieldByName("stuckData");                             // Data field
   }

  Layout layout()                                                               // Layout describing stuck, Having the keys ordered sequqntiall makes it easy to compare them in parallel and find the first key greater than or equal to a search key, this being the most common operation.
   {return new Layout(String.format("""
stuckSize      var    %d
Stuck          array  %d
  stuck        struct
    stuckKeys  var    %d
    stuckData  var    %d
""", logTwo(size), size, bitsPerKey, bitsPerData));
   }

  static void test_parse()                                                      // Parse the stuck
   {final Stuck a = new Stuck(4, 4, 4);

    //stop(a.L);
    ok(a.L, """
  #  Indent  Name           Value___  Command  Rep  Parent  Children              Dimension
  0       0  stuckSize             0  var        2
  1       0  Stuck                 0  array      4          stuck
  2       2    stuck               0  struct         Stuck  stuckKeys, stuckData
  3       4      stuckKeys         0  var        4   stuck                        4
  4       4      stuckData         0  var        4   stuck                        4
""");
   }

//D1 Actions                                                                    // Actions on the stuck

  void push()                                                                   // Push a new key, data pair on the stack
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= size)
         {L.stopProgram("Cannot push to a full stuck");
          return;
         }
        stuckKeys.iWrite(stuckKeys, stuckSize);
        stuckData.iWrite(stuckData, stuckSize);
        stuckSize.iInc();
        L.P.pc++;
       }
     };
   }

  void pop()                                                                    // Pop a key, data pair from the stack
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot pop empty stack");
          return;
         }
        stuckSize.iDec();
        stuckKeys.iRead(stuckSize);
        stuckData.iRead(stuckSize);
        L.P.pc++;
       }
     };
   }

  void unshift()                                                                // Unshift a key, data pair into the stack after moving all the existing elements up one
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {push();
          return;
         }
        if (stuckSize.value >= size)
         {L.stopProgram("Cannot unshift to a full stuck");
          return;
         }

        for (int i = size; i > 1; --i)
         {stuckKeys.memory[i-1] = (BitSet)stuckKeys.memory[i-2].clone();
          stuckData.memory[i-1] = (BitSet)stuckData.memory[i-2].clone();
         }
        stuckKeys.setBitsFromInt(stuckKeys.memory[0], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[0], stuckData.value);

        stuckSize.iInc();
        L.P.pc++;
       }
     };
   }

  void shift()                                                                  // Shift a key, data pair from the stack after moving all the existing elements up one
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot shift en empty stuck");
          return;
         }

        stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[0]);
        stuckData.value = stuckData.getIntFromBits(stuckData.memory[0]);

        for (int i = 1; i < stuckSize.value; ++i)
         {stuckKeys.memory[i-1] = (BitSet)stuckKeys.memory[i].clone();
          stuckData.memory[i-1] = (BitSet)stuckData.memory[i].clone();
         }

        stuckSize.iDec();
        L.P.pc++;
       }
     };
   }

//D1 Tests                                                                      // Tests

  protected static Stuck testStuck()                                            // Create a test stuck
   {return new Stuck(4, 4, 4);
   }

  protected static Stuck test_push()
   {final Stuck  s = testStuck();

    Layout.Field k = s.stuckKeys;
    Layout.Field d = s.stuckData;

    s.L.clearProgram(); k.iWrite(1); d.iWrite(2); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(2); d.iWrite(4); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(3); d.iWrite(6); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(4); d.iWrite(8); s.push(); s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");

    s.L.P.supressErrorMessagePrint = true;
    s.L.clearProgram(); k.iWrite(5); d.iWrite(10); s.push(); s.L.runProgram();
    ok(s.L.P.rc, "Cannot push to a full stuck");

    return s;
   }

  protected static Stuck test_pop()
   {final Stuck  s = test_push();

    s.L.clearProgram();
    s.pop();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=2");
    ok(s.stuckKeys, "stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=6, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=1");
    ok(s.stuckKeys, "stuckKeys: value=2, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=4, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=0");
    ok(s.stuckKeys, "stuckKeys: value=1, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=2, 1=4, 2=6, 3=8");

    s.L.P.supressErrorMessagePrint = true;
    s.L.P.clearProgram(); s.pop(); s.L.runProgram();

    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot pop empty stack");

    return s;
   }

  protected static Stuck test_unshift()
   {final Stuck  s = test_push();

    Layout.Field k = s.stuckKeys;
    Layout.Field d = s.stuckData;

    s.L.clearProgram();
    s.pop();
    s.L.runProgram();
    s.L.clearProgram(); k.iWrite(9); d.iWrite(11); s.unshift(); s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=9, 0=9, 1=1, 2=2, 3=3");
    ok(s.stuckData, "stuckData: value=11, 0=11, 1=2, 2=4, 3=6");

    return s;
   }

  protected static Stuck test_shift()
   {final Stuck s = test_push();

    s.L.clearProgram();
    s.shift();
    s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=1, 0=2, 1=3, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=4, 1=6, 2=8, 3=8");

    return s;
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_parse();
    test_push();
    test_pop();
    test_unshift();
    test_shift();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      if (github_actions)                                                       // Coverage analysis
       {coverageAnalysis(sourceFileName(), 12);
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
